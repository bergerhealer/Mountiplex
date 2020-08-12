package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.MethodSignature;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Extends a class or implements an interface, overriding and implementing all methods
 * requested. The super (original) method can be called by using {@link #createSuperInvoker(Class, Method)}.
 */
public class GeneratedHook {

    /**
     * Generates a new hook class extending or implementing the base class. All methods that could be hooked
     * are queried for the base class and sent through the callbacks query function. If a non-null result
     * is returned, then that method is overrided, calling into the returned invoker.
     * 
     * @param baseClass Base class or interface to extend/implement
     * @param interfaces Additional interfaces to implement
     * @param callbacks Callbacks to override, query function
     * @return generated class
     */
    public static <T> Class<? extends T> generate(Class<T> baseClass, Collection<Class<?>> interfaces, Function<Method, Invoker<?>> callbacks) {
        final ExtendedClassWriter<T> cw = ExtendedClassWriter.builder(baseClass)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .addInterfaces(interfaces)
                .build();

        // Used when generating invokeSuper trampolines
        final String superMethodPrefix = getSuperMethodPrefix(baseClass);

        // This counter guarantees objects we add to the class have a unique name
        final AtomicInteger counter = new AtomicInteger(0);

        // Add all visible constructors
        MethodVisitor mv;
        for (java.lang.reflect.Constructor<?> constructor : baseClass.getDeclaredConstructors()) {
            String constructorDesc = MPLType.getConstructorDescriptor(constructor);
            Class<?>[] parameterTypes = constructor.getParameterTypes();

            mv = cw.visitMethod(constructor.getModifiers(),
                    "<init>",
                    constructorDesc,
                    null, /* signature */
                    MPLType.getInternalNames(constructor.getExceptionTypes()));
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0); // this

            // Parameters of the constructor
            int varIndex = 1;
            for (Class<?> parameterType : parameterTypes) {
                varIndex = MPLType.visitVarILoad(mv, parameterType, varIndex);
            }

            mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(baseClass), "<init>", constructorDesc, false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Collect all classes to operate on, in order
        // First we go through the methods declared in the base class itself, then its
        // superclasses, then all interfaces for the base class and all it's superclasses.
        // TODO: Some way to maybe do this through streaming API?
        LinkedHashSet<Class<?>> types = new LinkedHashSet<Class<?>>();
        for (Class<?> currentClass = baseClass; currentClass != null; currentClass = currentClass.getSuperclass()) {
            types.add(currentClass);
        }
        for (Class<?> currentClass = baseClass; currentClass != null; currentClass = currentClass.getSuperclass()) {
            types.addAll(Arrays.asList(currentClass.getInterfaces()));
        }
        types.addAll(interfaces);

        // Track what methods we have already implemented to prevent multiple-implementations
        final Set<MethodSignature> implemented = new HashSet<MethodSignature>();
        types.stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(method -> {
                    int modifiers = method.getModifiers();

                    // Can't override private or static methods
                    if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) {
                        return false;
                    }

                    // If final, skip, can't hook, but also label as implemented
                    // This prevents us later trying to implement it, if it is also
                    // declared inside an interface or superclass.
                    if (Modifier.isFinal(modifiers)) {
                        implemented.add(new MethodSignature(method));
                        return false;
                    }

                    return true;
                })
                .forEach(method -> {
                    // Check not already implemented
                    MethodSignature signature = new MethodSignature(method);
                    if (!implemented.contains(signature)) {
                        Invoker<?> callback = callbacks.apply(method);
                        if (callback != null) {
                            implemented.add(signature);
                            implement(cw, method, callback, superMethodPrefix, counter);
                        }
                    }
                });

        return cw.generate();
    }

    /**
     * Generates an invoker that, when invoked, calls the method of a generated class
     * with the invoke-super flag set. If the particular method is not overrided in
     * the generated class, then a default method invoker is generated instead.
     * 
     * @param method Method to invoke
     * @return Invoker that calls the super-method of the method
     */
    public static <T> Invoker<T> createSuperInvoker(Class<?> generatedClass, Method method) {
        String superMethodName = getSuperMethodPrefix(generatedClass.getSuperclass()) + MPLType.getName(method);
        Method methodToInvoke = method;
        try {
            methodToInvoke = generatedClass.getDeclaredMethod(superMethodName, method.getParameterTypes());
        } catch (Throwable t) { /* not declared */ }

        // Standard invoker, use reflection if needed, otherwise generate one
        if (GeneratedMethodInvoker.canCreate(methodToInvoke)) {
            return GeneratedMethodInvoker.create(methodToInvoke);
        } else {
            return ReflectionInvoker.create(methodToInvoke);
        }
    }

    /**
     * Computes how many levels deep class inheritance goes. This number is used
     * to generate unique method names for super-invoker trampolines that guarantee
     * they don't accidentally get overrided.
     * 
     * @param type
     * @return prefix
     */
    private static String getSuperMethodPrefix(Class<?> type) {
        int depth = 0;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            depth++;
        }
        return "mplsuper" + depth + "_";
    }

    /**
     * Creates an invoker that returns the value returned by a supplier, and stores it in
     * a local field of the instance on which the method is called. If the value is
     * already stored in the field, then that value is returned the next time the method is called.<br>
     * <br>
     * <b>Can only be used with the {@link GeneratedHook}</b>
     * 
     * @param initialValue Supplier for the initial value, the first time the method is called
     * @return invoker
     */
    public static <T> Invoker<T> createLocalField(Supplier<T> initialValue) {
        return new LocalFieldInvoker<T>(initialValue);
    }

    private static class LocalFieldInvoker<T> implements Invoker<T> {
        private final Supplier<T> initialValue;

        public LocalFieldInvoker(Supplier<T> initialValue) {
            this.initialValue = initialValue;
        }

        @Override
        public T invokeVA(Object instance, Object... args) {
            return this.initialValue.get();
        }
    }

    private static void implement(ExtendedClassWriter<?> cw, Method method, Invoker<?> callback, String superMethodPrefix, AtomicInteger counter) {
        MethodVisitor mv;
        FieldVisitor fv;

        // Add super invoke method trampoline, which allows the real underlying method to be called from the outside
        // The name is a predictable format
        {
            mv = cw.visitMethod(Modifier.PUBLIC | Modifier.FINAL, /* public, so invoking it is faster */
                    superMethodPrefix + MPLType.getName(method),
                    MPLType.getMethodDescriptor(method),
                    null, /* signature */
                    MPLType.getInternalNames(method.getExceptionTypes()));
            mv.visitCode();
            if (Modifier.isAbstract(method.getModifiers())) {
                // unsupported
                mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
                mv.visitInsn(DUP);
                mv.visitLdcInsn("Method is abstract");
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
                mv.visitInsn(ATHROW);
            } else {
                // super()
                mv.visitVarInsn(ALOAD, 0); // this
                Class<?>[] parameterTypes = method.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    mv.visitVarInsn(MPLType.getOpcode(parameterTypes[i], ILOAD), i+1);
                }
                mv.visitMethodInsn(INVOKESPECIAL,
                        MPLType.getInternalName(method.getDeclaringClass()),
                        MPLType.getName(method),
                        MPLType.getMethodDescriptor(method),
                        false);
                mv.visitInsn(MPLType.getOpcode(method.getReturnType(), IRETURN));
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // This postfix is put after any fields added to the Class
        String postfix = MPLType.getName(method) + "_" + counter.incrementAndGet();

        // Local field invoker: add a local field, and generate a method body that initializes the
        // field the first time the method is called
        if (callback instanceof LocalFieldInvoker) {
            String returnTypeDesc = MPLType.getDescriptor(method.getReturnType());

            // Add the supplier as a static field
            String fieldSupplierFieldName = "supplier_" + postfix;
            cw.visitStaticField(fieldSupplierFieldName, Supplier.class, ((LocalFieldInvoker<?>) callback).initialValue);

            // Add a boolean field which stores whether this local field has been initialized yet
            String localIsInitializedFieldName = "init_" + postfix;
            fv = cw.visitField(ACC_PUBLIC, localIsInitializedFieldName, "Z", null, null);
            fv.visitEnd();

            // Add a typed field which stores the last computed value for the instance
            String localValueFieldName = "value_" + postfix;
            fv = cw.visitField(ACC_PUBLIC, localValueFieldName, returnTypeDesc, null, null);
            fv.visitEnd();

            // Implement the method itself
            mv = cw.visitMethod(Modifier.PUBLIC, /* public, so invoking it is faster */
                    MPLType.getName(method),
                    MPLType.getMethodDescriptor(method),
                    null, /* signature */
                    MPLType.getInternalNames(method.getExceptionTypes()));
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, cw.getInternalName(), localIsInitializedFieldName, "Z");
            Label label0 = new Label();
            mv.visitJumpInsn(IFEQ, label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, cw.getInternalName(), localValueFieldName, returnTypeDesc);
            mv.visitInsn(MPLType.getOpcode(method.getReturnType(), IRETURN));
            mv.visitLabel(label0);
            mv.visitFrame(F_SAME, 0, null, 0, null);
            mv.visitFieldInsn(GETSTATIC, cw.getInternalName(), fieldSupplierFieldName, MPLType.getDescriptor(Supplier.class));
            mv.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Supplier.class), "get", "()Ljava/lang/Object;", true);
            ExtendedClassWriter.visitUnboxVariable(mv, method.getReturnType());
            int varResultIdx = 1 + method.getParameterCount();
            mv.visitVarInsn(ASTORE, varResultIdx);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, varResultIdx);
            mv.visitFieldInsn(PUTFIELD, cw.getInternalName(), localValueFieldName, returnTypeDesc);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(ICONST_1);
            mv.visitFieldInsn(PUTFIELD, cw.getInternalName(), localIsInitializedFieldName, "Z");
            mv.visitVarInsn(ALOAD, varResultIdx);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }

        String invokerFieldName = "invoker_" + postfix;
        Class<?>[] parameterTypes = method.getParameterTypes();

        cw.visitStaticField(invokerFieldName, Invoker.class, callback);

        mv = cw.visitMethod(Modifier.PUBLIC, /* public, so invoking it is faster */
                MPLType.getName(method),
                MPLType.getMethodDescriptor(method),
                null, /* signature */
                MPLType.getInternalNames(method.getExceptionTypes()));
        mv.visitCode();

        // Retrieve the invoker instance we want to call invoke() / invokeVA() on
        mv.visitFieldInsn(GETSTATIC, cw.getInternalName(), invokerFieldName, MPLType.getDescriptor(Invoker.class));

        // Load 'this' onto the stack
        mv.visitVarInsn(ALOAD, 0);

        // Call the invoker interface method
        if (parameterTypes.length <= 5) {
            // Load all the parameters onto the stack, box them into Objects if needed
            int varIdx = 1;
            for (Class<?> parameterType : parameterTypes) {
                varIdx = MPLType.visitVarILoad(mv, parameterType, varIdx);
                ExtendedClassWriter.visitBoxVariable(mv, parameterType);
            }

            // invoke
            StringBuilder descriptor = new StringBuilder();
            descriptor.append('(');
            for (int i = 0; i <= parameterTypes.length; i++) {
                descriptor.append("Ljava/lang/Object;");
            }
            descriptor.append(")Ljava/lang/Object;");
            mv.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Invoker.class), "invoke", descriptor.toString(), true);
        } else {
            // Create new array with all the parameters loaded inside
            ExtendedClassWriter.visitPushInt(mv, parameterTypes.length);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            int varIdx = 1;
            for (int i = 0; i < parameterTypes.length; i++) {
                mv.visitInsn(DUP);
                ExtendedClassWriter.visitPushInt(mv, i);
                varIdx = MPLType.visitVarILoad(mv, parameterTypes[i], varIdx);
                ExtendedClassWriter.visitBoxVariable(mv, parameterTypes[i]);
                mv.visitInsn(AASTORE);
            }

            // invokeVA
            mv.visitMethodInsn(INVOKEINTERFACE, MPLType.getInternalName(Invoker.class),
                    "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", true);
        }

        // Return result of the invoker, may need to unbox it
        if (method.getReturnType() == void.class) {
            mv.visitInsn(POP);
            mv.visitInsn(RETURN);
        } else {
            ExtendedClassWriter.visitUnboxVariable(mv, method.getReturnType());
            mv.visitInsn(MPLType.getOpcode(method.getReturnType(), IRETURN));
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
