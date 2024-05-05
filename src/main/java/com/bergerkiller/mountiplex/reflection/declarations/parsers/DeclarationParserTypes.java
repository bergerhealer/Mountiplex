package com.bergerkiller.mountiplex.reflection.declarations.parsers;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.Declaration;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Remapping;
import com.bergerkiller.mountiplex.reflection.declarations.Requirement;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.ClassDeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.DeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.context.SourceDeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Contains declaration parser definitions that can be used to parse text buffers.
 * Not all parsers are active at all times. The groups of parsers that are in
 * use are defined in {@link DeclarationParserGroups}.
 */
public final class DeclarationParserTypes {

    /** Comments are omitted from the actual bodies/code */
    public static final DeclarationParser COMMENT = new DeclarationParser() {
        @Override
        public boolean detect(ParserStringBuffer buffer, DeclarationParserContext context) {
            return buffer.startsWith("//");
        }

        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            buffer.trimLine();
        }
    };
    /** Adds bootstrap code to be executed when the handle is first used */
    public static final DeclarationParser BOOTSTRAP = new MacroParser("#bootstrap") {
        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            super.parse(buffer, context);

            // Add code
            StringBuffer postfix = buffer.get();
            int code_end_index;
            if (postfix.startsWith("{")) {
                // Code block. Find matching }, keep embedded { into account
                code_end_index = -1;
                int depth = 0;
                for (int i = 1; i < postfix.length(); i++) {
                    char c = postfix.charAt(i);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}' && (depth--) <= 0) {
                        code_end_index = i + 1;
                        break;
                    }
                }
            } else {
                // Single line of code
                code_end_index = postfix.indexOf('\n');
            }
            if (code_end_index == -1) {
                buffer.set(StringBuffer.EMPTY);
            } else {
                String code = postfix.substringToString(0, code_end_index);
                context.getResolver().addBootstrap(code);
                buffer.trimWhitespace(code_end_index);
            }
        }
    };
    /** Sets the resolver used to obtain the class declarations */
    public static final DeclarationParser RESOLVER = new MacroParser("#resolver") {
        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            super.parse(buffer, context);
            context.getResolver().setClassDeclarationResolverName(buffer.trimLine());
        }
    };
    /** Adds a requirement that can be used in method bodies */
    public static final DeclarationParser REQUIREMENT = new MacroDeclarationParser("#require", "requirement") {
        @Override
        public void runMacro(Declaration dec, DeclarationParserContext context) {
            // Resolve name
            String name = "unknown";
            if (dec instanceof MethodDeclaration) {
                name = ((MethodDeclaration) dec).name.real();
            } else if (dec instanceof FieldDeclaration) {
                name = ((FieldDeclaration) dec).name.real();
            }

            // Store it
            context.getResolver().storeRequirement(new Requirement(name, dec));
        }
    };
    /** Adds a context remapping rule for further requirements, methods and fields */
    public static final DeclarationParser REMAPPING = new MacroDeclarationParser("#remap", "remapping") {
        @Override
        public void runMacro(Declaration dec, DeclarationParserContext context) {
            Declaration resolved = dec.discover();
            if (resolved == null) {
                // Log this
                if (context.getResolver().getLogErrors()) {
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Remapping declaration not found!");
                    dec.discoverAlternatives();
                }
                return;
            }

            if (resolved instanceof MethodDeclaration) {
                MethodDeclaration mDec = (MethodDeclaration) resolved;
                if (mDec.body != null && mDec.method == null) {
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Method bodies for remapped methods are not supported");
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Method: " + resolved.toString());
                    return;
                }
                context.getResolver().storeRemapping(new Remapping.MethodRemapping(mDec));
            } else if (resolved instanceof FieldDeclaration) {
                context.getResolver().storeRemapping(new Remapping.FieldRemapping((FieldDeclaration) resolved));
            }
        }
    };
    /** Adds a logged warning message */
    public static final DeclarationParser WARNING = new MacroParser("#warning") {
        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            super.parse(buffer, context);
            context.addWarning(buffer.trimLine());
        }
    };
    /** Adds a logged error message */
    public static final DeclarationParser ERROR = new MacroParser("#error") {
        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            super.parse(buffer, context);
            context.addError(buffer.trimLine());
        }
    };

    /** Sets the package of a resolver */
    public static final DeclarationParser PACKAGE = new MacroPathParser("package") {
        @Override
        public void runMacro(String path, DeclarationParserContext context) {
            context.getResolver().setPackage(path);
        }
    };
    /** Adds a class import to the resolver */
    public static final DeclarationParser IMPORT = new MacroPathParser("import") {
        @Override
        public void runMacro(String path, DeclarationParserContext context) {
            context.getResolver().addImport(path);
        }
    };
    /** Inserts declaration from another template file at the current position */
    public static final DeclarationParser INCLUDE = new MacroPathParser("#include") {

        private InputStream openResource(SourceDeclarationParserContext context, String path) {
            if (context.getCurrentDirectory() == null) {
                return context.getClassLoader().getResourceAsStream(path);
            } else {
                try {
                    String includedPath = context.getCurrentDirectory().getAbsolutePath() + File.separator + path.replace("/", File.separator);
                    return new FileInputStream(includedPath);
                } catch (FileNotFoundException ignored) {}
            }
            return null;
        }

        @Override
        public void runMacro(String path, DeclarationParserContext origContext) {
            final SourceDeclarationParserContext context = (SourceDeclarationParserContext) origContext;
            final String templateFile = context.getCurrentTemplateFile();

            if (path.startsWith(".") || path.startsWith("/")) {
                // Trim everything after the last / in the old template path
                int lastPathIdx = templateFile.lastIndexOf('/');
                if (lastPathIdx != -1) {
                    path = templateFile.substring(0, lastPathIdx) + "/" + path;
                } else {
                    path = templateFile + "/" + path;
                }

                // Repeatedly remove the word in front of /../
                int moveUpIdx;
                while ((moveUpIdx = path.indexOf("/../")) != -1) {
                    int before = path.lastIndexOf('/', moveUpIdx - 1);
                    if (before == -1) {
                        path = path.substring(moveUpIdx + 4);
                    } else {
                        path = path.substring(0, before) + "/" + path.substring(moveUpIdx + 4);
                    }
                }

                // Clean up the path
                path = path.replace("/./", "/").replace("//", "/");
            }

            String inclSourceStr;
            try (InputStream is = openResource(context, path)) {
                if (is == null) {
                    MountiplexUtil.LOGGER.warning("Could not resolve include while parsing template: " + path);
                    MountiplexUtil.LOGGER.warning("Template file: " + templateFile);
                    return;
                }

                try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                    int length;
                    byte[] buffer = new byte[1024];
                    while ((length = is.read(buffer)) != -1) {
                        result.write(buffer, 0, length);
                    }
                    inclSourceStr = result.toString("UTF-8");
                }
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to load template " + path, t);
                return;
            }

            // Load this source file
            StringBuilder subSource = new StringBuilder();
            subSource.append(context.getResolver().saveDeclaration()).append("\n");
            subSource.append("#setpath ").append(path).append("\n");
            subSource.append(inclSourceStr);

            // And include it for the current source
            context.includeSource(StringBuffer.of(subSource));
        }
    };
    /** Updates the current path of the current source template */
    public static final DeclarationParser SET_PATH = new MacroPathParser("#setpath") {
        @Override
        public void runMacro(String path, DeclarationParserContext origContext) {
            final SourceDeclarationParserContext context = (SourceDeclarationParserContext) origContext;
            context.setCurrentTemplateFile(path);
        }
    };
    /** Assigns a new value to a variable */
    public static final DeclarationParser SET_VARIABLE = new MacroParser("#set") {
        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext context) {
            super.parse(buffer, context);

            int nameEndIdx = buffer.get().indexOf(' ');
            if (nameEndIdx == -1) {
                buffer.set(StringBuffer.EMPTY);
                return;
            }
            String varName = buffer.get().substringToString(0, nameEndIdx);
            String varValue = "";
            buffer.trimWhitespace(nameEndIdx + 1);

            StringBuffer postfix = buffer.get();
            for (int cidx = 0; cidx < postfix.length(); cidx++) {
                char c = postfix.charAt(cidx);
                if (MountiplexUtil.containsChar(c, ParserStringBuffer.INVALID_NAME_CHARACTERS)) {
                    varValue = postfix.substringToString(0, cidx);
                    break;
                }
            }
            buffer.trimLine();

            context.getResolver().setVariable(varName, varValue);
        }
    };

    /** Keeps track of code blocks that must be included for a template class handle implementation */
    public static final DeclarationParser CODE_BLOCK = new DeclarationParser() {
        @Override
        public boolean detect(ParserStringBuffer buffer, DeclarationParserContext context) {
            return buffer.startsWith("<code>") &&
                    buffer.get().indexOf("</code>", 6) != -1;
        }

        @Override
        public void parse(ParserStringBuffer buffer, DeclarationParserContext origContext) {
            ClassDeclarationParserContext context = (ClassDeclarationParserContext) origContext;

            buffer.trimWhitespace(6);

            int endIdx = buffer.get().indexOf("</code>");
            if (endIdx != -1) {
                String code = buffer.get().substringToString(0, endIdx);

                // Identify "import <name>;" at the beginning of the code block
                // These are extracted and put at the top of the actual file
                int searchStartIndex = 0, startIndex;
                while ((startIndex = code.indexOf("import ", searchStartIndex)) != -1) {
                    // Must have a statement end
                    int statementEnd = code.indexOf(';', startIndex + 7);
                    if (statementEnd == 1) {
                        break;
                    }

                    // Statement end must be before a line end
                    int lineEnd = code.indexOf('\n', startIndex + 7);
                    if (lineEnd != -1 && lineEnd < statementEnd) {
                        searchStartIndex = lineEnd;
                        continue;
                    }

                    // Contents before import must be all whitespace up to a newline character
                    int importLineStart = startIndex;
                    boolean validImport = true;
                    for (int i = startIndex - 1; i >= 0; --i) {
                        char c = code.charAt(i);
                        if (c == '\n') {
                            break;
                        } else if (c == ' ') {
                            importLineStart = i;
                            continue;
                        } else {
                            validImport = false;
                            break;
                        }
                    }
                    if (!validImport) {
                        searchStartIndex = statementEnd + 1;
                        continue;
                    }

                    // Extract the import and add
                    context.addCodeImport(code.substring(startIndex + 7, statementEnd).trim());

                    // Find the end of the line to trim it
                    int importLineEnd = statementEnd + 1;
                    while (importLineEnd < code.length()) {
                        char c = code.charAt(importLineEnd);
                        if (c == ' ') {
                            importLineEnd++;
                        } else if (c == '\n') {
                            importLineEnd++; // Omit newline character too
                            break;
                        } else {
                            break;
                        }
                    }

                    // Exclude this line from the code block itself
                    code = code.substring(0, importLineStart) + code.substring(importLineEnd);
                    searchStartIndex = importLineStart;
                }

                context.appendCode(code);
                buffer.set(buffer.get().substring(endIdx + 7));
                buffer.trimLine();
            }
        }
    };
}
