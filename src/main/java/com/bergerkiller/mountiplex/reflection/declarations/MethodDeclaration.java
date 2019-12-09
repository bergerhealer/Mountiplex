package com.bergerkiller.mountiplex.reflection.declarations;

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
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

public class MethodDeclaration extends Declaration {
    public Method method;
    public final ModifierDeclaration modifiers;
    public final TypeDeclaration returnType;
    public final NameDeclaration name;
    public final ParameterListDeclaration parameters;
    public final String body;
    public final Requirement[] bodyRequirements;

    public MethodDeclaration(ClassResolver resolver, Method method) {
        super(resolver);
        this.method = method;
        this.modifiers = new ModifierDeclaration(resolver, method.getModifiers());
        this.returnType = TypeDeclaration.fromType(resolver, method.getGenericReturnType());
        this.name = new NameDeclaration(resolver, method.getName(), null);
        this.parameters = new ParameterListDeclaration(resolver, method.getGenericParameterTypes());
        this.body = null;
        this.bodyRequirements = new Requirement[0];
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
    public void addAsRequirement(CtClass invokerClass, String name) throws CannotCompileException, NotFoundException {
        // Create a new method with the exposed parameter types and return type
        StringBuilder methodBody = new StringBuilder();
        methodBody.append("private final ");
        methodBody.append(ReflectionUtil.getTypeName(this.returnType.exposed().type));
        methodBody.append(' ').append(name).append('(');
        methodBody.append("Object instance");
        for (int i = 0; i < this.parameters.parameters.length; i++) {
            ParameterDeclaration param = this.parameters.parameters[i];

            methodBody.append(", ");
            methodBody.append(ReflectionUtil.getTypeName(param.type.exposed().type));
            methodBody.append(' ').append(param.name.real());

            // If converted or a primitive type, name the input parameter '_conv_input'
            if (param.type.cast != null || param.type.isPrimitive) {
                methodBody.append("_conv_input");
            }
        }
        methodBody.append(") {\n");

        // Add fast method for the underlying method invoking
        String methodFieldName = name + "_method";
        {
            FastMethod<Object> method = new FastMethod<Object>();
            method.init(this);

            CtClass fastMethodClass = ClassPool.getDefault().get(FastMethod.class.getName());
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

            if (isVarArgsInvoke) {
                // Assign to varargs element
                methodBody.append("  ").append(name).append("_input_args");
                methodBody.append('[').append(i).append("] = ");
            } else if (param.type.cast != null || param.type.isPrimitive) {
                // Is converted or boxed, assign to its own Object field
                methodBody.append("  Object ").append(param.name.real());
                methodBody.append(" = ");
            }

            if (param.type.cast == null) {
                if (param.type.isPrimitive) {
                    // Box it before assigning
                    methodBody.append(BoxedType.getBoxedType(param.type.type).getSimpleName());
                    methodBody.append(".valueOf(");
                    methodBody.append(param.name.real()).append("_conv_input);\n");
                } else if (isVarArgsInvoke) {
                    // Direct assigning, no need when varargs is not used (method parameter)
                    methodBody.append(param.name.real()).append(";\n");
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
                CtClass converterClass = ClassPool.getDefault().get(Converter.class.getName());
                CtField ctConverterField = new CtField(converterClass, converterFieldName, invokerClass);
                ctConverterField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
                invokerClass.addField(ctConverterField, GeneratorArgumentStore.initializeField(converter));
            }

            methodBody.append("this.").append(converterFieldName);
            methodBody.append(".convertInput(");
            if (param.type.cast.isPrimitive) {
                methodBody.append(BoxedType.getBoxedType(param.type.cast.type).getSimpleName());
                methodBody.append(".valueOf(");
                methodBody.append(param.name.real()).append("_conv_input)");
            } else {
                methodBody.append(param.name.real()).append("_conv_input)");
            }
            methodBody.append(");\n");
        }
        
        // If not void, store return type, with possible cast
        // We can use Object when a converter is going to be used
        if (!this.returnType.type.equals(void.class)) {
            methodBody.append("  ");
            if (this.returnType.cast != null) {
                // Store as Object with _conv_input before conversion
                methodBody.append("Object ").append(name).append("_return_conv_input = ");
            } else if (this.returnType.isPrimitive) {
                // Cast to boxed type
                Class<?> boxedType = BoxedType.getBoxedType(this.returnType.type);
                methodBody.append(ReflectionUtil.getTypeName(boxedType)).append(' ');
                methodBody.append(name).append("_return = ");
                methodBody.append(ReflectionUtil.getCastString(boxedType));
            } else {
                // Cast to returned type
                methodBody.append(ReflectionUtil.getTypeName(this.returnType.type)).append(' ');
                methodBody.append(name).append("_return = ");
                methodBody.append(ReflectionUtil.getCastString(this.returnType.type));
            }
        }

        // Invoke the method
        methodBody.append("this.").append(methodFieldName);
        if (isVarArgsInvoke) {
            methodBody.append(".invokeVA(instance, ").append(name).append("_input_args);\n");
        } else {
            methodBody.append(".invoke(instance");
            for (int i = 0; i < this.parameters.parameters.length; i++) {
                ParameterDeclaration param = this.parameters.parameters[i];
                methodBody.append(", ").append(param.name.real());
            }
            methodBody.append(");\n");
        }

        // Converter
        if (this.returnType.cast != null) {
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
                CtClass converterClass = ClassPool.getDefault().get(Converter.class.getName());
                CtField ctConverterField = new CtField(converterClass, converterFieldName, invokerClass);
                ctConverterField.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
                invokerClass.addField(ctConverterField, GeneratorArgumentStore.initializeField(converter));
            }

            // Perform conversion in body
            Class<?> rType;
            if (this.returnType.cast.isPrimitive) {
                rType = BoxedType.getBoxedType(this.returnType.cast.type);
            } else {
                rType = this.returnType.cast.type;
            }
 
            methodBody.append("  ").append(ReflectionUtil.getTypeName(rType));
            methodBody.append(' ').append(name).append("_return = ");
            methodBody.append(ReflectionUtil.getCastString(rType)).append("this.");
            methodBody.append(converterFieldName).append(".convertInput(");
            methodBody.append(name).append("_return_conv_input);\n");
        }

        // Return the result from the method. Unbox it if required. Only if not void.
        if (!this.returnType.type.equals(void.class)) {
            methodBody.append("  return ");
            methodBody.append(name).append("_return");
            if (this.returnType.exposed().isPrimitive) {
                methodBody.append('.').append(this.returnType.exposed().type.getSimpleName());
                methodBody.append("Value()");
            }
            methodBody.append(";\n");
        }

        // Close body
        methodBody.append("}");

        // Create the method and add it to the class
        CtMethod method = CtNewMethod.make(methodBody.toString(), invokerClass);
        invokerClass.addMethod(method);
    }

    @Override
    public MethodDeclaration discover() {
        if (!this.isValid() || !this.isResolved()) {
            return null;
        }

        // Always exists when a body is specified
        if (this.body != null) {
            return this;
        }

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
        TypeDeclaration typeDec = TypeDeclaration.parse(this.getResolver().getDeclaredClassName());
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

        // First try to find the method in a quick way
        try {
            java.lang.reflect.Method method;
            method = this.getResolver().getDeclaredClass().getDeclaredMethod(this.name.value(), this.parameters.toParamArray());
            MethodDeclaration result = new MethodDeclaration(this.getResolver(), method);
            if (result.match(this)) {
                this.method = method;
                return this;
            }
        } catch (NoSuchMethodException | SecurityException e) {
            // Ignored
        }

        // Try looking through the class itself by using a ClassDeclaration to preprocess it
        {
            ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, this.getResolver().getDeclaredClass());
            MethodDeclaration result = cDec.findMethod(this);
            if (result != null) {
                this.method = result.method;
                return this;
            }
        }

        // Check the superclasses of the declaring class as well
        for (TypeDeclaration superType : typeDec.getSuperTypes()) {
            ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, superType.type);
            MethodDeclaration result = cDec.findMethod(this);
            if (result != null) {
                this.method = result.method;
                return this;
            }
        }

