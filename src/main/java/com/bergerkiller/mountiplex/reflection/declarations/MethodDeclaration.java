package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Method;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

public class MethodDeclaration extends Declaration {
    public Method method;
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration returnType;
    public final NameDeclaration name;
    public final ParameterListDeclaration parameters;
    public final String body;

    public MethodDeclaration(ClassResolver resolver, Method method) {
        super(resolver);
        this.method = method;
        this.modifiers = new ModifierDeclaration(resolver, method.getModifiers());
        this.returnType = TypeDeclaration.fromType(resolver, method.getGenericReturnType());
        this.name = new NameDeclaration(resolver, method.getName(), null);
        this.parameters = new ParameterListDeclaration(resolver, method.getGenericParameterTypes());
        this.body = null;
    }

    public MethodDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public MethodDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);
        this.method = null;
        this.modifiers = nextModifier();

        // Skip type variables, they may exist. For now do a simple replace between < > portions
        //TODO: Make this better? It makes it overly complicated.
        StringBuffer postfix = getPostfix();
        if (postfix != null && postfix.length() > 0 && postfix.charAt(0) == '<') {
            boolean foundEnd = false;
            for (int cidx = 1; cidx < postfix.length(); cidx++) {
                char c = postfix.charAt(cidx);
                if (c == '>') {
                    foundEnd = true;
                } else if (foundEnd && !MountiplexUtil.containsChar(c, invalid_name_chars)) {
                    setPostfix(postfix.substring(cidx));
                    break;
                }
            }
        }

        this.returnType = nextType();
        this.name = nextName();
        this.parameters = nextParameterList();

        // Check if there is a body attached to this method. This is the case when
        // the very next character encountered (excluding whitespace) is {
        this.trimWhitespace(0);
        postfix = this.getPostfix();
        if (postfix != null && postfix.startsWith("{")) {
            // Collect the entire body until the amount of { and } evens out
            this.setPostfix(StringBuffer.EMPTY);
            StringBuilder bodyBuilder = new StringBuilder();
            int curlyBrackets = 0;
            boolean inString = false;
            for (int cIdx = 0; cIdx < postfix.length(); cIdx++) {
                char c = postfix.charAt(cIdx);
                bodyBuilder.append(c);
                if (c == '\"') {
                    inString = !inString;
                } else if (inString) {
                    continue;
                } else if (c == '{') {
                    curlyBrackets++;
                } else if (c == '}') {
                    curlyBrackets--;
                    if (curlyBrackets == 0) {
                        this.setPostfix(postfix.substring(cIdx + 1));
                        break;
                    }
                }
            }

            // Use the indentation of the trailing } for the first {
            int lastIndent = bodyBuilder.lastIndexOf("\n");
            if (lastIndent != -1) {
                int lastIndentEnd = lastIndent + 1;
                while (lastIndentEnd < bodyBuilder.length() && bodyBuilder.charAt(lastIndentEnd) == ' ') {
                    bodyBuilder.insert(0, ' ');
                    lastIndentEnd += 2;
                }
            }

            // Correct indentation of body and done
            this.body = SourceDeclaration.trimIndentation(bodyBuilder.toString());
        } else {
            this.body = null;
            if (postfix != null && postfix.startsWith(";")) {
                setPostfix(postfix.substring(1));
            }
        }

        // Make sure to put a newline after the post data
        this.trimWhitespace(0);
        if (this.getPostfix() != null) {
            this.setPostfix(this.getPostfix().prepend("\n"));
        }
    }

    @Override
    public double similarity(Declaration other) {
        if (!(other instanceof MethodDeclaration)) {
            return 0.0;
        }
        MethodDeclaration m = (MethodDeclaration) other;
        return 0.1 * this.modifiers.similarity(m.modifiers) +
               0.3 * this.name.similarity(m.name) +
               0.3 * this.returnType.similarity(m.returnType) +
               0.3 * this.parameters.similarity(m.parameters);
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof MethodDeclaration) {
            MethodDeclaration method = (MethodDeclaration) declaration;
            return modifiers.match(method.modifiers) &&
                    returnType.match(method.returnType) &&
                    name.match(method.name) &&
                    parameters.match(method.parameters);
        }
        return false;
    }

    /**
     * Matches this declaration with another declaration, ignoring the name of the method
     * 
     * @param declaration to check against
     * @return True if the signatures match (except for name), False if not
     */
    public boolean matchSignature(Declaration declaration) {
        if (declaration instanceof MethodDeclaration) {
            MethodDeclaration method = (MethodDeclaration) declaration;
            return modifiers.match(method.modifiers) &&
                    returnType.match(method.returnType) &&
                    parameters.match(method.parameters);
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        String m = modifiers.toString(identity);
        String t = returnType.toString(identity);
        String n = name.toString(identity);
        String p = parameters.toString(identity);
        if (m.length() > 0) {
            return m + " " + t + " " + n + p + ";";
        } else {
            return t + " " + n + p + ";";
        }
    }

    @Override
    public boolean isResolved() {
        return this.modifiers.isResolved() && this.returnType.isResolved() && 
                this.name.isResolved() && this.parameters.isResolved();
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Method {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        this.modifiers.debugString(str, indent + "  ");
        this.returnType.debugString(str, indent + "  ");
        this.name.debugString(str, indent + "  ");
        this.parameters.debugString(str, indent + "  ");
        str.append(indent).append("}\n");
    }
}
