package com.bergerkiller.mountiplex.reflection.declarations;

import static net.sf.cglib.asm.$Opcodes.*;

import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.Template.Handle;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.FastConstructor;

import net.sf.cglib.asm.$ClassWriter;
import net.sf.cglib.asm.$FieldVisitor;
import net.sf.cglib.asm.$MethodVisitor;
import net.sf.cglib.asm.$Type;

/**
 * Reads the abstract class information of a Template Handle type and generates an appropriate
 * implementation of the abstract methods.
 */
public class TemplateHandleBuilder<H> {
    private final Class<H> handleType;
    private Class<? extends H> handleImplType;
    private final FastConstructor<H> handleConstructor = new FastConstructor<H>();

    public TemplateHandleBuilder(Class<H> handleType) {
        this.handleType = handleType;
        this.handleConstructor.initUnavailable("new " + handleType.getName() + "()");
    }

    public Class<? extends H> getImplType() {
        return this.handleImplType;
    }

    public H create(Object instance) {
        return handleConstructor.newInstance(instance);
    }

    private Template.Class<?> getTemplateClass(Class<?> handleClass) {
        try {
            return (Template.Class<?>) handleClass.getField("T").get(null);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    public void build() {
        // Set up the class writer for the implementation of the handle type
        ExtendedClassWriter<H> cw = new ExtendedClassWriter<H>($ClassWriter.COMPUTE_MAXS, this.handleType, "$impl");
        Class<?> topInstanceType = getTemplateClass(this.handleType).getType();
        if (topInstanceType == null) {
            throw new IllegalStateException("Handle internal type of " + this.handleType + " is null");
        }

        // Non-public classes can not be stored as a type in another class
        // In those cases, we can only access them through reflection, and in
        // the generated class we store an 'Object' field for the instance.
        boolean instanceAccessible = Resolver.getMeta(topInstanceType).isPublic;
        if (!instanceAccessible) {
            topInstanceType = Object.class;
        }

        String instanceTypeDesc = $Type.getDescriptor(topInstanceType);
        String instanceTypeName = $Type.getInternalName(topInstanceType);
        $MethodVisitor mv;
        $FieldVisitor fv;

        // Add instance field of the main handle instance type
        fv = cw.visitField(ACC_PUBLIC + ACC_FINAL, "instance", instanceTypeDesc, null, null);
        fv.visitEnd();

        // Add constructor accepting the main handle instance type
        // By not doing a checked cast in here, we could potentially optimize the checked cast away
        // Right now though, there will always be a checked cast inside the constructor calling code
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + instanceTypeDesc + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, $Type.getInternalName(this.handleType), "<init>", "()V", false);
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
        do {
            // Find the value of 'T' for the current Handle type
            Template.Class<?> templateClass = getTemplateClass(currentHandleType);

            // Internal name of the handle type, when writing access to T
            String currentHandleName = $Type.getInternalName(currentHandleType);

            Class<?> templateClassType = templateClass.getClass();
            String templateClassDesc = $Type.getDescriptor(templateClassType);
            String templateClassName = $Type.getInternalName(templateClassType);
            Class<?> instanceType = templateClass.getType();
            ClassDeclaration classDec = templateClass.getClassDeclaration();
            if (classDec == null) {
                throw new IllegalStateException("Template Handle Class " + templateClass + " has no Class Declaration! Not initialized?");
            }

            // Implement the getter and setter methods for all non-static fields
            for (FieldDeclaration fieldDec : classDec.fields) {
                if (fieldDec.modifiers.isStatic() || fieldDec.modifiers.isUnknown() || fieldDec.modifiers.isOptional() || fieldDec.isEnum) {
                    continue;
                }

                Class<?> fieldType = TemplateGenerator.getExposedType(fieldDec.type).type;
                String fieldTypeDesc = $Type.getDescriptor(fieldType);
                String fieldName = fieldDec.name.real();

                // Find out what Template accessor names to use
                // For example, this returns class type Template.Field.Integer for 'int' fields
                Class<?> templateElement;
                String templateElementName;
                String templateElementDesc;
                try {
                    templateElement = templateClassType.getField(fieldName).getType();
                    templateElementName = $Type.getInternalName(templateElement);
                    templateElementDesc = $Type.getDescriptor(templateElement);
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
                    mv.visitFieldInsn(GETFIELD, instanceTypeName, fieldDec.name.value(), fieldTypeDesc);
                } else {
                    mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                    mv.visitFieldInsn(GETFIELD, templateClassName, fieldName, templateElementDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                    mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "get" + accessorName, "(Ljava/lang/Object;)" + accessorType, false);
                    if (accessorName.isEmpty() && !fieldType.equals(Object.class)) {
                        mv.visitTypeInsn(CHECKCAST, $Type.getInternalName(fieldType));
                    }
                }
                mv.visitInsn($Type.getType(fieldType).getOpcode(IRETURN));
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
                        mv.visitVarInsn($Type.getType(fieldType).getOpcode(ILOAD), 1);
                        mv.visitFieldInsn(PUTFIELD, instanceTypeName, fieldDec.name.value(), fieldTypeDesc);
                    } else {
                        mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                        mv.visitFieldInsn(GETFIELD, templateClassName, fieldName, templateElementDesc);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);
                        mv.visitVarInsn($Type.getType(fieldType).getOpcode(ILOAD), 1);
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
                boolean hasTypeConversion = (methodDec.returnType.cast != null);
                Class<?> returnTypeClass = TemplateGenerator.getExposedType(methodDec.returnType).type;
                $Type returnType = $Type.getType(returnTypeClass);
                Class<?>[] paramTypeClasses = new Class<?>[methodDec.parameters.parameters.length];
                $Type[] paramTypes = new $Type[paramTypeClasses.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    hasTypeConversion |= (methodDec.parameters.parameters[i].type.cast != null);
                    paramTypeClasses[i] = TemplateGenerator.getExposedType(methodDec.parameters.parameters[i].type).type;
                    paramTypes[i] = $Type.getType(paramTypeClasses[i]);
                }
                String methodDesc = $Type.getMethodDescriptor(returnType, paramTypes);

                // Figure out what kind of accessor Object is used here (Converted or not)
                Class<?> templateElement;
                String templateElementName;
                String templateElementDesc;
                try {
                    templateElement = templateClassType.getField(methodName).getType();
                    templateElementName = $Type.getInternalName(templateElement);
                    templateElementDesc = $Type.getDescriptor(templateElement);
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
                    int register = 1;
                    for (int i = 0; i < paramTypes.length; i++) {
                        mv.visitVarInsn(paramTypes[i].getOpcode(ILOAD), register);
                        register += paramTypes[i].getSize();
                    }
                    ExtendedClassWriter.visitInvoke(mv, instanceType, methodDec.method);
                    mv.visitInsn(returnType.getOpcode(IRETURN));
                } else {
                    // Call invoke or invokeVA on the static template method instance
                    mv.visitFieldInsn(GETSTATIC, currentHandleName, "T", templateClassDesc);
                    mv.visitFieldInsn(GETFIELD, templateClassName, methodName, templateElementDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", instanceTypeDesc);

                    if (paramTypes.length <= 5) {
                        // invoke() overloads for 5 or less parameters
                        // Convert arguments into a descriptor
                        // Build a generic Object invoke descriptor at the same time
                        StringBuilder invokeDescBldr = new StringBuilder();
                        invokeDescBldr.append("(Ljava/lang/Object;");
                        int register = 1;
                        for (int i = 0; i < paramTypes.length; i++) {
                            mv.visitVarInsn(paramTypes[i].getOpcode(ILOAD), register);
                            register += paramTypes[i].getSize();

                            ExtendedClassWriter.visitBoxVariable(mv, paramTypeClasses[i]);
                            invokeDescBldr.append("Ljava/lang/Object;");
                        }
                        invokeDescBldr.append(")Ljava/lang/Object;");

                        mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "invoke", invokeDescBldr.toString(), false);
                    } else {
                        // invokeVA(...) for larger amounts of parameters
                        // Fill an array with the parameter values
                        mv.visitIntInsn(BIPUSH, paramTypes.length);
                        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                        int register = 1;
                        for (int i = 0; i < paramTypes.length; i++) {
                            mv.visitInsn(DUP);
                            mv.visitIntInsn(BIPUSH, i);

                            mv.visitVarInsn(paramTypes[i].getOpcode(ILOAD), register);
                            register += paramTypes[i].getSize();

                            ExtendedClassWriter.visitBoxVariable(mv, paramTypeClasses[i]);
                            mv.visitInsn(AASTORE);
                        }

                        mv.visitMethodInsn(INVOKEVIRTUAL, templateElementName, "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                    }

                    // Close the method with a valid return statement
                    // Cast the value returned from invoke() to a primitive if required
                    if (returnTypeClass.equals(void.class)) {
                        mv.visitInsn(POP);
                        mv.visitInsn(RETURN);
                    } else if (returnTypeClass.equals(Object.class)) {
                        mv.visitInsn(ARETURN);
                    } else {
                        ExtendedClassWriter.visitUnboxVariable(mv, returnTypeClass);
                        mv.visitInsn(returnType.getOpcode(IRETURN));
                    }
                }
                mv.visitMaxs(3, 2);
                mv.visitEnd();
            }
            currentHandleType = currentHandleType.getSuperclass();
        } while (currentHandleType != Handle.class);

        this.handleImplType = cw.generate();

        try {
            this.handleConstructor.init(this.handleImplType.getConstructor(topInstanceType));
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
