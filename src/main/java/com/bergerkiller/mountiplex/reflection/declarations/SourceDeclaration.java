package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * A list of package paths and imports, combined with class definitions
 */
public class SourceDeclaration extends Declaration {
    public final ClassDeclaration[] classes;

    private SourceDeclaration(ClassLoader classLoader, File sourceDirectory, String declaration) {
        super(new ClassResolver(), declaration);

        trimBlockComments();

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
            if (postfix.startsWith("package ")) {
                trimWhitespace(8);
                is_package = true;
            } else if (postfix.startsWith("import ")) {
                trimWhitespace(7);
                is_import = true;
            } else if (postfix.startsWith("include ")) {
                trimWhitespace(8);
                is_include = true;
            } else if (postfix.startsWith("setpath ")) {
                trimWhitespace(8);
                is_setpath = true;
            }

            // Parse package or import name, or include another source file
            if (is_package || is_import || is_include || is_setpath) {
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
                        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                        String inclSourceStr = (s.hasNext() ? s.next() : "");
                        s.close();

                        if (!inclSourceStr.isEmpty()) {
                            // Load this source file
                            inclSourceStr = "setpath " + name + "\n" + inclSourceStr;
                            SourceDeclaration inclSource = new SourceDeclaration(classLoader, sourceDirectory, inclSourceStr);
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
            classes.add(nextClass());
        }
        this.classes = classes.toArray(new ClassDeclaration[classes.size()]);
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

    /**
     * Parses the full source contents into a Source Declaration from a String
     * 
     * @param source to parse
     * @return Source Declaration
     */
    public static SourceDeclaration parse(String source) {
        return new SourceDeclaration(null, null, source);
    }

    /**
     * Parses the full source contents by reading a bundled resource file
     * 
     * @param classLoader to use when resolving loaded and included resources
     * @param sourceInclude resource file to load
     * @return Source Declaration
     */
    public static SourceDeclaration parseFromResources(ClassLoader classLoader, String sourceInclude) {
        return new SourceDeclaration(classLoader, null, "include " + sourceInclude);
    }

    /**
     * Parses the full source contents by reading from files on disk
     * 
     * @param sourceDirectory relative to which included files are resolved
     * @param sourceInclude relative file path to load
     * @return Source Declaration
     */
    public static SourceDeclaration loadFromDisk(File sourceDirectory, String sourceInclude) {
        return new SourceDeclaration(null, sourceDirectory, "include " + sourceInclude);
    }
}
