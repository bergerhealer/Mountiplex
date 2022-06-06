package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.reflection.util.GeneratorArgumentStore;
import com.bergerkiller.mountiplex.reflection.util.MethodBodyBuilder;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLCtNewMethod;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;

public class MethodDeclaration extends Declaration {
    public Method method;
    public Constructor<Object> constructor;
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration returnType;
    public final NameDeclaration name;
    public final ParameterListDeclaration parameters;
    public final String body;
    public final Requirement[] bodyRequirements;

    @SuppressWarnings("unchecked")
    public MethodDeclaration(ClassResolver resolver, Constructor<?> constructor) {
        super(resolver);

        try {
            this.method = null;
            this.constructor = (Constructor<Object>) constructor;
            this.modifiers = new ModifierDeclaration(resolver, constructor.getModifiers());
            this.returnType = TypeDeclaration.fromClass(constructor.getDeclaringClass());
            this.name = new NameDeclaration(resolver, "<init>", null);
            this.parameters = new ParameterListDeclaration(resolver, constructor.getGenericParameterTypes());
            this.body = null;
            this.bodyRequirements = new Requirement[0];
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to read details of " + toDebugString(constructor), t);
        }
    }

    public MethodDeclaration(ClassResolver resolver, Method method) {
        super(resolver);

        // If classloader loading this class altered the bytecode, the result of alias will differ from name
        // In that case, store the name the classloader gives the method as an alias
        String name = MPLType.getName(method);
        String alias = Resolver.resolveMethodAlias(method, name);
        if (name.equals(alias)) {
            alias = null;
        }

        try {
            this.method = method;
            this.constructor = null;
            this.modifiers = new ModifierDeclaration(resolver, method.getModifiers() & ~Modifier.VOLATILE);
            this.returnType = TypeDeclaration.fromType(resolver, method.getGenericReturnType());
            this.name = new NameDeclaration(resolver, name, alias);
            this.parameters = new ParameterListDeclaration(resolver, method.getGenericParameterTypes());
            this.body = null;
            this.bodyRequirements = new Requirement[0];
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to read details of " + toDebugString(method), t);
        }
    }

    public MethodDeclaration(ClassResolver resolver, String declaration) {
        this(resolver, StringBuffer.of(declaration));
    }

