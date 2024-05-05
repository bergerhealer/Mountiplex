package com.bergerkiller.mountiplex.reflection.declarations;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.DeclarationParserGroups;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.SourceDeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * A list of package paths and imports, combined with class definitions
 */
public class SourceDeclaration extends Declaration {
    public final ClassDeclaration[] classes;

    private SourceDeclaration(ClassResolver resolver, ClassLoader classLoader, File sourceDirectory, StringBuffer declaration) {
        super(resolver, preprocess(declaration));

        // Tracks classes being loaded
        final ParserContext parserContext = new ParserContext(classLoader, sourceDirectory);

        getParserPostfix().trimWhitespace(0);

        // Parse all segments
        StringBuffer postfix;
        while ((postfix = this.getPostfix()) != null && postfix.length() > 0) {
            if (parserContext.runParsers(DeclarationParserGroups.SOURCE)) {
                continue;
            }

            // Read classes
            ClassDeclaration cDec = nextClass();
            if (cDec.isValid()) {
                parserContext.classes.add(cDec);
            } else {
                MountiplexUtil.LOGGER.warning("Invalid class declaration parsed:\n" + cDec);
                MountiplexUtil.LOGGER.warning("Source: " + cDec._initialDeclaration);
                this.setInvalid();
                this.classes = new ClassDeclaration[0];
                return;
            }
        }
        this.classes = parserContext.getClasses();
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

    private class ParserContext extends BaseDeclarationParserContext implements SourceDeclarationParserContext {
        private ClassLoader classLoader;
        private final File currentDirectory;
        private String templateFile = "";
        private LinkedList<ClassDeclaration> classes = new LinkedList<ClassDeclaration>();

        public ParserContext(ClassLoader classLoader, File currentDirectory) {
            this.classLoader = classLoader;
            this.currentDirectory = currentDirectory;
        }

        public ClassDeclaration[] getClasses() {
            return classes.toArray(new ClassDeclaration[classes.size()]);
        }

        @Override
        public ClassLoader getClassLoader() {
            if (classLoader == null) {
                classLoader = SourceDeclaration.class.getClassLoader();
            }
            return classLoader;
        }

        @Override
        public File getCurrentDirectory() {
            return currentDirectory;
        }

        @Override
        public void includeSource(StringBuffer subSource) {
            SourceDeclaration inclSource = new SourceDeclaration(
                    getResolver(),
                    classLoader,
                    currentDirectory,
                    subSource);

            classes.addAll(Arrays.asList(inclSource.classes));
        }

        @Override
        public void setCurrentTemplateFile(String path) {
            templateFile = path;
        }

        @Override
        public String getCurrentTemplateFile() {
            return templateFile;
        }
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
