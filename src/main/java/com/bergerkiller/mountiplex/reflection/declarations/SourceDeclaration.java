package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * A list of package paths and imports, combined with class definitions
 */
public class SourceDeclaration extends Declaration {
    public final ClassDeclaration[] classes;

    private SourceDeclaration(ClassResolver resolver, ClassLoader classLoader, File sourceDirectory, StringBuffer declaration) {
        super(resolver, preprocess(declaration));

        trimWhitespace(0);

        // Parse all segments
        StringBuffer postfix;
        String templatefile = "";
        LinkedList<ClassDeclaration> classes = new LinkedList<ClassDeclaration>();
        while ((postfix = this.getPostfix()) != null && postfix.length() > 0) {
            if (nextInternal()) {
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
                    setPostfix(StringBuffer.EMPTY);
                    break;
                }
                String varName = postfix.substringToString(0, nameEndIdx);
                String varValue = "";
                trimWhitespace(nameEndIdx + 1);
                postfix = this.getPostfix();
                for (int cidx = 0; cidx < postfix.length(); cidx++) {
                    char c = postfix.charAt(cidx);
                    if (MountiplexUtil.containsChar(c, invalid_name_chars)) {
                        varValue = postfix.substringToString(0, cidx);
                        break;
                    }
                }
                if (varValue == null) {
                    varValue = postfix.toString();
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
                        name = postfix.substringToString(0, cidx);
                        break;
                    }
                }
                if (name == null) {
                    name = postfix.toString();
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
                        String inclSourceStr;
                        try {
                            try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                                int length;
                                byte[] buffer = new byte[1024];
                                while ((length = is.read(buffer)) != -1) {
                                    result.write(buffer, 0, length);
                                }
                                inclSourceStr = result.toString("UTF-8");
                            }
                        } catch (Throwable t) {
                            MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to load template " + name, t);
                            inclSourceStr = "";
                        }

                        if (!inclSourceStr.isEmpty()) {
                            // Load this source file
                            StringBuilder subSource = new StringBuilder();
                            subSource.append(getResolver().saveDeclaration()).append("\n");
                            subSource.append("#setpath ").append(name).append("\n");
                            subSource.append(inclSourceStr);

                            SourceDeclaration inclSource = new SourceDeclaration(this.getResolver(), classLoader, sourceDirectory, StringBuffer.of(subSource));

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

    public static StringBuffer preprocess(StringBuffer declaration) {
        return StringBuffer.of(preprocess(declaration.toString()));
    }

    /// pre-processes the source file, keeping the parts that pass variable evaluation
    public static String preprocess(String declaration) {
        return preprocess(declaration, new ClassResolver());
    }

    /// pre-processes the source file, keeping the parts that pass variable evaluation
    public static String preprocess(String declaration, ClassResolver resolver) {
        return (new SourcePreprocessor(resolver)).preprocess(declaration);
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
        for (String imp : getResolver().getAllImports().collect(Collectors.toList())) {
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
     * Parses the full source contents into a Source Declaration from a String.
     * The class resolver root can be specified.
     * 
     * @param resolver to use as base for resolving types and classes
     * @param source to parse
     * @return Source Declaration
     */
    public static SourceDeclaration parse(ClassResolver resolver, String source) {
        return new SourceDeclaration(resolver, null, null, StringBuffer.of(source));
    }

    /**
     * Parses the full source contents into a Source Declaration from a String
     * 
     * @param source to parse
     * @return Source Declaration
     */
    public static SourceDeclaration parse(String source) {
        return new SourceDeclaration(new ClassResolver(), null, null, StringBuffer.of(source));
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
        return new SourceDeclaration(new ClassResolver(), classLoader, null, StringBuffer.of(saveVars(variables) + "\n" + "#include " + sourceInclude));
    }

    /**
     * Parses the full source contents by reading from files on disk
     * 
     * @param sourceDirectory relative to which included files are resolved
     * @param sourceInclude relative file path to load
     * @param variables to use while loading the source files
     * @param isGenerating sets the class resolver 'isGenerating' option
     * @return Source Declaration
     */
    public static SourceDeclaration loadFromDisk(File sourceDirectory, String sourceInclude, Map<String, String> variables, boolean isGenerating) {
        ClassResolver resolver = new ClassResolver();
        resolver.setGenerating(isGenerating);
        return new SourceDeclaration(resolver, null, sourceDirectory, StringBuffer.of(saveVars(variables) + "\n" + "#include " + sourceInclude));
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
        return new SourceDeclaration(new ClassResolver(), null, sourceDirectory, StringBuffer.of(saveVars(variables) + "\n" + "#include " + sourceInclude));
    }

    /**
     * Parses the full source contents by reading a bundled resource file
     * 
     * @param classLoader to use when resolving loaded and included resources
     * @param sourceInclude resource file to load
     * @return Source Declaration
     */
    public static SourceDeclaration parseFromResources(ClassLoader classLoader, String sourceInclude) {
        return new SourceDeclaration(new ClassResolver(), classLoader, null, StringBuffer.of("#include " + sourceInclude));
    }

    /**
     * Parses the full source contents by reading from files on disk
     * 
     * @param sourceDirectory relative to which included files are resolved
     * @param sourceInclude relative file path to load
     * @return Source Declaration
     */
    public static SourceDeclaration loadFromDisk(File sourceDirectory, String sourceInclude) {
        return new SourceDeclaration(new ClassResolver(), null, sourceDirectory, StringBuffer.of("#include " + sourceInclude));
    }
}
