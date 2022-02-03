package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Copies all the member fields of a Class from one instance to another.
 * The fields of superclasses are also copied.
 */
public abstract class ClassFieldCopier<T> {
    private static final Map<Class<?>, ClassFieldCopier<?>> cache = new HashMap<>();
    private static final ClassFieldCopier<?> NOOP_COPIER = new ClassFieldCopier<Object>() {
        @Override
        protected void tryCopy(Object from, Object to) {
        }

        @Override
        protected Stream<ClassFieldCopier<?>> all() {
            return Stream.empty();
        }
    };

    /**
     * Copies all the fields declared in the old instance to the new instance
     *
     * @param from Object whose fields to read
     * @param to Object whose fields to assign to
     */
    public final void copy(T from, T to) {
        try {
            tryCopy(from, to);
        } catch (Throwable t) {
            if (from == null) {
                throw new IllegalArgumentException("Object to copy fields from is null");
            } else if (to == null) {
                throw new IllegalArgumentException("Object to copy fields to is null");
            } else {
                throw new UnsupportedOperationException("Failed to copy fields of " +
                        from.getClass() + " to " + to.getClass(), t);
            }
        }
    }

    protected abstract void tryCopy(T from, T to) throws Throwable;

    protected Stream<ClassFieldCopier<?>> all() {
        return MountiplexUtil.toStream(this);
    }

    protected final void copyAllWithReflection(ReflectionCopier[] copiers, Object from, Object to) throws Throwable {
        int idx = copiers.length;
        while (--idx >= 0) {
            copiers[idx].copy(from, to);
        }
    }

    /**
     * Gets or creates the copier for a particular Class type
     *
     * @param <T> Class type
     * @param type Class type
     * @return Copier for copying the fields of this Class type
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> ClassFieldCopier<T> of(Class<T> type) {
        ClassFieldCopier<?> copier = cache.get(type); // Can't use computeIfAbsent due to recursion
        if (copier == null) {
            copier = generateOneAndCombine(type);
            cache.put(type, copier);
        }
        return (ClassFieldCopier<T>) copier;
    }

    @SuppressWarnings("unchecked")
    private static ClassFieldCopier<?> generateOneAndCombine(Class<?> type) {
        ClassFieldCopier<?> copier = generateOne(type);
        ClassFieldCopier<?> superCopier = NOOP_COPIER;
        {
            Class<?> superType = type.getSuperclass();
            if (superType != null && superType != Object.class) {
                superCopier = of(superType);
            }
        }
        if (superCopier == NOOP_COPIER) {
            return copier;
        } else if (copier == NOOP_COPIER) {
            return superCopier;
        } else {
            return new MultiCopier(Stream.concat(superCopier.all(), copier.all())
                    .toArray(ClassFieldCopier[]::new));

        }
    }

    private static final class MultiCopier extends ClassFieldCopier<Object> {
        private final ClassFieldCopier<Object>[] copiers;

        public MultiCopier(ClassFieldCopier<Object>[] copiers) {
            this.copiers = copiers;
        }

        @Override
        protected void tryCopy(Object from, Object to) {
            for (ClassFieldCopier<Object> copier : copiers) {
                copier.copy(from, to);
            }
        }

        @Override
        protected Stream<ClassFieldCopier<?>> all() {
            return Stream.of(copiers);
        }
    }

    /**
     * Copies the fields using reflection exclusively. Used when the type is not
     * accessible, or there are private fields.
     */
    private static final class MultiFieldReflectionCopier extends ClassFieldCopier<Object> {
        private final ReflectionCopier[] copiers;

        public MultiFieldReflectionCopier(List<ReflectionCopier> copiers) {
            this.copiers = copiers.toArray(new ReflectionCopier[copiers.size()]);
        }

        @Override
        protected void tryCopy(Object from, Object to) throws Throwable {
            copyAllWithReflection(copiers, from, to);
        }
    }

    /**
     * Handles the copying of a field from one instance to another, using reflection exclusively.
     */
    public static abstract class ReflectionCopier {
        private static Map<Class<?>, Function<Field, ReflectionCopier>> primitiveCopierLookup = new IdentityHashMap<>();
        private static Map<Class<?>, ReflectionSetterMethod> reflectionSetterMethods = new IdentityHashMap<>();
        private static final ReflectionSetterMethod defaultSetterMethod = new ReflectionSetterMethod("set", Object.class);

        private static void register(Class<?> type, String setterName, Function<Field, ReflectionCopier> copierFunc) {
            primitiveCopierLookup.put(type, copierFunc);
            reflectionSetterMethods.put(type, new ReflectionSetterMethod(setterName, type));
        }

