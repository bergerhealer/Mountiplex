package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * A list of package paths and imports, combined with class definitions
 */
public class SourceDeclaration extends Declaration {
    public final ClassDeclaration[] classes;

    private SourceDeclaration(ClassLoader classLoader, File sourceDirectory, String declaration) {
        super(new ClassResolver(), preprocess(declaration));

        trimWhitespace(0);

        // Parse all segments
        String postfix;
        String templatefile = "";
        LinkedList<ClassDeclaration> classes = new LinkedList<ClassDeclaration>();
        while ((postfix = this.getPostfix()) != null && postfix.length() > 0) {
            if (postfix.startsWith("//")) {
                trimLine();
                continue;
            }

            boolean is_package = false;
            boolean is_import = false;
            boolean is_include = false;
            boolean is_setpath = false;
            boolean is_setvar = false;
            if (postfix.startsWith("package ")) {
                trimWhitespace(8);
                is_package = true;
            } else if (postfix.startsWith("import ")) {
                trimWhitespace(7);
                is_import = true;
            } else if (postfix.startsWith("#include ")) {
                trimWhitespace(9);
                is_include = true;
            } else if (postfix.startsWith("#setpath ")) {
                trimWhitespace(9);
                is_setpath = true;
            } else if (postfix.startsWith("#set ")) {
                trimWhitespace(5);
                is_setvar = true;
            }

            // Parse package or import name, or include another source file
            if (is_setvar) {
                postfix = this.getPostfix();
                int nameEndIdx = postfix.indexOf(' ');
                if (nameEndIdx == -1) {
                    setPostfix("");
                    break;
                }
                String varName = postfix.substring(0, nameEndIdx);
                String varValue = "";
                trimWhitespace(nameEndIdx + 1);
                postfix = this.getPostfix();
                for (int cidx = 0; cidx < postfix.length(); cidx++) {
                    char c = postfix.charAt(cidx);
                    if (MountiplexUtil.containsChar(c, invalid_name_chars)) {
                        varValue = postfix.substring(0, cidx);
                        break;
                    }
                }
                if (varValue == null) {
                    varValue = postfix;
                }
                this.trimLine();
                this.getResolver().setVariable(varName, varValue);
                continue;
            } else if (is_package || is_import || is_include || is_setpath) {
                String name = null;
                postfix = this.getPostfix();
                for (int cidx = 0; cidx < postfix.length(); cidx++) {
                    char c = postfix.charAt(cidx);
                    if (MountiplexUtil.containsChar(c, invalid_name_chars)) {
                        name = postfix.substring(0, cidx);
                        break;
                    }
                }
                if (name == null) {
                    name = postfix;
                }

                this.trimLine();

                if (is_package) {
                    this.getResolver().setPackage(name);
                }
                if (is_import) {
                    this.getResolver().addImport(name);
                }
                if (is_include) {
                    if (name.startsWith(".") || name.startsWith("/")) {
                        // Trim everything after the last / in the old template path
                        int lastPathIdx = templatefile.lastIndexOf('/');
                        if (lastPathIdx != -1) {
                            name = templatefile.substring(0, lastPathIdx) + "/" + name;
                        } else {
                            name = templatefile + "/" + name;
                        }

                        // Repeatedly remove the word in front of /../
                        int moveUpIdx;
                        while ((moveUpIdx = name.indexOf("/../")) != -1) {
                            int before = name.lastIndexOf('/', moveUpIdx - 1);
                            if (before == -1) {
                                name = name.substring(moveUpIdx + 4);
                            } else {
                                name = name.substring(0, before) + "/" + name.substring(moveUpIdx + 4);
                            }
                        }

                        // Clean up the path
                        name = name.replace("/./", "/").replace("//", "/");
                    }

                    // Load the resource pointed to by this name
                    InputStream is;
                    if (sourceDirectory == null) {
                        if (classLoader == null) {
                            classLoader = SourceDeclaration.class.getClassLoader();
                        }
                        is = classLoader.getResourceAsStream(name);
                    } else {
                        try {
                            String path = sourceDirectory.getAbsolutePath() + File.separator + name.replace("/", File.separator);
                            is = new FileInputStream(path);
                        } catch (FileNotFoundException e) {
                            is = null;
                        }
                    }

                    if (is == null) {
                        MountiplexUtil.LOGGER.warning("Could not resolve include while parsing template: " + name);
                        MountiplexUtil.LOGGER.warning("Template file: " + templatefile);
                    } else {
                        java.util.Scanner s = new java.util.Scanner(is);
                        s.useDelimiter("\\A");
                        String inclSourceStr = (s.hasNext() ? s.next() : "");
                        s.close();

                        if (!inclSourceStr.isEmpty()) {
                            // Load this source file
                            String subSource = "";
                            subSource += getResolver().saveDeclaration() + "\n";
                            subSource += "#setpath " + name +  "\n";
                            subSource += inclSourceStr;
                            SourceDeclaration inclSource = new SourceDeclaration(classLoader, sourceDirectory, subSource);
                            classes.addAll(Arrays.asList(inclSource.classes));
                        }
                    }
                }
                if (is_setpath) {
                    templatefile = name;
                }
                continue;
            }

            // Read classes
            ClassDeclaration cDec = nextClass();
            if (cDec.isValid()) {
                classes.add(cDec);
            } else {
                MountiplexUtil.LOGGER.warning("Invalid class declaration parsed:\n" + cDec);
                this.setInvalid();
                this.classes = new ClassDeclaration[0];
                return;
            }
        }
        this.classes = classes.toArray(new ClassDeclaration[classes.size()]);
    }

