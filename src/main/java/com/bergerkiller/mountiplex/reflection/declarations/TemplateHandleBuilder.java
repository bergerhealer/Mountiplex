package com.bergerkiller.mountiplex.reflection.declarations;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.Template.Handle;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.FastConstructor;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * Reads the abstract class information of a Template {@link Template.Handle Handle} type and generates an appropriate
 * implementation of the abstract methods.
 */
public class TemplateHandleBuilder<H extends Handle> {
    private final Template.Class<H> templateClass;
    private final Class<H> handleType;
    private Class<? extends H> handleImplType;
    private final FastConstructor<H> handleConstructor = new FastConstructor<H>();

    public TemplateHandleBuilder(Template.Class<H> templateClass) {
        this.templateClass = templateClass;
        this.handleType = templateClass.getHandleType();
        this.handleConstructor.initUnavailable("new " + handleType.getName() + "()");
    }

    public Class<? extends H> getImplType() {
        return this.handleImplType;
    }

    public H create(Object instance) {
        return handleConstructor.newInstance(instance);
    }

    //TODO: Might be incorrect if somebody named this something other than T!
    private Template.Class<?> getTemplateClass(Class<?> handleClass) {
        try {
            return (Template.Class<?>) handleClass.getField("T").get(null);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static boolean isGeneratedInvoker(Template.TemplateElement<?> templateElement) {
        return templateElement instanceof Template.AbstractMethod &&
               ((Template.AbstractMethod<?>) templateElement).invoker instanceof GeneratedInvoker;
    }

    @SuppressWarnings("unchecked")
    public void build() {
        if (this.templateClass.getType() == null) {
            throw new IllegalStateException("Handle internal type of " + this.handleType + " is null");
        }

        if (this.handleType == Template.Handle.class) {
            this.handleImplType = (Class<? extends H>) FallbackHandle.class;
            try {
                Constructor<? extends H> constructor;
                try {
                    constructor = this.handleImplType.getConstructor(Object.class);
                } catch (Throwable t) {
                    throw new IllegalStateException("Failed to find generated handle constructor for fallback handle", t);
                }

                this.handleConstructor.init(constructor);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
            return;
        }

        // Set up the class writer for the implementation of the handle type
        ExtendedClassWriter<H> cw = ExtendedClassWriter.builder(this.handleType)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL)
                .setPostfix("$impl").build();

        Class<?> topInstanceType = this.templateClass.getType();

        // Non-public classes can not be stored as a type in another class
        // In those cases, we can only access them through reflection, and in
        // the generated class we store an 'Object' field for the instance.
        boolean instanceAccessible = Resolver.getMeta(topInstanceType).isPublic;
        if (!instanceAccessible) {
            topInstanceType = Object.class;
        }

        String instanceTypeDesc = MPLType.getDescriptor(topInstanceType);
        String instanceTypeName = MPLType.getInternalName(topInstanceType);

        MethodVisitor mv;
        FieldVisitor fv;

        // Add instance field of the main handle instance type
        fv = cw.visitField(ACC_PUBLIC + ACC_FINAL, "instance", instanceTypeDesc, null, null);
        fv.visitEnd();

        // Add constructor accepting the main handle instance type
        // By not doing a checked cast in here, we could potentially optimize the checked cast away
        // Right now though, there will always be a checked cast inside the constructor calling code
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + instanceTypeDesc + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(this.handleType), "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        // Add a getRaw() function overload, returning the instance field
        mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "getRaw", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Walk all Handle superclass types that this Handle class type is
        // For all types we must implement the abstract methods for the fields/methods represented
        Class<?> currentHandleType = this.handleType;
        Template.Class<?> currentTemplateClass = this.templateClass;
        do {
            // Internal name of the handle type, when writing access to T
            String currentHandleName = MPLType.getInternalName(currentHandleType);

            // Note: superclass because it is actually $impl (generated)
            Class<?> templateClassType = currentTemplateClass.getSelfClassType();
            String templateClassDesc = MPLType.getDescriptor(templateClassType);
            String templateClassName = MPLType.getInternalName(templateClassType);
            Class<?> instanceType = currentTemplateClass.getType();
            ClassDeclaration classDec = currentTemplateClass.getClassDeclaration();
            if (classDec == null) {
                throw new IllegalStateException("Template Handle Class " + currentTemplateClass + " has no Class Declaration! Not initialized?");
            }

            // Implement the getter and setter methods for all non-static fields
            for (FieldDeclaration fieldDec : classDec.fields) {
                if (fieldDec.modifiers.isStatic() || fieldDec.modifiers.isUnknown() || fieldDec.modifiers.isOptional() || fieldDec.isEnum) {
                    continue;
                }

                Class<?> fieldType = fieldDec.type.exposed().type;
                String fieldTypeDesc = MPLType.getDescriptor(fieldType);
                String fieldName = fieldDec.name.real();

                // Find out what Template accessor names to use
                // For example, this returns class type Template.Field.Integer for 'int' fields
                Class<?> templateElement;
                String templateElementName;
                String templateElementDesc;
                try {
                    templateElement = templateClassType.getField(fieldName).getType();
                    templateElementName = MPLType.getInternalName(templateElement);
                    templateElementDesc = MPLType.getDescriptor(templateElement);
                } catch (Throwable t) {
                    throw MountiplexUtil.uncheckedRethrow(t);
                }

                // Find out how the get/set function of choice is called
                // For Object types this is simply get/set
                // But for primitives there are overloads for getInteger/getFloat/etc.
                String accessorName = "";
                String accessorType = "Ljava/lang/Object;";
                for (Class<?> boxedType : BoxedType.getBoxedTypes()) {
                    if (templateElementName.endsWith(boxedType.getSimpleName())) {
                        accessorName = boxedType.getSimpleName();
                        accessorType = fieldTypeDesc;
                        break;
                    }
                }

                // If the variable is public, we can get it without having to use the accessor in Template.Class
                // This allows for a slight performance improvement (and avoids unneeded initialization of FastField)
                boolean isPublicField = instanceAccessible &&
                        (fieldDec.type.cast == null && 
                        fieldDec.field != null && 
                        Modifier.isPublic(fieldDec.field.getModifiers()));

                // If the field is public and not final, we can optimize the setter too
                boolean isPublicNonfinalField = isPublicField &&
                        !Modifier.isFinal(fieldDec.field.getModifiers());

                // Generate getter
                String getterName = TemplateGenerator.getGetterName(fieldDec);
                mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, getterName, "()" + fieldTypeDesc, null, null);
                mv.visitCode();
                if (isPublicField) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                    mv.visitFieldInsn(GETFIELD, instanceTypeName, fieldDec.getAccessedName(), fieldTypeDesc);
                } else {
                    mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                    mv.visitFieldInsn(GETFIELD, templateClassName, fieldName, templateElementDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                    mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "get" + accessorName, "(Ljava/lang/Object;)" + accessorType, false);
                    if (accessorName.isEmpty() && !fieldType.equals(Object.class)) {
                        mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(fieldType));
                    }
                }
                mv.visitInsn(MPLType.getOpcode(fieldType, IRETURN));
                mv.visitMaxs(2, 1);
                mv.visitEnd();

                // Generate setter
                if (!fieldDec.modifiers.isReadonly()) {
                    String setterName = TemplateGenerator.getSetterName(fieldDec);
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, setterName, "(" + fieldTypeDesc + ")V", null, null);
                    mv.visitCode();
                    if (isPublicNonfinalField) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                        mv.visitVarInsn(MPLType.getOpcode(fieldType, ILOAD), 1);
                        mv.visitFieldInsn(PUTFIELD, instanceTypeName, fieldDec.getAccessedName(), fieldTypeDesc);
                    } else {
                        mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                        mv.visitFieldInsn(GETFIELD, templateClassName, fieldName, templateElementDesc);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                        mv.visitVarInsn(MPLType.getOpcode(fieldType, ILOAD), 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "set" + accessorName, "(Ljava/lang/Object;" + accessorType + ")V", false);
                    }
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(4, 3);
                    mv.visitEnd();
                }
            }

            // Implement the invoke method for all non-static methods
            for (MethodDeclaration methodDec : classDec.methods) {
                if (methodDec.modifiers.isStatic() || methodDec.modifiers.isUnknown() || methodDec.modifiers.isOptional()) {
                    continue;
                }

                String methodName = methodDec.name.real();

                // Build the method descriptor
                boolean hasTypeConversion = !methodDec.returnType.canDownCast();
                Class<?> returnType = methodDec.returnType.exposed().type;
                Class<?>[] paramTypes = new Class<?>[methodDec.parameters.parameters.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    hasTypeConversion |= !methodDec.parameters.parameters[i].type.canUpCast();
                    paramTypes[i] = methodDec.parameters.parameters[i].type.exposed().type;
                }
                String methodDesc = MPLType.getMethodDescriptor(returnType, paramTypes);

                // Figure out what kind of accessor Object is used here (Converted or not)
                Template.TemplateElement<?> templateElement;
                String templateElementName;
                String templateElementDesc;
                try {
                    java.lang.reflect.Field templateField = templateClassType.getField(methodName);
                    templateElement = (Template.TemplateElement<?>) templateField.get(currentTemplateClass);
                    templateElementName = MPLType.getInternalName(templateField.getType());
                    templateElementDesc = MPLType.getDescriptor(templateField.getType());
                } catch (Throwable t) {
                    throw MountiplexUtil.uncheckedRethrow(t);
                }

                // Check if we can inline the function call directly, instead of invoking the template method
                // This is only possible when not performing conversion, and the method is public.
                boolean canInline = instanceAccessible &&
                        (methodDec.method != null) &&
                        Modifier.isPublic(methodDec.method.getModifiers()) &&
                        !hasTypeConversion;

                mv = cw.visitMethod(ACC_PUBLIC, methodName, methodDesc, null, null);
                mv.visitCode();
                if (canInline) {
                    // Call the method directly
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                    MPLType.visitVarILoad(mv, 1, paramTypes);
                    ExtendedClassWriter.visitInvoke(mv, instanceType, methodDec.method);
                    mv.visitInsn(MPLType.getOpcode(returnType, IRETURN));
                } else if (isGeneratedInvoker(templateElement)) {
                    // Can cast invoker to a runtime-generated interface and call that directly
                    // Note: these are only local methods, static methods aren't generated here
                    GeneratedInvoker<?> invoker = (GeneratedInvoker<?>) ((Template.AbstractMethod<?>) templateElement).invoker;
                    String interfaceType = MPLType.getInternalName(invoker.getInterface());

                    mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                    mv.visitFieldInsn(GETFIELD, templateClassName, methodName, templateElementDesc);
                    mv.visitFieldInsn(GETFIELD, templateElementName, "invoker", MPLType.getDescriptor(Invoker.class));
                    mv.visitTypeInsn(CHECKCAST, interfaceType);

                    // Load instance
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);

                    // Load parameters
                    MPLType.visitVarILoad(mv, 1, methodDec.parameters);

                    // Call the interface method
                    mv.visitMethodInsn(INVOKEINTERFACE, interfaceType, methodName, methodDec.getASMInvokeDescriptor(), true);

                    // Close the method with a valid return statement
                    mv.visitInsn(MPLType.getOpcode(returnType, IRETURN));
                } else {
                    // Call into the T.fieldname Template Method
                    mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                    mv.visitFieldInsn(GETFIELD, templateClassName, methodName, templateElementDesc);                   

                    // Is an abstract method, we can call the invoker field directly
                    boolean useInvokerField = (templateElement instanceof Template.AbstractMethod);
                    if (useInvokerField) {
                        mv.visitFieldInsn(GETFIELD, templateElementName, "invoker", MPLType.getDescriptor(Invoker.class));
                    }

                    // Call invoke or invokeVA on the static template method instance, or the invoker field if we can
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);

                    if (paramTypes.length <= 5) {
                        // invoke() overloads for 5 or less parameters
                        // Convert arguments into a descriptor
                        // Build a generic Object invoke descriptor at the same time
                        StringBuilder invokeDescBldr = new StringBuilder();
                        invokeDescBldr.append("(Ljava/lang/Object;");
                        int varIdx = 1;
                        for (int i = 0; i < paramTypes.length; i++) {
                            varIdx = MPLType.visitVarILoadAndBox(mv, varIdx, paramTypes[i]);
                            invokeDescBldr.append("Ljava/lang/Object;");
                        }
                        invokeDescBldr.append(")Ljava/lang/Object;");

                        // Call invoke(instance, argn) on either the invoker interface, or the template method virtual function
                        if (useInvokerField) {
                            mv.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Invoker.class), "invoke", invokeDescBldr.toString(), true);
                        } else {
                            mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "invoke", invokeDescBldr.toString(), false);
                        }
                    } else {
                        // invokeVA(...) for larger amounts of parameters
                        // Fill an array with the parameter values
                        ExtendedClassWriter.visitPushInt(mv, paramTypes.length);
                        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                        int varIdx = 1;
                        for (int i = 0; i < paramTypes.length; i++) {
                            mv.visitInsn(DUP);
                            ExtendedClassWriter.visitPushInt(mv, i);
                            varIdx = MPLType.visitVarILoadAndBox(mv, varIdx, paramTypes[i]);
                            mv.visitInsn(AASTORE);
                        }

                        // Call invokeVA(instance, args[]) on either the invoker interface, or the template method virtual function
                        if (useInvokerField) {
                            mv.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Invoker.class), "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", true);
                        } else {
                            mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                        }
                    }

                    // Close the method with a valid return statement
                    // Cast the value returned from invoke() to a primitive if required
                    if (returnType.equals(void.class)) {
                        mv.visitInsn(POP);
                        mv.visitInsn(RETURN);
                    } else if (returnType.equals(Object.class)) {
                        mv.visitInsn(ARETURN);
                    } else {
                        ExtendedClassWriter.visitUnboxVariable(mv, returnType);
                        mv.visitInsn(MPLType.getOpcode(returnType, IRETURN));
                    }
                }
                mv.visitMaxs(3, 2);
                mv.visitEnd();
            }

            // Next layer of handles, if we reach Handle, then there are no more superclasses
            currentHandleType = currentHandleType.getSuperclass();
            if (currentHandleType == Template.Handle.class) {
                break;
            }
            currentTemplateClass = getTemplateClass(currentHandleType);
        } while (true);

        this.handleImplType = cw.generate();

        try {
            Constructor<? extends H> constructor;
            try {
                constructor = this.handleImplType.getConstructor(topInstanceType);
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to find generated handle constructor of handle for " + MPLType.getName(topInstanceType), t);
            }

            this.handleConstructor.init(constructor);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Gets whether a given Method Declaration refers to a createHandle method,
     * which is normally generated. If this method exists in the class declaration,
     * then it overrides the default createHandle logic.
     * 
     * @param method
     * @return True if the method refers to a createHandle implementation
     */
    public static boolean isCreateHandleMethod(MethodDeclaration method) {
        return method.modifiers.isStatic() &&
               method.name.value().equals("createHandle") &&
               method.parameters.parameters.length == 1 &&
               method.parameters.parameters[0].type.isResolved() &&
               method.parameters.parameters[0].type.type.equals(Object.class) &&
               method.parameters.parameters[0].type.cast == null;
    }

    /**
     * Fallback Handle class when no declarations/generated methods exist.
     * Merely provides access to the raw instance.
     */
    static final class FallbackHandle extends Template.Handle {
        private final Object instance;

        public FallbackHandle(Object instance) {
            this.instance = instance;
        }

        @Override
        public Object getRaw() {
            return this.instance;
        }
    }
}