        // Not found
        return null;
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
            Declaration foundDeclaration = null;

            // Check name not already resolved
            for (Requirement req : result) {
                if (req.name.equals(name)) {
                    foundDeclaration = req.declaration;
                    break;
                }
            }

            // Find it by name
            if (foundDeclaration == null) {
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
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaring class for requirement not found: " + declaredClassName);
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + parsedDeclaration.toString());
                    }
                    continue;
                }

                // Check it was fully resolved too
                if (!parsedDeclaration.isResolved()) {
                    // Log this
                    if (this.getResolver().getLogErrors()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration could not be resolved for: " + declaredClassName);
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + parsedDeclaration.toString());
                    }
                    continue;
                }

                // Find the Declaration object
                foundDeclaration = parsedDeclaration.discover();
                if (foundDeclaration == null) {
                    if (this.getResolver().getLogErrors()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration could not be found inside: " + declaredClassName);
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Declaration: " + parsedDeclaration.toString());
                    }
                    continue; // Not found!
                }

                // Add it so code invoker can include it in the code generation
                result.add(new Requirement(name, foundDeclaration));
            }

            // Find the start of the contents before the #name
            // This will be the input value object for the field
            int instanceEndIdx = seek - 1;
            while (instanceEndIdx > 0 && body.charAt(instanceEndIdx) == ' ') {
                instanceEndIdx--;
            }
            int instanceStartIdx = instanceEndIdx;
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
            String instanceName = body.substring(instanceStartIdx, instanceEndIdx);

            if (foundDeclaration instanceof FieldDeclaration) {
                TypeDeclaration fieldType = ((FieldDeclaration) foundDeclaration).type;
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

                // For static fields, use null instanceName
                if (((FieldDeclaration) foundDeclaration).modifiers.isStatic()) {
                    instanceName = "null";
                }

                // When setting, find the end of the piece of 'value code'
                // For example, this will find 'helper.counter + 5' in:
                // object#field = helper.counter + 5;
                if (setOperationValueStartIdx != -1) {
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
                    replacement.append("this.").append(name).append(".set");
                    if (fieldType.isPrimitive) {
                        replacement.append(BoxedType.getBoxedType(fieldType.type).getSimpleName());
                    }
                    replacement.append('(');
                    replacement.append(instanceName);
                    replacement.append(", ");
                    replacement.append(valueName);
                    replacement.append(')');

                    // Replace portion in body with replacement
                    body.replace(instanceStartIdx, setOperationValueEndIdx, replacement.toString());
                } else {
                    // Modify the original body to use the field getter method instead
                    StringBuilder replacement = new StringBuilder();
                    if (fieldType.isPrimitive) {
                        // Primitive-specific getter method
                        replacement.append("this.").append(name).append(".get");
                        replacement.append(BoxedType.getBoxedType(fieldType.type).getSimpleName());
                    } else {
                        // Get + cast
                        replacement.append(ReflectionUtil.getCastString(fieldType.type));
                        replacement.append("this.").append(name).append(".get");
                    }
                    replacement.append('(');
                    replacement.append(instanceName);
                    replacement.append(')');

                    // Replace portion in body with replacement
                    body.replace(instanceStartIdx, nameEndIdx, replacement.toString());
                }
            } // field handling end

            if (foundDeclaration instanceof MethodDeclaration) {
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
                        MountiplexUtil.LOGGER.warning("Method body: " + instanceName + "#" + name);
                    }
                } else {
                    // For static methods, use null instanceName
                    if (((MethodDeclaration) foundDeclaration).modifiers.isStatic()) {
                        instanceName = "null";
                    }

                    // Replace instanceName#name ( with our invoker
                    StringBuilder replacement = new StringBuilder();
                    replacement.append("this.").append(name);
                    replacement.append('(').append(instanceName);

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
            } // method handling end
        }

        return result.toArray(new Requirement[result.size()]);
    }
}