    public MethodDeclaration(ClassResolver resolver, StringBuffer declaration) {
        super(resolver, declaration);
        this.method = null;
        this.constructor = null;
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
            // Go line by line processing the body
            // This way we can still handle special macros
            StringBuilder bodyBuilder = new StringBuilder();
            int curlyBrackets = 0;
            boolean done = false;
            while (true) {
                // Process current line up to the next newline
                boolean inString = false;
                int cIdx;
                for (cIdx = 0; cIdx < postfix.length(); cIdx++) {
                    char c = postfix.charAt(cIdx);
                    bodyBuilder.append(c);
                    if (c == '\n') {
                        cIdx++;

                        // Add all whitespaces to body
                        while (cIdx < postfix.length() && postfix.charAt(cIdx) == ' ') {
                            bodyBuilder.append(' ');
                            cIdx++;
                        }
                        break;
                    } else if (c == '\"') {
                        inString = !inString;
                    } else if (inString) {
                        continue;
                    } else if (c == '{') {
                        curlyBrackets++;
                    } else if (c == '}') {
                        curlyBrackets--;
                        if (curlyBrackets == 0) {
                            done = true;
                            cIdx++;
                            break;
                        }
                    }
                }

                // Move current postfix to past what we have parsed so far
                postfix = postfix.substring(cIdx);
                this.setPostfix(postfix);
                if (done || this.getPostfix().length() == 0) {
                    break;
                }

                // Perform internal parsing of macros
                while (this.nextInternal()) {
                    postfix = this.getPostfix();
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

            // Resolve requirements used in the body. This looks at # tokens in the body.
            // Only do this when resolver is not in a mode of generating the templates (then we don't care)
            if (!this.getResolver().isGenerating()) {
                this.bodyRequirements = processRequirements(bodyBuilder);
            } else {
                this.bodyRequirements = new Requirement[0];
            }

            // Correct indentation of body and done
            this.body = SourceDeclaration.trimIndentation(bodyBuilder.toString());
        } else {
            this.body = null;
            this.bodyRequirements = new Requirement[0];
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

    /* Hidden constructor for changing the name of the method */
    private MethodDeclaration(MethodDeclaration original, NameDeclaration newName) {
        super(original.getResolver());
        this.method = original.method;
        this.constructor = original.constructor;
        this.modifiers = original.modifiers;
        this.returnType = original.returnType;
        this.name = newName;
        this.parameters = original.parameters;
        this.body = original.body;
        this.bodyRequirements = original.bodyRequirements;
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
            if (!( name.match(method.name) &&
                   returnType.match(method.returnType) &&
                   parameters.match(method.parameters) ))
            {
                return false;
            }

            // For modifiers, both must be static or non-static
            if (this.modifiers.isStatic() != method.modifiers.isStatic()) {
                return false;
            }

            // Note: we do not check modifiers here
            // When modifiers differ, we log a warning elsewhere
            // modifiers.match(method.modifiers);
            return true;
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

    /**
     * Gets the Class in which this method is declared
     * 
     * @return declaring class
     */
    public Class<?> getDeclaringClass() {
        if (this.body == null && this.method != null) {
            return this.method.getDeclaringClass();
        } else if (this.body == null && this.constructor != null) {
            return this.constructor.getDeclaringClass();
        } else {
            return this.getResolver().getDeclaredClass();
        }
    }

    /**
     * Gets the ASM library method descriptor String for the internal (not cast) interface of this method.
     * This has signature (InstanceType omitted if static):
     * <pre>ReturnType (InstanceType, Parameters...)</pre>
     * If types used by this method are not accessible, Object is used instead.
     * 
     * @return ASM method descriptor
     */
    public String getASMInvokeDescriptor() {
        Class<?>[] params;
        if (this.modifiers.isStatic()) {
            params = new Class[this.parameters.parameters.length];
            for (int i = 0; i < this.parameters.parameters.length; i++) {
                params[i] = getAccessibleType(this.parameters.parameters[i].type);
            }
        } else {
            params = new Class[this.parameters.parameters.length + 1];
            params[0] = getAccessibleType(this.getDeclaringClass());
            for (int i = 0; i < this.parameters.parameters.length; i++) {
                params[i+1] = getAccessibleType(this.parameters.parameters[i].type);
            }
        }
        return MPLType.getMethodDescriptor(this.returnType.type, params);
    }

    private static Class<?> getAccessibleType(TypeDeclaration type) {
        if (type == null) {
            throw new IllegalArgumentException("Input type is null");
        } else if (!type.isResolved()) {
            throw new IllegalArgumentException("Input type " + type + " was not resolved");
        } else {
            return getAccessibleType(type.type);
        }
    }

    private static Class<?> getAccessibleType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Input type is null");
        }
        return Resolver.getMeta(type).isPublic ? type : Object.class;
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

    @Override
    public void modifyBodyRequirement(Requirement requirement, StringBuilder body, String instanceName, String requirementName, int instanceStartIdx, int nameEndIdx) {
        // Modifiers for checking public, if missing (0), none of the modifiers match
        // When the class in which the method is declared is not accessible, force field as unavailable
        Class<?> methodDeclaringClass = this.getDeclaringClass();
        boolean canCallDirectly = false;
        if (methodDeclaringClass != null && Resolver.isPublic(methodDeclaringClass)) {
            if (this.method != null) {
                canCallDirectly = Modifier.isPublic(this.method.getModifiers());
            } else if (this.constructor != null) {
                canCallDirectly = Modifier.isPublic(this.constructor.getModifiers());
            }
        }

        // Also check we aren't using any converters
        if (canCallDirectly) {
            if (this.returnType.cast != null) {
                canCallDirectly = false;
            } else {
                for (ParameterDeclaration param : this.parameters.parameters) {
                    if (param.type.cast != null) {
                        canCallDirectly = false;
                        break;
                    }
                }
            }
        }

        // If method is accessible, call it directly rather than using reflection/generated code
        if (canCallDirectly) {
            MethodBodyBuilder replacement = new MethodBodyBuilder();
            if (this.method != null) {
                if (this.modifiers.isStatic()) {
                    // Replace with ClassName.MethodName
                    replacement.append(MPLType.getName(this.method.getDeclaringClass()));
                } else {
                    // Replace with instanceName.MethodName
                    replacement.append(instanceName);
                }
                replacement.append('.');
                replacement.append(MPLMemberResolver.IGNORE_PREFIX); // to prevent double-renaming
                replacement.append(MPLType.getName(this.method));
            } else if (this.constructor != null) {
                // Replace with new <DeclaringClass>() constructor expression
                replacement.append("new ");
                replacement.append(MPLMemberResolver.IGNORE_PREFIX);
                replacement.append(MPLType.getName(this.constructor.getDeclaringClass()));
            }
            body.replace(instanceStartIdx, nameEndIdx, replacement.toString());
            return;
        }

        // Find the first opening parenthesis after the name
        int firstOpenIndex = nameEndIdx;
        while (firstOpenIndex < body.length()) {
            char c = body.charAt(firstOpenIndex);
            if (c == ' ') {
                firstOpenIndex++;
            } else if (c == '(') {
                break;
            } else {
                // Invalid!
                firstOpenIndex = -1;
                break;
            }
        }
        if (firstOpenIndex == -1) {
            // Invalid! Not a method!
            if (this.getResolver().getLogErrors()) {
                MountiplexUtil.LOGGER.warning("Requirement refers to method but is used as field");
                MountiplexUtil.LOGGER.warning("Method body: " + instanceName + "#" + requirementName);
            }
        } else {
            // Replace instanceName#name ( with our invoker
            // For static methods, only #name ( is replaced
            MethodBodyBuilder replacement = new MethodBodyBuilder();
            replacement.append("this.").append(requirementName);
            replacement.append('(');
            replacement.append(instanceName);

            // Add a comma if there is something inside the ()
            for (int i = firstOpenIndex + 1; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == ' ') continue;
                if (c == ')') break;

                replacement.append(", ");
                break;
            }

            body.replace(instanceStartIdx, firstOpenIndex+1, replacement.toString());
        }

        // Custom method body with reflection/converters is used
        requirement.setProperty("generateMethod");
    }

    @Override
    public void addAsRequirement(Requirement requirement, CtClass invokerClass, String name) throws CannotCompileException, NotFoundException {
        // If we could access the method directly, then we don't need to generate a reflection call stub
        if (!requirement.hasProperty("generateMethod")) {
            return;
        }

        // Create a new method with the exposed parameter types and return type
        MethodBodyBuilder methodBody = new MethodBodyBuilder();
        methodBody.append("private final ");
        methodBody.append(ReflectionUtil.getAccessibleTypeName(this.returnType.exposed().type));
        methodBody.append(' ').append(name).append('(');
        methodBody.append("Object instance");
        for (int i = 0; i < this.parameters.parameters.length; i++) {
            ParameterDeclaration param = this.parameters.parameters[i];

            methodBody.append(", ");
            methodBody.appendAccessibleTypeName(param.type.exposed().type);
            methodBody.append(' ').append(param.name.real());

            // If converted or a primitive type, name the input parameter '_conv_input'
            if ((param.type.cast != null && param.type.cast.type != Object.class) || param.type.isPrimitive) {
                methodBody.append("_conv_input");
            }
        }
        methodBody.append(") {\n");

        // Add fast method for the underlying method invoking
        String methodFieldName = name + "_method";
        {
            FastMethod<Object> method = new FastMethod<Object>();
            method.init(this);

            ClassPool tmp_pool = new ClassPool();
            tmp_pool.insertClassPath(new ClassClassPath(FastMethod.class));
            CtClass fastMethodClass = tmp_pool.get(FastMethod.class.getName());

            CtField ctConverterField = new CtField(fastMethodClass, methodFieldName, invokerClass);
            ctConverterField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            invokerClass.addField(ctConverterField, GeneratorArgumentStore.initializeField(method));
        }

        boolean isVarArgsInvoke = (this.parameters.parameters.length > 5);

        // Add Object[] input array when varargs are used
        if (isVarArgsInvoke) {
            methodBody.append("  Object[] ").append(name).append("_input_args");
            methodBody.append(" = new Object[").append(this.parameters.parameters.length).append("];\n");
        }

        for (int i = 0; i < this.parameters.parameters.length; i++) {
            ParameterDeclaration param = this.parameters.parameters[i];
            boolean hasConversion = param.type.cast != null && param.type.cast.type != Object.class;

            if (isVarArgsInvoke) {
                // Assign to varargs element
                methodBody.append("  ")
                          .appendFieldName(name, "_input_args")
                          .append('[').append(i).append("] = ");
            } else if (hasConversion || param.type.isPrimitive) {
                // Is converted or boxed, assign to its own Object field
                methodBody.append("  Object ")
                          .append(param.name.real())
                          .append(" = ");
            }

            // Assigning a boxed version of a primitive input parameter
            if (param.type.cast == null && param.type.isPrimitive) {
                methodBody.appendBoxPrimitive(param.type.type, param.name.real(), "_conv_input")
                          .appendEnd();
                continue;
            }

            // When no conversion happens, assign to the var-args array right away and/or stop.
            if (!hasConversion) {
                if (isVarArgsInvoke) {
                    methodBody.append(param.name.real()).appendEnd();
                }
                continue;
            }

            // Generate name for the converter field
            String converterFieldName = name + "_conv_" + param.name.real();

            // Find converter
            Converter<Object, Object> converter = Conversion.find(param.type.cast, param.type);
            if (converter == null) {
                throw new RuntimeException("Failed to find converter for parameter " + param.name.real() +
                        " of method " + name + " (" + param.type.cast.toString(true) +
                        " -> " + param.type.toString(true) + ")");
            }

            // Add to class definition
            {
                ClassPool tmp_pool = new ClassPool();
                tmp_pool.insertClassPath(new ClassClassPath(Converter.class));
                CtClass converterClass = tmp_pool.get(Converter.class.getName());
                CtField ctConverterField = new CtField(converterClass, converterFieldName, invokerClass);
                ctConverterField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
                invokerClass.addField(ctConverterField, GeneratorArgumentStore.initializeField(converter, Converter.class));
            }

            methodBody.append("this.").append(converterFieldName);
            methodBody.append(".convertInput(");
            if (param.type.cast.isPrimitive) {
                methodBody.appendBoxPrimitive(param.type.cast.type, param.name.real(), "_conv_input");
            } else {
                methodBody.appendFieldName(param.name.real(), "_conv_input");
            }
            methodBody.append(')').appendEnd();
        }

        // If not void, store return type, with possible cast
        // We can use Object when a converter is going to be used
        if (!this.returnType.type.equals(void.class)) {
            methodBody.append("  ");
            if (this.returnType.cast != null && this.returnType.cast.type == Object.class) {
                // Returns an Object, so no conversion or casting has to occur
                methodBody.append("Object ");
                methodBody.appendFieldName(name, "_return").append(" = ");
            } else if (this.returnType.cast != null) {
                // Store as Object with _conv_input before conversion
                methodBody.append("Object ");
                methodBody.appendFieldName(name, "_return_conv_input").append(" = ");
            } else if (this.returnType.isPrimitive) {
                // Cast to boxed type
                Class<?> boxedType = BoxedType.getBoxedType(this.returnType.type);
                methodBody.appendTypeName(boxedType).append(' ');
                methodBody.appendFieldName(name, "_return").append(" = ");
                methodBody.appendTypeCast(boxedType);
            } else {
                // Cast to returned type
                methodBody.appendAccessibleTypeName(this.returnType.type).append(' ');
                methodBody.appendFieldName(name, "_return").append(" = ");
                methodBody.appendAccessibleTypeCast(this.returnType.type);
            }
        }

        // Invoke the method
        methodBody.append("this.").append(methodFieldName);
        if (isVarArgsInvoke) {
            methodBody.append(".invokeVA(instance, ").appendFieldName(name, "_input_args").append(')')
                      .appendEnd();
        } else {
            methodBody.append(".invoke(instance");
            for (int i = 0; i < this.parameters.parameters.length; i++) {
                ParameterDeclaration param = this.parameters.parameters[i];
                methodBody.append(", ").append(param.name.real());
            }
            methodBody.append(')')
                      .appendEnd();
        }

        // Converter
        if (this.returnType.cast != null && this.returnType.cast.type != Object.class) {
            // Generate name for the return type converter field
            String converterFieldName = name + "_conv_return";

            // Find return type converter
            Converter<Object, Object> converter = Conversion.find(this.returnType, this.returnType.cast);
            if (converter == null) {
                throw new RuntimeException("Failed to find converter for return value " +
                        " of method " + name + " (" + this.returnType.toString(true) +
                        " -> " + this.returnType.cast.toString(true) + ")");
            }

            // Add to class definition
            {
                ClassPool tmp_pool = new ClassPool();
                tmp_pool.insertClassPath(new ClassClassPath(Converter.class));
                CtClass converterClass = tmp_pool.get(Converter.class.getName());
                CtField ctConverterField = new CtField(converterClass, converterFieldName, invokerClass);
                ctConverterField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
                invokerClass.addField(ctConverterField, GeneratorArgumentStore.initializeField(converter, Converter.class));
            }

            // Perform conversion in body
            Class<?> rType;
            if (this.returnType.cast.isPrimitive) {
                rType = BoxedType.getBoxedType(this.returnType.cast.type);
            } else {
                rType = this.returnType.cast.type;
            }
 
            methodBody.append("  ")
                      .appendAccessibleTypeName(rType).append(' ').appendFieldName(name, "_return")
                      .append(" = ")
                      .appendAccessibleTypeCast(rType)
                      .append("this.").append(converterFieldName).append(".convertInput(")
                      .appendFieldName(name, "_return_conv_input").append(')')
                      .appendEnd();
        }

        // Return the result from the method. Unbox it if required. Only if not void.
        if (!this.returnType.type.equals(void.class)) {
            methodBody.append("  return ");
            methodBody.appendFieldName(name, "_return");
            if (this.returnType.exposed().isPrimitive) {
                methodBody.appendUnboxPrimitive(this.returnType.exposed().type);
            }
            methodBody.appendEnd();
        }

        // Close body
        methodBody.append("}");

        // Create the method and add it to the class
        try {
            CtMethod method = MPLCtNewMethod.make(methodBody.toString(), invokerClass);
            invokerClass.addMethod(method);
        } catch (CannotCompileException ex) {
            MountiplexUtil.LOGGER.severe("Failed to compile method: " + methodBody.toString());
            throw ex;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public MethodDeclaration discover() {
        if (!this.isValid() || !this.isResolved()) {
            return null;
        }

        // Always exists when a body is specified
        if (this.body != null) {
            return this;
        }

        // If the method in which it is declared cannot be found, fail right away
        if (this.getResolver().getDeclaredClass() == null) {
            return null;
        }

        // Resolve the type of this declaration
        TypeDeclaration typeDec = TypeDeclaration.parse(this.getResolver().getDeclaredClassName());

        // Only do this when only an alias is specified, for example, inside ClassHook
        if (this.name.isAliasOnly()) {
            // Resolve the Class Declaration of the Class where this method is declared
            // Then try to find the method in there
            {
                ClassDeclaration cDec = Resolver.resolveClassDeclaration(
                        this.getResolver().getDeclaredClassName(),
                        this.getResolver().getDeclaredClass());
                if (cDec != null) {
                    MethodDeclaration result = cDec.findMethod(this);
                    if (result != null) {
                        return result;
                    }
                }
            }

            // Check the superclasses of the declaring class as well
            if (typeDec != null) {
                for (TypeDeclaration superType : typeDec.getSuperTypes()) {
                    ClassDeclaration cDec = Resolver.resolveClassDeclaration(superType.typePath, superType.type);
                    if (cDec != null) {
                        MethodDeclaration result = cDec.findMethod(this);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }

        // At this point we are no longer searching in the Class Declaration 'pool'
        // Because of that, we must now ask the Resolver to give us the real method name
        MethodDeclaration nameResolved = this.resolveName();

        if (nameResolved.name.value().equals("<init>")) {
            // Try to find a constructor matching the parameter types of this method declaration
            // Name is ignored entirely
            try {
                this.constructor = (Constructor<Object>) this.getResolver().getDeclaredClass().getDeclaredConstructor(nameResolved.parameters.toParamArray());
                return this;
            } catch (NoSuchMethodException | SecurityException e) {
                // Ignored
            }
        } else {
            // First try to find the method in a quick way
            try {
                java.lang.reflect.Method method;
                method = MPLType.getDeclaredMethod(this.getResolver().getDeclaredClass(), nameResolved.name.value(), nameResolved.parameters.toParamArray());
                MethodDeclaration result = new MethodDeclaration(this.getResolver(), method);
                if (result.match(nameResolved) && checkPublic(method)) {
                    this.method = method;
                    return this;
                }
            } catch (NoSuchMethodException | SecurityException e) {
                // Ignored
            }

            // Try looking through the class itself by using a ClassDeclaration to preprocess it
            {
                ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, this.getResolver().getDeclaredClass());
                MethodDeclaration result = cDec.findMethod(nameResolved);
                if (result != null && checkPublic(result.method)) {
                    this.method = result.method;
                    return this;
                }
            }

            // Check the superclasses of the declaring class as well
            for (TypeDeclaration superType : typeDec.getSuperTypes()) {
                ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, superType.type);
                MethodDeclaration result = cDec.findMethod(nameResolved);
                if (result != null && checkPublic(result.method)) {
                    this.method = result.method;
                    return this;
                }
            }
        }

        // Not found
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

        MethodDeclaration[] alternatives;
        if (this.modifiers.isStatic()) {
            alternatives = ReflectionUtil.getAllMethods(declaringClass)
                    .filter(m -> Modifier.isStatic(m.getModifiers()))
                    .map(m -> new MethodDeclaration(getResolver(), m))
                    .toArray(MethodDeclaration[]::new);
        } else {
            alternatives = ReflectionUtil.getAllMethods(declaringClass)
                    .filter(m -> !Modifier.isStatic(m.getModifiers()))
                    .filter(ReflectionUtil.createDuplicateMethodFilter())
                    .map(m -> new MethodDeclaration(getResolver(), m))
                    .toArray(MethodDeclaration[]::new);
        }
        sortSimilarity(this, alternatives);
        FieldLCSResolver.logAlternatives("method", alternatives, this, true);
    }

    @Override
    public String getTemplateLogIdentity() {
        StringBuilder str = new StringBuilder();
        if (this.modifiers.isStatic()) {
            str.append("static ");
        }
        str.append("method ");
        str.append(this.returnType.exposed().typeName);
        str.append(' ');
        str.append(name.toString());
        str.append('(');
        boolean first = true;
        for (ParameterDeclaration param : this.parameters.parameters) {
            if (first) {
                first = false;
            } else {
                str.append(", ");
            }
            str.append(param.name.toString());
        }
        str.append(')');
        return str.toString();
    }

    private boolean checkPublic(java.lang.reflect.Method method) {
        return !this.modifiers.isPublic() || Modifier.isPublic(method.getModifiers());
    }

    private Requirement[] processRequirements(StringBuilder body) {
        ArrayList<Requirement> result = new ArrayList<Requirement>();

        for (int seek = 1; seek < body.length(); seek++) {
            if (body.charAt(seek) != '#') {
                continue;
            }

            // Ignore ##
            if (seek >= 1 && body.charAt(seek-1) == '#') {
                continue;
            }
            if ((seek+1) < body.length() && body.charAt(seek+1) == '#') {
                continue;
            }

            // Figure out whether this is a Field or Method
            // Methods have an opening (, fields everything else
            int nameEndIdx = -1;
            boolean isMethod = false;
            boolean isField = false;
            for (int i = seek + 1; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '(') {
                    nameEndIdx = i;
                    isMethod = true;
                    break;
                } else if (!Character.isLetterOrDigit(c) && c != '_') {
                    nameEndIdx = i;
                    isField = true;
                    break;
                }
            }
            String name = body.substring(seek + 1, nameEndIdx);
            Requirement foundRequirement = null;

            // Check name not already resolved
            for (Requirement req : result) {
                if (req.name.equals(name)) {
                    foundRequirement = req;
                    break;
                }
            }

            // Find it by name
            if (foundRequirement == null) {
                Declaration parsedDeclaration = null;

                // Find the declaration matching this name
                for (Requirement req : this.getResolver().getRequirements()) {
                    if (!req.name.equals(name)) {
                        continue;
                    }
                    if (isMethod && req.declaration instanceof MethodDeclaration) {
                        parsedDeclaration = req.declaration;
                        break;
                    } else if (isField && req.declaration instanceof FieldDeclaration) {
                        parsedDeclaration = req.declaration;
                        break;
                    }
                }

                // If not found, skip it
                if (parsedDeclaration == null) {
                    continue;
                }

                // Further resolve the declaration if required

                // If class not found
                String declaredClassName = parsedDeclaration.getResolver().getDeclaredClassName();
                if (parsedDeclaration.getResolver().getDeclaredClass() == null) {
                    // Log this
                    if (this.getResolver().getLogErrors()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Requirement declaration declaring Class not found: ??" + declaredClassName + "??");
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + parsedDeclaration.toString());
                    }
                    continue;
                }

                // Check it was fully resolved too
                if (!parsedDeclaration.isResolved()) {
                    // Log this
                    if (this.getResolver().getLogErrors()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Requirement declaration could not be resolved for: " + declaredClassName);
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + parsedDeclaration.toString());
                    }
                    continue;
                }

                // Find the Declaration object
                Declaration foundDeclaration = parsedDeclaration.discover();
                if (foundDeclaration == null) {
                    if (this.getResolver().getLogErrors()) {
                        parsedDeclaration.discoverAlternatives();
                    }
                    continue; // Not found!
                }

                // Add it so code invoker can include it in the code generation
                foundRequirement = new Requirement(name, foundDeclaration);
                result.add(foundRequirement);
            }

            // Check static (no instance name)
            boolean isStatic = false;
            if (foundRequirement.declaration instanceof MethodDeclaration) {
                MethodDeclaration mDec = (MethodDeclaration) foundRequirement.declaration;
                isStatic = (mDec.constructor != null || mDec.modifiers.isStatic());
            }
            if (foundRequirement.declaration instanceof FieldDeclaration &&
                ((FieldDeclaration) foundRequirement.declaration).modifiers.isStatic())
            {
                isStatic = true;
            }

            // Find the start of the contents before the #name
            // This will be the input value object for the field
            int instanceEndIdx = seek - 1;
            int instanceStartIdx;
            if (isStatic && !Character.isLetterOrDigit(body.charAt(instanceEndIdx))) {
                instanceStartIdx = ++instanceEndIdx; // exclusive
            } else {
                while (instanceEndIdx > 0 && body.charAt(instanceEndIdx) == ' ') {
                    instanceEndIdx--;
                }
                instanceStartIdx = instanceEndIdx;
                instanceEndIdx++; // exclusive
                {
                    int parenthesesCtr = 0;
                    for (; instanceStartIdx >= 0; instanceStartIdx--) {
                        char c = body.charAt(instanceStartIdx);
                        if (c == ')') {
                            parenthesesCtr++;
                        } else if (c == '(') {
                            if (--parenthesesCtr < 0) {
                                break;
                            }
                        } else if (!Character.isLetterOrDigit(c) && c != '.' && parenthesesCtr == 0) {
                            break;
                        }
                    }
                    instanceStartIdx++; // exclude delimiter
                }
            }

            String instanceName = isStatic ? "null" : body.substring(instanceStartIdx, instanceEndIdx);

            // Alter the body to place the code that calls the requirement
            foundRequirement.declaration.modifyBodyRequirement(
                    foundRequirement, body, instanceName, name, instanceStartIdx, nameEndIdx);
        }

        return result.toArray(new Requirement[result.size()]);
    }

    /**
     * Asks the {@link Resolver} what the real method name is, given the provided signature
     * of this method declaration. If the name is not different, this same method declaration
     * is returned.
     * 
     * @return name-resolved method declaration
     */
    public MethodDeclaration resolveName() {
        if (!this.isResolved() || this.getResolver().getDeclaredClass() == null || this.body != null || this.name.value().equals("<init>")) {
            return this;
        }

        String resolvedName = Resolver.resolveMethodName(this.getResolver().getDeclaredClass(), this.name.value(), this.parameters.toParamArray());
        if (resolvedName != null && !resolvedName.equals(this.name.value())) {
            return new MethodDeclaration(this, this.name.rename(resolvedName));
        } else {
            return this;
        }
    }

    /**
     * Returns a new MethodDeclaration which refers to the same method as this one, but with an
     * alias set exactly as specified. Any previous aliases are lost.
     * 
     * @param alias
     * @return method declaration with the set alias
     */
    public MethodDeclaration setAlias(String alias) {
        if (alias.equals(this.name.alias())) {
            return this;
        } else {
            return new MethodDeclaration(this, new NameDeclaration(this.getResolver(), this.name.value(), alias));
        }
    }

    /**
     * Gets the name of the method actually accessed in generated code.
     * This is the reflection Method name if found, otherwise the name.value()
     * is used as a fallback.
     * 
     * @return accessed name
     */
    protected String getAccessedName() {
        return method != null ? MPLType.getName(method) : this.name.value();
    }

    /**
     * Tries really hard to stringify a method or constructor that might be corrupted
     *
     * @param method
     * @return String representation
     */
    private static String toDebugString(Executable executable) {
        if (executable == null) {
            return "<null>";
        }

        try {
            return executable.toGenericString();
        } catch (Throwable t) {}

        try {
            return executable.toString();
        } catch (Throwable t) {}

        StringBuilder str = new StringBuilder();
        if (executable instanceof Method) {
            try {
                str.append(((Method) executable).getReturnType());
                str.append(' ');
            } catch (Throwable t) {}
        }
        try {
            str.append(executable.getDeclaringClass());
            str.append('.');
        } catch (Throwable t) {}
        try {
            str.append(executable.getName());
        } catch (Throwable t) {}
        str.append('(');
        try {
            Class<?>[] params = executable.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    str.append(", ");
                }
                str.append(params[i]);
            }
        } catch (Throwable t) {
            str.append("???");
        }
        str.append(')');

        return str.toString();
    }
}