    /// pre-processes the source file, keeping the parts that pass variable evaluation
    public static String preprocess(String declaration) {
        // Trim block comments from the declaration text
        while (true) {
            int startIndex = declaration.lastIndexOf("/*");
            if (startIndex == -1) {
                break;
            }
            int endIndex = declaration.indexOf("*/", startIndex + 2);
            if (endIndex == -1) {
                break;
            }
            declaration = declaration.substring(0, startIndex) +
                    declaration.substring(endIndex + 2);
        }

        // Resolve variables and #if - preprocessor declarations
        ClassResolver resolver = new ClassResolver();
        StringBuilder result = new StringBuilder();
        int disabledIfLevel = 0;
        boolean disabledIfExpression = false;
        for (String line : declaration.split("\\r?\\n")) {
            String lineTrimmed = line.trim();
            String lineLower = lineTrimmed.toLowerCase(Locale.ENGLISH);
            if (disabledIfLevel > 1) {
                // At this level, #elseif and #else have no effect, only switch levels
                if (lineLower.startsWith("#if")) {
                    disabledIfLevel++;
                } else if (lineLower.startsWith("#endif")) {
                    disabledIfLevel--;
                }
                continue;
            }
            if (disabledIfLevel == 1) {
                // At this level, #elseif or #else can toggle modes
                if (lineLower.startsWith("#if")) {
                    disabledIfLevel++;
                } else if (lineLower.startsWith("#endif")) {
                    disabledIfLevel--;
                } else if (lineLower.startsWith("#else")) {
                    int ifIdx = lineTrimmed.indexOf("if", 5);
                    boolean evaluates = true;
                    if (ifIdx != -1) {
                        // Else if - evaluate expression to decide whether to allow
                        String expr = lineTrimmed.substring(ifIdx + 2).trim();
                        evaluates = resolver.evaluateExpression(expr);
                    }
                    if (!disabledIfExpression && evaluates) {
                        // Evaluates - enter this if-block
                        disabledIfLevel--;
                    }
                }
                continue;
            }

            // Over here all lines are allowed to be included
            // Parse if-statements in case we go a level deeper
            // All else-evaluations fail here
            disabledIfExpression = false;
            if (lineLower.startsWith("#if")) {
                String expr = lineTrimmed.substring(3).trim();
                if (!resolver.evaluateExpression(expr)) {
                    disabledIfLevel++;
                }
                continue;
            }
            if (lineLower.startsWith("#else")) {
                disabledIfLevel++;
                disabledIfExpression = true;
                continue;
            }
            if (lineLower.startsWith("#endif")) {
                continue; // ignore
            }

            // Ignore comments
            if (lineLower.startsWith("//")) {
                continue;
            }

            // The below statements are all included in the source
            result.append(line).append('\n');
            if (lineLower.startsWith("#set ")) {
                lineTrimmed = lineTrimmed.substring(5).trim();
                int nameEndIdx = lineTrimmed.indexOf(' ');
                if (nameEndIdx == -1) {
                    continue;
                }
                String varName = lineTrimmed.substring(0, nameEndIdx);
                String varValue = lineTrimmed.substring(nameEndIdx + 1);
                while (varValue.length() > 0 && varValue.charAt(0) == ' ') {
                    varValue = varValue.substring(1);
                }
                resolver.setVariable(varName, varValue);
                continue;
            }
        }
        return result.toString();
    }

