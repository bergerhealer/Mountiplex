package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Type;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

/**
 * A single named parameter in a parameter list for a method or constructor.
 * Matching only matches the parameter type, not the name, as that is unimportant.
 */
public class ParameterDeclaration extends Declaration {
    public final TypeDeclaration type;
    public final NameDeclaration name;

    public ParameterDeclaration(ClassResolver resolver, Type type, String name) {
        super(resolver);
        this.type = TypeDeclaration.fromType(resolver, type);
        this.name = new NameDeclaration(resolver, name, null);
    }

    @Deprecated
    public ParameterDeclaration(ClassResolver resolver, String declaration, int paramIdx) {
        this(resolver, StringBuffer.of(declaration), paramIdx);
    }

    public ParameterDeclaration(ClassResolver resolver, StringBuffer declaration, int paramIdx) {
        super(resolver, declaration);
        this.type = nextType();
        this.name = nextName(paramIdx);
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof ParameterDeclaration)) {
    		return 0.0;
    	}
    	return this.type.similarity(((ParameterDeclaration) other).type);
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof ParameterDeclaration) {
            return type.match(((ParameterDeclaration) declaration).type);
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        return type.toString(identity) + " " + name.toString(identity);
    }

    @Override
    public boolean isResolved() {
        return this.type.isResolved() && this.name.isResolved();
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Parameter {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        this.name.debugString(str, indent + "  ");
        this.type.debugString(str, indent + "  ");
        str.append(indent).append("}\n");
    }

}
