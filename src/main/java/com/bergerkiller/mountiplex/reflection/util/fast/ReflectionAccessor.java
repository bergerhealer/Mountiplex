package com.bergerkiller.mountiplex.reflection.util.fast;

import static net.sf.cglib.asm.Opcodes.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

public class ReflectionAccessor<T> implements Reader<T>, Writer<T> {
    private final java.lang.reflect.Field f;

    protected ReflectionAccessor(java.lang.reflect.Field field) {
        this.f = field;
    }

    private RuntimeException checkInstance(Object instance) {
        // Verify the instance is of the correct type
        if (Modifier.isStatic(f.getModifiers())) {
            if (instance != null) {
                return new IllegalArgumentException("Instance should be null for static fields, but was " +
                        instance.getClass().getName() + " instead");
            }
        } else {
            if (instance == null) {
                return new IllegalArgumentException("Instance can not be null for member fields declared in " +
                        f.getDeclaringClass().getName());
            }
            if (!f.getDeclaringClass().isAssignableFrom(instance.getClass())) {
                return new IllegalArgumentException("Instance of type " + instance.getClass().getName() +
                        " does not contain the field declared in " + f.getDeclaringClass().getName());
            }
        }
        return null;
    }

    private RuntimeException f(Object instance, Throwable t) {
        // Check instance
        RuntimeException iex = checkInstance(instance);
        if (iex != null) {
            return iex;
        }

        // Don't know, then
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException("Failed to get field", t);
    }

    private RuntimeException f(Object instance, Object value, Throwable t) {
        // Check instance
        RuntimeException iex = checkInstance(instance);
        if (iex != null) {
            return iex;
        }

        // Verify that the value parameter can be assigned to the field
        java.lang.Class<?> fieldType = f.getType();
        if (fieldType.isPrimitive()) {
            if (value == null) {
                return new IllegalArgumentException("Field primitive type " + fieldType.getName() + " can not be assigned null");
            }

            java.lang.Class<?> valueType = BoxedType.getUnboxedType(value.getClass());
            if (valueType == null || !fieldType.isAssignableFrom(valueType)) {
                return new IllegalArgumentException("value type " + value.getClass().getName() +
                        " can not be assigned to primitive field type " + fieldType.getName());
            }
        } else {
            if (value != null && !fieldType.isAssignableFrom(value.getClass())) {
                return new IllegalArgumentException("value type " + value.getClass().getName() +
                        " can not be assigned to field type " + fieldType.getName());
            }
        }

        // Don't know, then
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException("Failed to set field", t);
    }

    // Reader implementation
    @SuppressWarnings("unchecked")
    public T get(Object o){try{return (T) f.get(o);}catch(Throwable t){throw f(o,t);}}
    public double getDouble(Object o){try{return f.getDouble(o);}catch(Throwable t){throw f(o,t);}}
    public float getFloat(Object o){try{return f.getFloat(o);}catch(Throwable t){throw f(o,t);}}
    public byte getByte(Object o){try{return f.getByte(o);}catch(Throwable t){throw f(o,t);}}
    public short getShort(Object o){try{return f.getShort(o);}catch(Throwable t){throw f(o,t);}}
    public int getInteger(Object o){try{return f.getInt(o);}catch(Throwable t){throw f(o,t);}}
    public long getLong(Object o){try{return f.getLong(o);}catch(Throwable t){throw f(o,t);}}
    public char getCharacter(Object o){try{return f.getChar(o);}catch(Throwable t){throw f(o,t);}}
    public boolean getBoolean(Object o){try{return f.getBoolean(o);}catch(Throwable t){throw f(o,t);}}
    public Field getReadField(){return f;}
    public void checkCanRead(){}

    // Writer implementation
    public void set(Object o, T v) {try{f.set(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setDouble(Object o, double v) {try{f.setDouble(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setFloat(Object o, float v) {try{f.setFloat(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setByte(Object o, byte v) {try{f.setByte(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setShort(Object o, short v) {try{f.setShort(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setInteger(Object o, int v) {try{f.setInt(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setLong(Object o, long v) {try{f.setLong(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setCharacter(Object o, char v) {try{f.setChar(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public void setBoolean(Object o, boolean v) {try{f.setBoolean(o, v);}catch(Throwable t){throw f(o,v,t);}}
    public Field getWriteField(){return f;}
    public void checkCanWrite(){}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> ReflectionAccessor<T> create(java.lang.reflect.Field field) {
        int mod = field.getModifiers();
        if (Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
            // Optimize field access be generating the code to do it (its a public member)
            ExtendedClassWriter<ReflectionAccessor<T>> cw = new ExtendedClassWriter<ReflectionAccessor<T>>(ClassWriter.COMPUTE_MAXS, (Class) ReflectionAccessor.class);
            MethodVisitor mv;

            // Constructor that calls the super constructor with the Field
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Field;)V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(ReflectionAccessor.class), "<init>", "(" + Type.getDescriptor(java.lang.reflect.Field.class) + ")V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();

            // Getter/setter makeup
            String className = Type.getInternalName(field.getDeclaringClass());
            Type fieldType = Type.getType(field.getType());
            String fieldTypeName = fieldType.getDescriptor();
            String fieldName = field.getName();
            String accessorName = null;
            if (field.getType().isPrimitive()) {
                Class<?> unboxed = BoxedType.getBoxedType(field.getType());
                if (unboxed != null) {
                    accessorName = unboxed.getSimpleName();
                }
            }

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

            return cw.generateInstance(new Class<?>[] {java.lang.reflect.Field.class}, new Object[] { field });
        }
        return new ReflectionAccessor<T>(field);
    }
}
