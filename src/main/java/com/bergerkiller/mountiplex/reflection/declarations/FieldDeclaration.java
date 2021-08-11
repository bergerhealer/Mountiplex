package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Field;
import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.Modifier;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastConvertedField;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.GeneratorArgumentStore;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;

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

        // If classloader loading this class altered the bytecode, the result of alias will differ from name
        // In that case, store the name the classloader gives the field as an alias
        String name = MPLType.getName(field);
        String alias = Resolver.resolveFieldAlias(field, name);
        if (name.equals(alias)) {
            alias = null;
        }

        // Try to get generic field type information. If this fails, revert back to just using Class type
        TypeDeclaration fieldType;
        try {
            fieldType = TypeDeclaration.fromType(resolver, field.getGenericType());
        } catch (GenericSignatureFormatError ex) {
            fieldType = TypeDeclaration.fromType(resolver, field.getType());
        }

        this.isEnum = field.isEnumConstant();
        this.field = field;
        this.modifiers = new ModifierDeclaration(resolver, field.getModifiers());
        this.type = fieldType;
        this.name = new NameDeclaration(resolver, name, alias);
    }

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

    /* Hidden constructor for changing the name of the field */
    private FieldDeclaration(FieldDeclaration original, String newName) {
        super(original.getResolver());
        this.modifiers = original.modifiers;
        this.name = original.name.rename(newName);
        this.type = original.type;
        this.isEnum = original.isEnum;
        this.field = original.field;
    }

    public final void copyFieldFrom(FieldDeclaration other) {
        if (this != other) {
            this.field = other.field;
            this.isEnum = other.isEnum;
        }
    }

    @Override
    public double similarity(Declaration other) {
    	if (!(other instanceof FieldDeclaration)) {
    		return 0.0;
    	}
    	FieldDeclaration f = (FieldDeclaration) other;
    	return 0.1 * this.modifiers.similarity(f.modifiers) +
               0.3 * this.name.similarity(f.name) +
               0.6 * this.type.similarity(f.type);
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

    @Override
    public void modifyBodyRequirement(Requirement requirement, StringBuilder body, String instanceName, String requirementName, int instanceStartIdx, int nameEndIdx) {
        TypeDeclaration fieldType = this.type;
        if (fieldType.cast != null) {
            fieldType = fieldType.cast;
        }

        // If there is a = after the name (possible spaces), then the field is assigned
        // In that case, wrap the entire statement in a set operation
        // Beware of == as that is not an assignment operation
        int setOperationValueStartIdx = -1;
        for (int i = nameEndIdx; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (c == '=') {
                if ((i+1) >= body.length() || body.charAt(i+1) != '=') {
                    setOperationValueStartIdx = i + 1;
                    while (setOperationValueStartIdx < body.length() &&
                           body.charAt(setOperationValueStartIdx) == ' ')
                    {
                        setOperationValueStartIdx++;
                    }
                }
            }
            break;
        }

        // Modifiers for checking public, if missing (0), none of the modifiers match
        // When the class in which the field is declared is not accessible, force field as unavailable
        Class<?> fieldDeclaringClass = (this.field == null) ? null : this.field.getDeclaringClass();
        int modifiers = 0;
        if (fieldDeclaringClass != null && this.type.cast == null && Resolver.isPublic(fieldDeclaringClass)) {
            modifiers = this.field.getModifiers();
        }

        // When setting
        if (setOperationValueStartIdx != -1) {
            // When setting, and the field is both public and non-final, we can set the field inline
            // That way no requirement needs to be used to do this
            // Only the instanceName#field portion in the body has to be replaced
            if (Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers)) {
                StringBuilder replacement = new StringBuilder();
                if (Modifier.isStatic(modifiers)) {
                    // Replace with ClassName.fieldName
                    replacement.append(ReflectionUtil.getAccessibleTypeName(fieldDeclaringClass));
                } else {
                    // Replace with instanceName.fieldName
                    replacement.append(instanceName);
                }
                replacement.append('.');
                replacement.append(MPLMemberResolver.IGNORE_PREFIX); // to prevent double-renaming
                replacement.append(MPLType.getName(this.field));
                body.replace(instanceStartIdx, nameEndIdx, replacement.toString());
                return;
            }

            // Find the end of the piece of 'value code'
            // For example, this will find 'helper.counter + 5' in:
            // object#field = helper.counter + 5;
            int setOperationValueEndIdx = setOperationValueStartIdx;

            int parenthesesCtr = 0;
            for (; setOperationValueEndIdx < body.length(); setOperationValueEndIdx++) {
                char c = body.charAt(setOperationValueEndIdx);
                if (c == ';') {
                    break;
                } else if (c == '(') {
                    parenthesesCtr++;
                } else if (c == ')') {
                    if (--parenthesesCtr < 0) {
                        break;
                    }
                }
            }

            String valueName = body.substring(setOperationValueStartIdx, setOperationValueEndIdx);

            // Modify the original body to use the field setter method instead
            StringBuilder replacement = new StringBuilder();
            replacement.append("this.").append(requirementName).append(".set");
            if (fieldType.isPrimitive) {
                replacement.append(BoxedType.getBoxedType(fieldType.type).getSimpleName());
            }
            replacement.append('(');
            replacement.append(instanceName);
            replacement.append(", ");
            replacement.append(valueName);
            replacement.append(')');

            // Mark as used so that the requirement is added to the class later
            requirement.setProperty("generateFastField");

            // Replace portion in body with replacement
            body.replace(instanceStartIdx, setOperationValueEndIdx, replacement.toString());
        } else {
            StringBuilder replacement = new StringBuilder();

            // When getting, and the field is public, we can set the field inline
            // That way no requirement needs to be used to do this
            // Only the instanceName#field portion in the body has to be replaced
            if (Modifier.isPublic(modifiers)) {
                // Get the field directly, not using the requirements
                if (Modifier.isStatic(modifiers)) {
                    // Replace with ClassName.fieldName
                    replacement.append(ReflectionUtil.getAccessibleTypeName(fieldDeclaringClass));
                } else {
                    // Replace with instanceName.fieldName
                    replacement.append(instanceName);
                }

                replacement.append('.');
                replacement.append(MPLMemberResolver.IGNORE_PREFIX); // to prevent double-renaming
                replacement.append(MPLType.getName(this.field));
            } else {
                // Modify the original body to use the field getter method instead
                if (fieldType.isPrimitive) {
                    // Primitive-specific getter method
                    replacement.append("this.").append(requirementName).append(".get");
                    replacement.append(BoxedType.getBoxedType(fieldType.type).getSimpleName());
                } else {
                    // Get + cast
                    replacement.append(ReflectionUtil.getAccessibleTypeCast(fieldType.type));
                    replacement.append("this.").append(requirementName).append(".get");
                }
                replacement.append('(');
                replacement.append(instanceName);
                replacement.append(')');

                // Mark as used so that the requirement is added to the class later
                requirement.setProperty("generateFastField");
            }

            // Replace portion in body with replacement
            body.replace(instanceStartIdx, nameEndIdx, replacement.toString());
        }
    }

    @Override
    public void addAsRequirement(Requirement requirement, CtClass invokerClass, String name) throws CannotCompileException, NotFoundException {
        // If the field could be accessed directly, then we don't have to generate a FastField as well
        if (!requirement.hasProperty("generateFastField")) {
            return;
        }

        if (this.type.cast != null) {
            DuplexConverter<Object, Object> converter = Conversion.findDuplex(this.type, this.type.cast);
            if (converter == null) {
                throw new RuntimeException("Failed to find converter from " +
                        this.type.toString(true) + " <> " + this.type.cast.toString(true));
            }

            FastField<?> f = new FastField<Object>();
            f.init(this.field);
            FastConvertedField<?> cf = new FastConvertedField<Object>(f, converter);

            ClassPool tmp_pool = new ClassPool();
            tmp_pool.insertClassPath(new ClassClassPath(FastConvertedField.class));
            CtClass fastFieldClass = tmp_pool.get(FastConvertedField.class.getName());

            CtField ctField = new CtField(fastFieldClass, name, invokerClass);
            ctField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            invokerClass.addField(ctField, GeneratorArgumentStore.initializeField(cf));
        } else {
            // No conversion, we can use a simple FastField
            FastField<?> f = new FastField<Object>();
            f.init(this.field);

            ClassPool tmp_pool = new ClassPool();
            tmp_pool.insertClassPath(new ClassClassPath(FastField.class));
            CtClass fastFieldClass = tmp_pool.get(FastField.class.getName());

            CtField ctField = new CtField(fastFieldClass, name, invokerClass);
            ctField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            invokerClass.addField(ctField, GeneratorArgumentStore.initializeField(f));
        }
    }

    @Override
    public FieldDeclaration discover() {
        if (!this.isValid() || !this.isResolved()) {
            return null;
        }

        java.lang.reflect.Field javaField;
        try {
            FieldDeclaration nameResolved = this.resolveName();
            javaField = MPLType.getDeclaredField(this.getResolver().getDeclaredClass(), nameResolved.name.value());
            FieldDeclaration realField = new FieldDeclaration(this.getResolver(), javaField);

            // Check matching
            if (!nameResolved.match(realField)) {
                return null;
            }

            // Field must be public when declaration says it's public
            if (this.modifiers.isPublic() && !Modifier.isPublic(realField.field.getModifiers())) {
                return null;
            }

            this.copyFieldFrom(realField);
            return this;
        } catch (NoSuchFieldException ex) {
            // Not found
        } catch (Throwable t) {
            t.printStackTrace(); // wut
        }
        return null;
    }

    @Override
    public void discoverAlternatives() {
        Class<?> declaringClass = this.getResolver().getDeclaredClass();
        if (declaringClass == null) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration could not be found inside: ??" + this.getResolver().getDeclaredClassName() + "??");
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + this.toString());
            return;
        }

        FieldDeclaration[] alternatives;
        if (this.modifiers.isStatic()) {
            alternatives = ReflectionUtil.getAllStaticFields(declaringClass)
                    .map(f -> new FieldDeclaration(getResolver(), f))
                    .toArray(FieldDeclaration[]::new);
        } else {
            alternatives = ReflectionUtil.getAllNonStaticFields(declaringClass)
                    .map(f -> new FieldDeclaration(getResolver(), f))
                    .toArray(FieldDeclaration[]::new);
        }
        sortSimilarity(this, alternatives);
        FieldLCSResolver.logAlternatives("field", alternatives, this.resolveName(), true);
    }

    /**
     * Asks the {@link Resolver} what the real field name is, given the provided signature
     * of this field declaration. If the name is not different, this same field declaration
     * is returned.
     * 
     * @return name-resolved field declaration
     */
    public FieldDeclaration resolveName() {
        if (!this.isResolved()) {
            return this;
        }
        String resolvedName = Resolver.resolveFieldName(this.getResolver().getDeclaredClass(), this.name.value());
        if (resolvedName != null && !resolvedName.equals(this.name.value())) {
            return new FieldDeclaration(this, resolvedName);
        } else {
            return this;
        }
    }

    /**
     * Gets the name of the field actually accessed in generated code.
     * This is the reflection Field name if found, otherwise the name.value()
     * is used as a fallback.
     * 
     * @return accessed name
     */
    protected String getAccessedName() {
        return field != null ? MPLType.getName(field) : this.name.value();
    }
}
