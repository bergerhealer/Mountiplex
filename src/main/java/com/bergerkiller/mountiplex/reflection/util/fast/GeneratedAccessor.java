package com.bergerkiller.mountiplex.reflection.util.fast;

import static net.sf.cglib.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;

public class GeneratedAccessor<T> extends ReflectionAccessor<T> {

    protected GeneratedAccessor(Field field) {
        super(field);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> GeneratedAccessor<T> create(java.lang.reflect.Field field) {
        // Optimize field access be generating the code to do it (its a public member)
        String selfName = Type.getInternalName(GeneratedAccessor.class);
        ExtendedClassWriter<GeneratedAccessor<T>> cw = new ExtendedClassWriter<GeneratedAccessor<T>>(ClassWriter.COMPUTE_MAXS, (Class) GeneratedAccessor.class);
        MethodVisitor mv;

        // Constructor that calls the super constructor with the Field
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Field;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, selfName, "<init>", "(" + Type.getDescriptor(java.lang.reflect.Field.class) + ")V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        // Getter/setter makeup
        int mod = field.getModifiers();
        String className = Type.getInternalName(field.getDeclaringClass());
        Type fieldType = Type.getType(field.getType());
        String fieldTypeName = fieldType.getDescriptor();
        String fieldName = field.getName();
        String accessorName = null;
        if (field.getType().isPrimitive()) {
            Class<?> boxed = BoxedType.getBoxedType(field.getType());
            if (boxed != null) {
                accessorName = boxed.getSimpleName();
            }
        }

        if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
            if (accessorName == null) {
                String fieldTypeInternalName = Type.getInternalName(field.getType());

                // Get the Object field
                mv = cw.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitFieldInsn(GETFIELD, className, fieldName, fieldTypeName);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // Set the Object field (if not final)
                if (!Modifier.isFinal(mod)) {
                    mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, className);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitTypeInsn(CHECKCAST, fieldTypeInternalName);
                    mv.visitFieldInsn(PUTFIELD, className, fieldName, fieldTypeName);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            } else {
                // Get the primitive field
                mv = cw.visitMethod(ACC_PUBLIC, "get" + accessorName, "(Ljava/lang/Object;)" + fieldTypeName, null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitFieldInsn(GETFIELD, className, fieldName, fieldTypeName);
                mv.visitInsn(fieldType.getOpcode(IRETURN));
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // Set the primitive field (if not final)
                if (!Modifier.isFinal(mod)) {
                    mv = cw.visitMethod(ACC_PUBLIC, "set" + accessorName, "(Ljava/lang/Object;" + fieldTypeName + ")V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, className);
                    mv.visitVarInsn(fieldType.getOpcode(ILOAD), 2);
                    mv.visitFieldInsn(PUTFIELD, className, fieldName, fieldTypeName);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            }
        }

        // Generate the copy method
        if (Modifier.isStatic(mod)) {
            // Throw exception for static fields
            mv = cw.visitMethod(ACC_PUBLIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
            mv.visitInsn(DUP);
            mv.visitLdcInsn("Static fields can not be copied");
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V");
            mv.visitInsn(ATHROW);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        } else {
            // Copy field from one member to another
            if (Modifier.isPublic(mod) && !Modifier.isFinal(mod)) {
                // Public non-final fields can be copied with simple assignment
                mv = cw.visitMethod(ACC_PUBLIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitFieldInsn(GETFIELD, className, fieldName, fieldTypeName);
                mv.visitFieldInsn(PUTFIELD, className, fieldName, fieldTypeName);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            } else if (Modifier.isPublic(mod) && !Modifier.isFinal(mod)) {
                // Final fields require use of reflection to set, but can be get normally
                String setMethod = (accessorName == null) ? "set" : ("set" + accessorName);
                mv = cw.visitMethod(ACC_PUBLIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitFieldInsn(GETFIELD, className, fieldName, fieldTypeName);
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, setMethod, "(Ljava/lang/Object;" + fieldTypeName + ")V");
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            } else if (accessorName != null) {
                // Reflection is used, but we only have to override when non-Object field types are copied
                mv = cw.visitMethod(ACC_PUBLIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, "get" + accessorName, "(Ljava/lang/Object;)" + fieldTypeName);
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, "set" + accessorName, "(Ljava/lang/Object;" + fieldTypeName + ")V");
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }

        return cw.generateInstance(new Class<?>[] {java.lang.reflect.Field.class}, new Object[] { field });
    }
}
