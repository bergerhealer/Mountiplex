package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.DeclarationParserContext;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.DeclarationParserGroups;
import com.bergerkiller.mountiplex.reflection.declarations.parsers.ParserStringBuffer;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Declares the full contents of a Class
 */
public class ClassDeclaration extends Declaration {
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration base;
    public final TypeDeclaration type;
    public final ClassDeclaration[] subclasses;
    public final ConstructorDeclaration[] constructors;
    public final MethodDeclaration[] methods;
    public final FieldDeclaration[] fields;
    public final String code; /* custom code section, used during generation only */
    public final boolean is_interface;

    public ClassDeclaration(ClassResolver resolver, Class<?> type) {
        super(resolver.clone());
        Class<?> superType = type.getSuperclass();
        this.is_interface = type.isInterface();
        this.base = (superType == null) ? null : TypeDeclaration.fromClass(superType);
        this.type = TypeDeclaration.fromClass(type);
        this.modifiers = new ModifierDeclaration(getResolver(), type.getModifiers());
        this.code = "";

        this.getResolver().setDeclaredClass(type);

        LinkedList<ConstructorDeclaration> constructors = new LinkedList<ConstructorDeclaration>();
        LinkedList<MethodDeclaration> methods = new LinkedList<MethodDeclaration>();
        LinkedList<FieldDeclaration> fields = new LinkedList<FieldDeclaration>();
        LinkedList<ClassDeclaration> classes = new LinkedList<ClassDeclaration>();

        if (type.isEnum()) {
            for (Object enumConstant : type.getEnumConstants()) {
                fields.add(new FieldDeclaration(getResolver(), (Enum<?>) enumConstant));
            }
        }
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructors.add(new ConstructorDeclaration(getResolver(), constructor));
        }
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            fields.add(new FieldDeclaration(getResolver(), field));
        }
        ReflectionUtil.getDeclaredMethods(type)
            .filter(ReflectionUtil.createDuplicateMethodFilter())
            .map(m -> new MethodDeclaration(getResolver(), m))
            .forEachOrdered(methods::add);
        for (java.lang.Class<?> decClass : type.getDeclaredClasses()) {
            classes.add(new ClassDeclaration(getResolver(), decClass));
        }

        this.constructors = constructors.toArray(new ConstructorDeclaration[constructors.size()]);
        this.methods = methods.toArray(new MethodDeclaration[methods.size()]);
        this.fields = fields.toArray(new FieldDeclaration[fields.size()]);
        this.subclasses = classes.toArray(new ClassDeclaration[classes.size()]);
    }

    public ClassDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public ClassDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver.clone(), declaration);

        // Modifiers, stop when invalid
        this.modifiers = nextModifier();
        if (!this.isValid()) {
            this.code = "";
            this.base = null;
            this.type = null;
            this.subclasses = new ClassDeclaration[0];
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.is_interface = false;
            return;
        }

        // Class or interface? Then parse class/interface type
        StringBuffer postfix = this.getPostfix();
        this.is_interface = postfix.startsWith("interface ");
        if (!this.is_interface && !postfix.startsWith("class ")) {
            this.base = null;
            this.type = null;
            this.code = "";
            this.subclasses = new ClassDeclaration[0];
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.setInvalid();
            return;
        }
        setPostfix(postfix.substring(this.is_interface ? 10 : 6));
        this.type = nextType();
        if (!this.isValid()) {
            this.base = null;
            this.code = "";
            this.subclasses = new ClassDeclaration[0];
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            return;
        }

        // This makes sure all parsed sub-declaration are subtyped to this class
        this.getResolver().setDeclaredClass(type.type, type.typePath);

        // If starts with 'extends', parse base type
        postfix = getPostfix();
        if (postfix.startsWith("extends ")) {
            this.setPostfix(postfix.substring(8));
            this.base = this.nextType();
        } else {
            this.base = null;
        }

        // Find start of class definitions {
        postfix = getPostfix();
        boolean foundClassStart = false;
        int startIdx = -1;
        for (int cidx = 0; cidx < postfix.length(); cidx++) {
            char c = postfix.charAt(cidx);
            if (c == '{') {
                foundClassStart = true;
            } else if (foundClassStart && !MountiplexUtil.containsChar(c, ParserStringBuffer.WHITESPACE_CHARACTERS)) {
                startIdx = cidx;
                break;
            }
        }
        if (startIdx == -1) {
            this.code = "";
            this.subclasses = new ClassDeclaration[0];
            this.constructors = new ConstructorDeclaration[0];
            this.methods = new MethodDeclaration[0];
            this.fields = new FieldDeclaration[0];
            this.setInvalid();
            return;
        }
        getParserPostfix().trimWhitespace(startIdx);

        final DeclarationParserContext parserContext = new BaseDeclarationParserContext();

        StringBuilder codeStr = new StringBuilder();
        LinkedList<ClassDeclaration> classes = new LinkedList<ClassDeclaration>();
        LinkedList<ConstructorDeclaration> constructors = new LinkedList<ConstructorDeclaration>();
        LinkedList<MethodDeclaration> methods = new LinkedList<MethodDeclaration>();
        LinkedList<FieldDeclaration> fields = new LinkedList<FieldDeclaration>();
        while ((postfix = getPostfix()) != null && postfix.length() > 0) {
            if (parserContext.runParsers(DeclarationParserGroups.BASE)) {
                continue;
            }

            if (postfix.charAt(0) == '}') {
                getParserPostfix().trimWhitespace(1);
                break;
            }

            if (postfix.startsWith("<code>")) {
                int endIdx = postfix.indexOf("</code>", 6);
                if (endIdx != -1) {
                    codeStr.append(SourceDeclaration.trimIndentation(postfix.substringToString(6, endIdx)));
                    setPostfix(postfix.substring(endIdx + 7));
                    getParserPostfix().trimLine();
                    continue;
                }
            }

            ClassDeclaration cldec = new ClassDeclaration(getResolver(), postfix);
            if (cldec.isValid()) {
                classes.add(cldec);
                setPostfix(cldec.getPostfix());
                getParserPostfix().trimWhitespace(0);
                continue;
            }

            Declaration dec = getParserPostfix().detectMemberDeclaration(getResolver());
            if (dec instanceof MethodDeclaration) {
                methods.add((MethodDeclaration) dec);
            } else if (dec instanceof ConstructorDeclaration) {
                constructors.add((ConstructorDeclaration) dec);
            } else if (dec instanceof FieldDeclaration) {
                fields.add((FieldDeclaration) dec);
            } else {
                break;
            }
        }
        this.code = codeStr.toString();
        this.subclasses = classes.toArray(new ClassDeclaration[classes.size()]);
        this.constructors = constructors.toArray(new ConstructorDeclaration[constructors.size()]);
        this.methods = methods.toArray(new MethodDeclaration[methods.size()]);
        this.fields = fields.toArray(new FieldDeclaration[fields.size()]);

        // Verify all the fields exist
        if (this.type.isResolved()) {
            resolveFields();
            resolveMethods();
            resolveConstructors();
        }
    }

    private void resolveFields() {
        java.lang.reflect.Field[] realRefFields;
        try {
            realRefFields = this.type.type.getDeclaredFields();
        } catch (Throwable t) {
            if (this.getResolver().getLogErrors()) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get declared fields of " + this.type.typePath, t);
            }
            return;
        }
        FieldDeclaration[] realFields = new FieldDeclaration[realRefFields.length];
        for (int i = 0; i < realFields.length; i++) {
            try {
                realFields[i] = new FieldDeclaration(getResolver(), realRefFields[i]);
            } catch (Throwable t) {
                if (this.getResolver().getLogErrors()) {
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read field " + realRefFields[i], t);
                }
            }
        }

        // This takes care of asking the Resolver about the true name of the fields
        FieldDeclaration[] nameResolvedFields = new FieldDeclaration[this.fields.length];
        for (int i = 0; i < nameResolvedFields.length; i++) {
            nameResolvedFields[i] = this.fields[i].resolveName();
        }
        FieldLCSResolver.resolve(nameResolvedFields, realFields);

        // Copy found reflection Fields back into the field declaration
        // Preserve original value/alias of the name
        for (int i = 0; i < nameResolvedFields.length; i++) {
            this.fields[i].copyFieldFrom(nameResolvedFields[i]);
        }
    }

    private void resolveMethods() {
        MethodMatchResolver.match(this.type.type, this.getResolver(), this.methods);
    }

    private void resolveConstructors() {
        java.lang.reflect.Constructor<?>[] realRefConstructors = this.type.type.getDeclaredConstructors();
        ConstructorDeclaration[] realConstructors = new ConstructorDeclaration[realRefConstructors.length];
        for (int i = 0; i < realConstructors.length; i++) {
            try {
                realConstructors[i] = new ConstructorDeclaration(getResolver(), realRefConstructors[i]);
            } catch (Throwable t) {
                if (this.getResolver().getLogErrors()) {
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read constructor " + realRefConstructors[i], t);
                }
            }
        }

        // Connect the methods together
        for (int i = 0; i < this.constructors.length; i++) {
            ConstructorDeclaration constructor = this.constructors[i];
            boolean found = false;
            for (int j = 0; j < realConstructors.length; j++) {
                if (realConstructors[j].match(constructor)) {
                    constructor.constructor = realConstructors[j].constructor;
                    found = true;
                    break;
                }
            }
            if (!found && !constructor.modifiers.isOptional()) {
                FieldLCSResolver.logAlternatives("constructor", realConstructors, constructor, false);
            }
        }
    }

    /**
     * Attempts to find the method matching the declaration in this Class
     * 
     * @param declaration to find
     * @return method declaration, <i>null</i> if not found
     */
    public MethodDeclaration findMethod(MethodDeclaration declaration) {
        for (MethodDeclaration mDec : this.methods) {
            if (mDec.match(declaration)) {
                return mDec;
            }
        }

        // Check for remapping rules
        Remapping.MethodRemapping remapping = getResolver().getRemappings().find(declaration);
        if (remapping != null) {
            return remapping.declaration;
        }

        return null;
    }

    /**
     * Attempts to find the Java Reflection Method in this Class Declaration.
     * If found, returns the alias name for the method as set in the declaration.
     * 
     * @param method to find
     * @return alias name for the method, <i>null</i> if not found
     */
    public String resolveMethodAlias(java.lang.reflect.Method method) {
        if (Modifier.isPrivate(method.getModifiers())) {
            // Note: In practise this branch is never even used
            //       The only caller of this method is ClassHook for resolving aliases
            //       Only public/protected (overridable) methods are handled there

            // Private methods are only matchable when the Class is exactly the same
            if (!this.type.type.equals(method.getDeclaringClass())) {
                return null;
            }

            // Go by all the methods; only match with equals()
            for (MethodDeclaration mDec : this.methods) {
                if (mDec.method != null && mDec.method.equals(method)) {
                    return mDec.name.real();
                }
            }

            return null; // not found
        } else {
            // First check if the methods declared in this Class Declaration even apply
            // Note: Removed, because we can declare methods in both directions
            //       A method defined in Car can refer to one in superclass Vehicle
            //       And a method defined in Vehicle can refer to one overrided in Car
            //       As such, this check makes no sense really.
            //if (!method.getDeclaringClass().isAssignableFrom(this.type.type)) {
            //    return null;
            //}

            // Check all non-private methods to see if they match
            Class<?>[] mParams = method.getParameterTypes();
            for (MethodDeclaration mDec : this.methods) {
                if (mDec.method == null || Modifier.isPrivate(mDec.method.getModifiers())) {
                    continue;
                }
                if (!MPLType.getName(mDec.method).equals(MPLType.getName(method))) {
                    continue;
                }

                boolean paramsMatch = true;
                Class<?>[] mDecParams = mDec.method.getParameterTypes();
                if (mDecParams.length != mParams.length) {
                    continue;
                }
                for (int i = 0; i < mParams.length; i++) {
                    if (!mParams[i].equals(mDecParams[i])) {
                        paramsMatch = false;
                        break;
                    }
                }
                if (!paramsMatch) {
                    continue;
                }

                return mDec.name.real();
            }
            return null; // not found
        }
    }

    @Override
    public boolean isResolved() {
        return this.type.isResolved();
    }

    @Override
    public double similarity(Declaration other) {
    	return 0.0; // not implemented
    }

    @Override
    public boolean match(Declaration declaration) {
        if (declaration instanceof ClassDeclaration) {
            return ((ClassDeclaration) declaration).type.match(this.type);
        }
        return false;
    }

    @Override
    public String toString(boolean identity) {
        String str = this.modifiers.toString(identity);
        if (str.length() > 0) {
            str += " ";
        }
        str += this.is_interface ? "interface " : "class ";
        if (this.type == null) {
            str += "<nulltype>";
        } else {
            str += this.type.toString(identity);
        }
        if (this.base != null) {
            str += " extends " + this.base.toString(identity);
        }
        str += " {\n";
        for (FieldDeclaration fdec : this.fields) str += "    " + fdec.toString(identity) + "\n";
        for (ConstructorDeclaration cdec : this.constructors) str += "    " + cdec.toString(identity) + "\n";
        for (MethodDeclaration mdec : this.methods) str += "    " + mdec.toString(identity) + "\n";

        if (this.subclasses.length > 0) {
            str += "\n";
            String subclassesStr = "";
            for (ClassDeclaration cDec : this.subclasses) {
                subclassesStr += cDec.toString(identity);
                subclassesStr += "\n";
            }
            for (String line : subclassesStr.split("\\r?\\n", -1)) {
                if (line.length() > 0) {
                    str += "    " + line + "\n";
                } else {
                    str += "\n";
                }
            }
        }

        str += "}";

        return str;
    }

    @Override
    public String getTemplateLogIdentity() {
        return "class " + this.type.typeName;
    }

    @Override
    protected void debugString(StringBuilder str, String indent) {
        
    }
}