    /**
     * Corrects space indentation in text, making sure the minimal indentation is 0.
     * 
     * @param text to correct
     * @return corrected text
     */
    public static String trimIndentation(String text) {
        String[] lines = text.split("\\r?\\n", -1);

        // Find the indent of the text section
        int minIndent = 20;
        for (String line : lines) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != ' ') {
                    if (i < minIndent) {
                        minIndent = i;
                    }
                    break;
                }
            }
        }

        // Trim indentation off the section and add the lines
        StringBuilder result = new StringBuilder(text.length());
        for (String line : lines) {
            if (line.length() >= minIndent) {
                line = line.substring(minIndent);
            }
            result.append(line).append('\n');
        }
        return result.toString();
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public boolean match(Declaration declaration) {
        return false; // don't care
    }

    @Override
    public String toString(boolean identity) {
        String pkg = getResolver().getPackage();
        String str = "";
        if (pkg.length() > 0) {
            str += "package " + pkg + ";\n\n";
        }
        for (String imp : getResolver().getImports()) {
            str += "import " + imp + ";\n";
        }
        str += "\n";
        for (ClassDeclaration c : classes) {
            str += c.toString(identity) + "\n\n";
        }
        return str;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
    }

    @Override
    public double similarity(Declaration other) {
    	return 0.0; // not implemented
    }
    
    /**
     * Parses the full source contents into a Source Declaration from a String
     * 
     * @param source to parse
     * @return Source Declaration
     */
    public static SourceDeclaration parse(String source) {
        return new SourceDeclaration(null, null, source);
    }

    private static String saveVars(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            str.append("#set ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        }
        return str.toString();
    }
 
    /**
     * Parses the full source contents by reading a bundled resource file
     * 
     * @param classLoader to use when resolving loaded and included resources
     * @param sourceInclude resource file to load
     * @param variables to use while loading the source files
     * @return Source Declaration
     */
    public static SourceDeclaration parseFromResources(ClassLoader classLoader, String sourceInclude, Map<String, String> variables) {
        return new SourceDeclaration(classLoader, null, saveVars(variables) + "\n" + "#include " + sourceInclude);
    }

    /**
     * Parses the full source contents by reading from files on disk
     * 
     * @param sourceDirectory relative to which included files are resolved
     * @param sourceInclude relative file path to load
     * @param variables to use while loading the source files
     * @return Source Declaration
     */
    public static SourceDeclaration loadFromDisk(File sourceDirectory, String sourceInclude, Map<String, String> variables) {
        return new SourceDeclaration(null, sourceDirectory, saveVars(variables) + "\n" + "#include " + sourceInclude);
    }

    /**
     * Parses the full source contents by reading a bundled resource file
     * 
     * @param classLoader to use when resolving loaded and included resources
     * @param sourceInclude resource file to load
     * @return Source Declaration
     */
    public static SourceDeclaration parseFromResources(ClassLoader classLoader, String sourceInclude) {
        return new SourceDeclaration(classLoader, null, "#include " + sourceInclude);
    }

    /**
     * Parses the full source contents by reading from files on disk
     * 
     * @param sourceDirectory relative to which included files are resolved
     * @param sourceInclude relative file path to load
     * @return Source Declaration
     */
    public static SourceDeclaration loadFromDisk(File sourceDirectory, String sourceInclude) {
        return new SourceDeclaration(null, sourceDirectory, "#include " + sourceInclude);
    }
}
