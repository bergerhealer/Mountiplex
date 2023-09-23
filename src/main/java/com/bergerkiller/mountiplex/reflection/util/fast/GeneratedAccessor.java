package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class GeneratedAccessor<T> extends ReflectionAccessor<T> {

    protected GeneratedAccessor(Field field) {
        super(field);
    }

    public static <T> GeneratedAccessor<T> create(java.lang.reflect.Field field) {
        String selfName = MPLType.getInternalName(GeneratedAccessor.class);
        int mod = field.getModifiers();
        boolean isStaticFinal = Modifier.isFinal(mod) && Modifier.isStatic(mod);

        // Optimize field access by generating the code to do it (its a public member)
        Class<?> baseClass = isStaticFinal
                ? GeneratedStaticFinalAccessor.class : GeneratedAccessor.class;
        ExtendedClassWriter<GeneratedAccessor<T>> cw = ExtendedClassWriter.builder(baseClass)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL).build();

        MethodVisitor mv;

        // Constructor that calls the super constructor with the Field
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Field;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(baseClass), "<init>", "(" + MPLType.getDescriptor(java.lang.reflect.Field.class) + ")V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        // Getter/setter makeup
        String className = MPLType.getInternalName(field.getDeclaringClass());
        Type fieldType = MPLType.getType(field.getType());
        String fieldTypeName = fieldType.getDescriptor();
        String fieldName = MPLType.getName(field);
        String accessorName = null;
        if (field.getType().isPrimitive()) {
            Class<?> boxed = BoxedType.getBoxedType(field.getType());
            if (boxed != null) {
                accessorName = boxed.getSimpleName();
            }
        }

        if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
            if (accessorName == null) {
                String fieldTypeInternalName = MPLType.getInternalName(field.getType());

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

        // For static final fields, a custom setter needs to be added which uses Unsafe to modify the field
        // We only need to do this for non-Object field types, rest is handled by the generic set()
        if (isStaticFinal && accessorName != null) {
            // The put<name> name of Unsafe matching the accessor name
            final String unsafePutName;
            if (field.getType() == int.class) {
                unsafePutName = "putInt";
            } else if (field.getType() == char.class) {
                unsafePutName = "putChar";
            } else {
                unsafePutName = "put" + accessorName;
            }

            // Override set<accessorName> and put the appropriate 'put' code in there
            {
                mv = cw.visitMethod(ACC_PUBLIC, "set" + accessorName, "(Ljava/lang/Object;" + fieldTypeName + ")V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "class_init", "Z");
                org.objectweb.asm.Label label0 = new org.objectweb.asm.Label();
                mv.visitJumpInsn(IFNE, label0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, cw.getInternalName(), "initDeclaringClass", "()V", false);
                mv.visitLabel(label0);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "unsafe", "Lsun/misc/Unsafe;");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "base", "Ljava/lang/Object;");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "offset", "J");
                mv.visitVarInsn(MPLType.getOpcode(field.getType(), ILOAD), 2);
                mv.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", unsafePutName, "(Ljava/lang/Object;J" + fieldTypeName + ")V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // The Boxed Type of the value we accept inside set()
            String boxedName = MPLType.getInternalName(BoxedType.getBoxedType(field.getType()));

            // Override set, check the value type matches the primitive type, and call the
            // set<accessorName> method instead
            {
                mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(INSTANCEOF, boxedName);
                org.objectweb.asm.Label label0 = new org.objectweb.asm.Label();
                mv.visitJumpInsn(IFEQ, label0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, boxedName);
                mv.visitMethodInsn(INVOKEVIRTUAL, boxedName, field.getType().getSimpleName() + "Value", "()" + fieldTypeName, false);
                mv.visitMethodInsn(INVOKEVIRTUAL, cw.getInternalName(), "set" + accessorName, "(Ljava/lang/Object;" + fieldTypeName + ")V", false);
                org.objectweb.asm.Label label1 = new org.objectweb.asm.Label();
                mv.visitJumpInsn(GOTO, label1);
                mv.visitLabel(label0);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEVIRTUAL, cw.getInternalName(), "checkValueType", "(Ljava/lang/Object;)V", false);
                mv.visitLabel(label1);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
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
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
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
            } else if (Modifier.isPublic(mod) && Modifier.isFinal(mod)) {
                // Final fields require use of reflection to set, but can be get normally
                String setMethod = (accessorName == null) ? "set" : ("set" + accessorName);
                mv = cw.visitMethod(ACC_PUBLIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, className);
                mv.visitFieldInsn(GETFIELD, className, fieldName, fieldTypeName);
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, setMethod, "(Ljava/lang/Object;" + fieldTypeName + ")V", false);
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
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, "get" + accessorName, "(Ljava/lang/Object;)" + fieldTypeName, false);
                mv.visitMethodInsn(INVOKEVIRTUAL, selfName, "set" + accessorName, "(Ljava/lang/Object;" + fieldTypeName + ")V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }

        return cw.generateInstance(new Class<?>[] {java.lang.reflect.Field.class}, new Object[] { field });
    }

    /**
     * Base class for accessors that try to set a static final field.
     * It makes use of Unsafe to do so, which is really the only way to bypass
     * the security manager to allow this.
     * 
     * @param <T>
     */
    public static class GeneratedStaticFinalAccessor<T> extends GeneratedAccessor<T> {
        protected final Class<?> type;
        protected final sun.misc.Unsafe unsafe;
        protected final Object base;
        protected final long offset;
        protected boolean class_init;

        protected GeneratedStaticFinalAccessor(Field field) {
            super(field);

            sun.misc.Unsafe unsafe;
            try {
                Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
                Field f = unsafeType.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe = (sun.misc.Unsafe) f.get(null);
            } catch (Throwable t) {
                throw new UnsupportedOperationException("Failed to access sun.misc.Unsafe", t);
            }

            this.type = field.getType();
            this.unsafe = unsafe;
            this.base = unsafe.staticFieldBase(field);
            this.offset = unsafe.staticFieldOffset(field);
            this.class_init = false;
        }

        protected synchronized final void initDeclaringClass() {
            if (!class_init) {
                try {
                    Class.forName(this.getWriteField().getDeclaringClass().getName());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Field declaring class failed to initialize", e);
                }
                class_init = true;
            }
        }

        // Just to reduce the amount of generated code: value type error handler
        protected void checkValueType(Object value) {
            if (this.type.isPrimitive()) {
                if (value == null) {
                    throw new IllegalArgumentException("Field of primitive type " + this.type.getName()
                            + " cannot be assigned null");
                }
                Class<?> primValueType = BoxedType.getUnboxedType(value.getClass());
                if (primValueType != null && primValueType != this.type) {
                    throw new IllegalArgumentException("Field of primitive type " + this.type.getName()
                            + " cannot be assigned a value of type " + primValueType);
                }
                throw new IllegalArgumentException("Field of primitive type " + this.type.getName()
                        + " cannot be assigned a value of type " + value.getClass().getName());
            } else if (value != null && !this.type.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Field of type " + this.type.getName()
                        + " cannot be assigned a value of type " + value.getClass().getName());
            }
        }

        // By default we want all methods to throw, since we cannot rely on Reflection Field to work properly
        // It would probably complain about final, rather than that the type is wrong.
        public void setDouble(Object o, double v) { checkValueType(v); }
        public void setFloat(Object o, float v) { checkValueType(v); }
        public void setByte(Object o, byte v) { checkValueType(v); }
        public void setShort(Object o, short v) { checkValueType(v); }
        public void setInteger(Object o, int v) { checkValueType(v); }
        public void setLong(Object o, long v) { checkValueType(v); }
        public void setCharacter(Object o, char v) { checkValueType(v); }
        public void setBoolean(Object o, boolean v) { checkValueType(v); }

        // Default setter, used when the field type is Object
        @Override
        public void set(Object o, Object value) {
            if (!class_init) {
                initDeclaringClass();
            }
            if (value == null || this.type.isInstance(value)) {
                unsafe.putObject(base, offset, value);
            } else {
                this.checkValueType(value);
            }
        }

        /**
         * Assumes a field value is not initialized by the class constructor, and updates the field
         * value instantly without trying to initialize (clinit) the class itself.
         *
         * @param field
         * @param value
         */
        public static void setUninitializedField(Field field, Object value) {
            GeneratedStaticFinalAccessor<Object> accessor = new GeneratedStaticFinalAccessor<Object>(field);
            if (value == null || accessor.type.isInstance(value)) {
                accessor.unsafe.putObject(accessor.base, accessor.offset, value);
            } else {
                accessor.checkValueType(value);
            }
        }
    }
}
