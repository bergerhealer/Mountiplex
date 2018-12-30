package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

public class FieldDeclaration extends Declaration {
    public final ModifierDeclaration modifiers;
    public final NameDeclaration name;
    public final TypeDeclaration type;
    public boolean isEnum;
    public Field field;

    public FieldDeclaration(ClassResolver resolver, Enum<?> enumValue) {
        super(resolver);
        this.isEnum = true;
        this.field = null;
        this.modifiers = new ModifierDeclaration(resolver, Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
        this.name = new NameDeclaration(resolver, enumValue.name(), null);
        this.type = TypeDeclaration.fromType(resolver, enumValue.getClass());
    }

    public FieldDeclaration(ClassResolver resolver, Field field) {
        super(resolver);
        this.isEnum = field.isEnumConstant();
        this.field = field;
        this.modifiers = new ModifierDeclaration(resolver, field.getModifiers());
        this.type = TypeDeclaration.fromType(resolver, field.getGenericType());
        this.name = new NameDeclaration(resolver, field.getName(), null);
    }

    @Deprecated
    public FieldDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public FieldDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);
        this.trimWhitespace(0);
        if (this.getPostfix().startsWith("enum ")) {
            this.trimWhitespace(5);
            this.setPostfix(this.getPostfix().prepend("public static final "));
            this.isEnum = true;
            this.modifiers = nextModifier();
            this.type = nextType();
            this.name = nextName();
        } else {
            this.isEnum = false;
            this.field = null;
            this.modifiers = nextModifier();
            this.type = nextType();
            this.name = nextName();
        }
    }

    public final void setField(FieldDeclaration other) {
        this.field = other.field;
        this.isEnum = other.isEnum;
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof FieldDeclaration)) {
    		return 0.0;
    	}
    	FieldDeclaration f = (FieldDeclaration) other;
    	return 0.1 * this.modifiers.similarity(f.modifiers) +
               0.3 * this.name.similarity(f.name) +
               0.5 * this.type.similarity(f.type);
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof FieldDeclaration) {
            FieldDeclaration field = (FieldDeclaration) declaration;
            return ((isEnum == field.isEnum) || modifiers.match(field.modifiers)) &&
                    name.match(field.name) &&
                    type.match(field.type);
        }
        return false;
    }

    /**
     * Matches this declaration with another declaration, ignoring the name of the field
     * 
     * @param declaration to check against
     * @return True if the signatures match (except for name), False if not
     */
    public boolean matchSignature(Declaration declaration) {
        if (declaration instanceof FieldDeclaration) {
            FieldDeclaration field = (FieldDeclaration) declaration;
            return ((isEnum == field.isEnum) || modifiers.match(field.modifiers)) &&
                    type.match(field.type);
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        if (!isValid()) {
            return "??[" + _initialDeclaration + "]??";
        }
        if (this.isEnum) {
            return "enum " + type.toString(identity) + " " + name.toString(identity);
        } else {
            String m = modifiers.toString(identity);
            String t = type.toString(identity);
            String n = name.toString(identity);
            if (m.length() > 0) {
                return m + " " + t + " " + n + ";";
            } else {
                return t + " " + n + ";";
            }
        }
    }

    @Override
    public boolean isResolved() {
        return this.modifiers.isResolved() && this.type.isResolved() && this.name.isResolved();
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        str.append(indent).append("Field {\n");
        str.append(indent).append("  declaration=").append(this._initialDeclaration).append('\n');
        str.append(indent).append("  postfix=").append(this.getPostfix()).append('\n');
        this.modifiers.debugString(str, indent + "  ");
        this.type.debugString(str, indent + "  ");
        this.name.debugString(str, indent + "  ");
        str.append(indent).append("}\n");
    }

}
