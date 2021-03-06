package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Constructor;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

public class ConstructorDeclaration extends Declaration {
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration type;
    public final ParameterListDeclaration parameters;
    public Constructor<?> constructor;

    public ConstructorDeclaration(ClassResolver resolver, Constructor<?> constructor) {
        super(resolver);
        this.constructor = constructor;
        this.modifiers = new ModifierDeclaration(resolver, constructor.getModifiers());
        this.type = TypeDeclaration.fromType(resolver, constructor.getDeclaringClass());
        this.parameters = new ParameterListDeclaration(resolver, constructor.getGenericParameterTypes());
    }

    @Deprecated
    public ConstructorDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public ConstructorDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);
        this.constructor = null;
        this.modifiers = nextModifier();
        this.type = nextType();
        this.parameters = nextParameterList();
    }

    /**
     * Gets a unique identifier name for this constructor
     * 
     * @return constructor name
     */
    public final String getName() {
        String name = "constr";
        for (ParameterDeclaration param : parameters.parameters) {
            name += "_" + param.name.real();
        }
        return name;
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof ConstructorDeclaration) {
            ConstructorDeclaration constr = (ConstructorDeclaration) declaration;
            return type.match(constr.type) && parameters.match(constr.parameters); 
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        String m = modifiers.toString(identity);
        if (m.length() > 0) {
            return m + " " + type.toString(identity) + parameters.toString(identity) + ";";
        } else {
            return type.toString() + parameters.toString(identity) + ";";
        }
    }

    @Override
    public double similarity(Declaration other) {
        if (!(other instanceof ConstructorDeclaration)) {
            return 0.0;
        }
        ConstructorDeclaration c = (ConstructorDeclaration) other;
        return 0.1 * this.modifiers.similarity(c.modifiers) +
               0.9 * this.parameters.similarity(c.parameters);
    }

    @Override
    public boolean isResolved() {
        return type.isResolved() && parameters.isResolved();
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Constructor {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        this.modifiers.debugString(str, indent + "  ");
        this.type.debugString(str, indent + "  ");
        this.parameters.debugString(str, indent + "  ");
        str.append(indent).append("}\n");
    }

    @Override
    public ConstructorDeclaration discover() {
        if (!this.isValid() || !this.isResolved()) {
            return null;
        }

        // TODO: Use class declaration or other tricks?

        try {
            java.lang.reflect.Constructor<?> constructor;
            constructor = this.getResolver().getDeclaredClass().getDeclaredConstructor(this.parameters.toParamArray());
            if (constructor != null) {
                this.constructor = constructor;
                return this;
            }
        } catch (NoSuchMethodException | SecurityException e) {
            // Not found
        }
        return null;
    }
}