        static {
            register(boolean.class, "setBoolean", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setBoolean(to, f.getBoolean(from)); }
            });
            register(byte.class, "setByte", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setByte(to, f.getByte(from)); }
            });
            register(char.class, "setChar", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setChar(to, f.getChar(from)); }
            });
            register(short.class, "setShort", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setShort(to, f.getShort(from)); }
            });
            register(int.class, "setInt", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setInt(to, f.getInt(from)); }
            });
            register(long.class, "setLong", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setLong(to, f.getLong(from)); }
            });
            register(float.class, "setFloat", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setFloat(to, f.getFloat(from)); }
            });
            register(double.class, "setDouble", field -> new ReflectionCopier(field) {
                @Override
                protected void copyBase(Field f, Object from, Object to) throws Throwable { f.setDouble(to, f.getDouble(from)); }
            });
        }

        public static ReflectionCopier of(Field field) {
            Class<?> type = field.getType();
            if (type.isPrimitive()) {
                return primitiveCopierLookup.get(type).apply(field);
            } else {
                return new ReflectionCopier(field) {
                    @Override
                    protected void copyBase(Field f, Object from, Object to) throws Throwable { f.set(to, f.get(from)); }
                };
            }
        }

        protected static void callSetter(MethodVisitor mv, Class<?> type) {
            ReflectionSetterMethod method = reflectionSetterMethods.getOrDefault(type, defaultSetterMethod);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", method.name, method.descriptor, false);
        }

        private final Field field;

        public ReflectionCopier(Field field) {
            this.field = field;
        }

        public final void copy(Object from, Object to) throws Throwable {
            copyBase(this.field, from, to);
        }

        protected abstract void copyBase(Field f, Object from, Object to) throws Throwable;

        private static class ReflectionSetterMethod {
            public final String name;
            public final String descriptor;

            public ReflectionSetterMethod(String name, Class<?> type) {
                this.name = name;
                this.descriptor = "(Ljava/lang/Object;" + MPLType.getDescriptor(type) + ")V";
            }
        }
    }

    /**
     * Generates a copier for the fields declared in just one class
     *
     * @return copier
     */
    private static ClassFieldCopier<?> generateOne(Class<?> type) {
        Field[] fields = type.getDeclaredFields();

        // Before doing anything, analyze the fields we are going to be copying
        // If there are no fields, return a NOOP without generating anything
        // If there is assigning to a public non-final field, to must be cast
        // If there is copying from a public field, from must be cast
        // If type isn't public, assume all fields are private
        ArrayList<ReflectionCopier> copiers = new ArrayList<>(fields.length);
        ArrayList<Field> publicFields = new ArrayList<>(fields.length);
        if (Resolver.isPublic(type)) {
            for (Field field : fields) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers)) {
                    if (Modifier.isPublic(modifiers)) {
                        publicFields.add(field);
                        if (Modifier.isFinal(modifiers)) {
                            field.setAccessible(true);
                        }
                    } else {
                        field.setAccessible(true);
                        copiers.add(ReflectionCopier.of(field));
                        continue;
                    }
                }
            }
        } else {
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    copiers.add(ReflectionCopier.of(field));
                }
            }
        }

        // When there are no public fields, we don't have to generate any sort of class
        if (publicFields.isEmpty()) {
            return copiers.isEmpty() ? NOOP_COPIER : new MultiFieldReflectionCopier(copiers);
        }

        // Generate a new class in which we do all of the logic that needs doing
        final ExtendedClassWriter<ClassFieldCopier<?>> writer = ExtendedClassWriter.builder(ClassFieldCopier.class)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL)
                .build();
        final String typeInternalName = MPLType.getInternalName(type);
        final String typeDescriptor = MPLType.getDescriptor(type);
        MethodVisitor mv;

        // If there are reflection copiers, add a static field for them
        if (!copiers.isEmpty()) {
            writer.visitStaticField("copiers", ReflectionCopier[].class,
                    copiers.toArray(new ReflectionCopier[copiers.size()]));
        }

        // Constructor
        {
            mv = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(ClassFieldCopier.class), "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        // tryCopy (Type, Type)
        {
            mv = writer.visitMethod(ACC_PROTECTED, "tryCopy", "(" + typeDescriptor + typeDescriptor + ")V", null, new String[] { "java/lang/Throwable" });
            mv.visitCode();

            // If there are copiers, run those first
            if (!copiers.isEmpty()) {
                final String arrCopierDesc = MPLType.getDescriptor(ReflectionCopier[].class);

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETSTATIC, writer.getInternalName(), "copiers", arrCopierDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(ClassFieldCopier.class), "copyAllWithReflection",
                        "(" + arrCopierDesc + "Ljava/lang/Object;Ljava/lang/Object;)V", false);
            }

            // Copy all the public fields
            for (Field field : publicFields) {
                final String fieldName = MPLType.getName(field);
                final String fieldDesc = MPLType.getDescriptor(field.getType());
                if (Modifier.isFinal(field.getModifiers())) {
                    // Get field, set using reflection
                    final String refFieldName = "field_" + fieldName;

                    writer.visitStaticField(refFieldName, Field.class, field);

                    mv.visitFieldInsn(GETSTATIC, writer.getInternalName(), refFieldName, "Ljava/lang/reflect/Field;");
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(GETFIELD, typeInternalName, fieldName, fieldDesc);
                    ReflectionCopier.callSetter(mv, field.getType());
                } else {
                    // Get and set field
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(GETFIELD, typeInternalName, fieldName, fieldDesc);
                    mv.visitFieldInsn(PUTFIELD, typeInternalName, fieldName, fieldDesc);
                }
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0); // Computed
            mv.visitEnd();
        }

        // tryCopy synthetic method that casts Object -> the Type
        {
            mv = writer.visitMethod(ACC_PROTECTED | ACC_BRIDGE | ACC_SYNTHETIC, "tryCopy", "(Ljava/lang/Object;Ljava/lang/Object;)V", null, new String[] { "java/lang/Throwable" });
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, typeInternalName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, typeInternalName);
            mv.visitMethodInsn(INVOKEVIRTUAL, writer.getInternalName(), "tryCopy", "(" + typeDescriptor + typeDescriptor + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }

        return writer.generateInstance();
    }
}
