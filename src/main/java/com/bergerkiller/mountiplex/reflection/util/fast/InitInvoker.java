package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.FieldAccessor;
import com.bergerkiller.mountiplex.reflection.IgnoredFieldAccessor;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;

/**
 * Special Invoker that initializes an Invoker field when first called, allowing for
 * lazy initialization.
 *
 * @param <T>
 */
public abstract class InitInvoker<T> implements Invoker<T>, LazyInitializedObject {
    private static final InitInvoker<?> defaultUnavailableMethod = unavailable("method", "!!UNKNOWN!!");
    private final FieldAccessor<Invoker<T>> fieldAccessor;
    private final Object fieldInstance;

    /**
     * Init invoker that always runs create() and never updates a field. Primarily used
     * to throw exceptions.
     */
    protected InitInvoker() {
        this.fieldInstance = null;
        this.fieldAccessor = new IgnoredFieldAccessor<Invoker<T>>(this);
    }

    /**
     * Creates an InitInvoker that updates the place where the invoker is stored using a field accessor.
     * 
     * @param instance Instance on which get and set operations are performed
     * @param accessor The accessor using which get and set operations are performed
     */
    public <I> InitInvoker(I instance, FieldAccessor<Invoker<T>> accessor) {
        this.fieldInstance = instance;
        this.fieldAccessor = accessor;
    }

    /**
     * Creates the invoker which is meant to be used, initializing it.
     * Should throw an exception when initialization fails.
     * 
     * @return invoker
     */
    protected abstract Invoker<T> create();

    /**
     * Initializes this invoker. If this is a generated method body,
     * the method is compiled. In other cases the method will be found through
     * reflection and optimized accessors compiled. All of this is only performed once.
     * 
     * All calls to invoke() in this class explicitly call initializeInvoker() first.
     * 
     * @return the new invoker that should be used from now on
     */
    @Override
    public final Invoker<T> initializeInvoker() {
        Invoker<T> invoker = this.fieldAccessor.get(this.fieldInstance);
        if (invoker == this) {
            synchronized (this) {
                invoker = this.fieldAccessor.get(this.fieldInstance);
                if (invoker == this) {
                    invoker = this.create();
                    this.fieldAccessor.set(this.fieldInstance, invoker);
                }
            }
        }
        return invoker;
    }

    @Override
    public final T invoke(Object instance) {
        return initializeInvoker().invoke(instance);
    }

    @Override
    public final T invoke(Object instance, Object arg0) {
        return initializeInvoker().invoke(instance, arg0);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1) {
        return initializeInvoker().invoke(instance, arg0, arg1);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2, arg3);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2, arg3, arg4);
    }

    @Override
    public final T invokeVA(Object instance, Object... args) {
        return initializeInvoker().invokeVA(instance, args);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the invoker is not available
     * 
     * @param type The type of invoker that is missing (method, constructor, etc.)
     * @param missingInfo Description of the invoker that is missing
     * @return init invoker that throws when invoke() is called
     */
    public static <T> InitInvoker<T> unavailable(String type, String missingInfo) {
        return new UnavailableInvoker<T>(type, missingInfo);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the method is not available. A default description (!!UNKNOWN!!)
     * is in this exception. This can be used to initialize fields before they are assigned.
     * 
     * @return init invoker
     */
    @SuppressWarnings("unchecked")
    public static <T> InitInvoker<T> unavailableMethod() {
        return (InitInvoker<T>) defaultUnavailableMethod;
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     * 
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param method The method to create an invoker for
     * @return init invoker
     */
    public static <T> InitInvoker<T> forMethod(Object fieldInstance, String fieldName, java.lang.reflect.Method method) {
        return forMethod(fieldInstance, fieldName, new MethodDeclaration(ClassResolver.DEFAULT, method));
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     * 
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param method The method to create an invoker for
     * @return init invoker
     */
    public static <T> InitInvoker<T> forMethod(Object fieldInstance, String fieldName, MethodDeclaration method) {
        return forMethod(fieldInstance, new ReflectionFieldAccessor<T>(fieldInstance.getClass(), fieldName), method);
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using the field accessor, on the instance specified.
     * 
     * @param instance Object on which to set the invoker field
     * @param accessor Accessor for the invoker field
     * @param method The method to create an invoker for
     * @return init invoker
     */
    @SuppressWarnings("unchecked")
    public static <T> InitInvoker<T> forMethod(Object instance, FieldAccessor<Invoker<T>> accessor, MethodDeclaration method) {
        if (method == null) {
            return unavailableMethod();
        } else if (method.body != null) {
            // Runtime-generated code invoker
            return (InitInvoker<T>) InitGeneratedCodeInvoker.generate(instance, accessor, method);
        } else if (method.method != null) {
            // Runtime-generated method invoker, or one using reflection
            return new InitGeneratedMethodInvoker<T>(instance, accessor, method.method);
        } else {
            return unavailable("method", method.toString());
        }
    }

    /**
     * Reuses another invoker, initializing it when first called and updating the field where it is stored. Effectively,
     * this allows storing the same invoker in more than one place. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     * 
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param invoker The invoker to initialize and use
     * @return init invoker
     */
    public static <T> InitInvoker<T> proxy(Object fieldInstance, String fieldName, Invoker<T> invoker) {
        return proxy(fieldInstance, new ReflectionFieldAccessor<T>(fieldInstance.getClass(), fieldName), invoker);
    }

    /**
     * Reuses another invoker, initializing it when first called and updating the field where it is stored. Effectively,
     * this allows storing the same invoker in more than one place.
     * 
     * @param instance Object on which to set the invoker field
     * @param accessor Accessor for the invoker field
     * @param invoker The invoker to initialize and use
     * @return init invoker
     */
    public static <T> InitInvoker<T> proxy(Object instance, FieldAccessor<Invoker<T>> accessor, Invoker<T> invoker) {
        return new ProxyInvoker<T>(instance, accessor, invoker);
    }

    /**
     * Invoker used by {@link #unavailable(String, String)}
     *
     * @param <T>
     */
    public static final class UnavailableInvoker<T> extends InitInvoker<T> {
        private final String type;
        private final String missingInfo;

        private UnavailableInvoker(String type, String missingInfo) {
            this.type = type;
            this.missingInfo = missingInfo;
        }

        @Override
        public final Invoker<T> create() {
            throw new UnsupportedOperationException(type + " " + missingInfo + " is not available");
        }
    }

    /**
     * Reuses another invoker, proxying it, while also updating the field this proxy is stored in
     *
     * @param <T>
     */
    private static final class ProxyInvoker<T> extends InitInvoker<T> {
        private final Invoker<T> invoker;

        public ProxyInvoker(Object fieldInstance, FieldAccessor<Invoker<T>> fieldAccessor, Invoker<T> invoker) {
            super(fieldInstance, fieldAccessor);
            this.invoker = invoker;
        }

        @Override
        public Invoker<T> create() {
            return invoker.initializeInvoker();
        }
    }

    /**
     * Helper class that initializes a runtime-generated invoker based on a Method signature.
     * Generates a class that calls the method directly. If calling the method is not possible,
     * reflection is initialized and a reflection invoker is created instead.
     *
     * @param <T>
     */
    private static final class InitGeneratedMethodInvoker<T> extends InitInvoker<T> {
        private final java.lang.reflect.Method method;

        protected InitGeneratedMethodInvoker(Object instance, FieldAccessor<Invoker<T>> accessor, java.lang.reflect.Method method) {
            super(instance, accessor);
            this.method = method;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Invoker<T> create() {
            if (GeneratedMethodInvoker.canCreate(method)) {
                return (Invoker<T>) GeneratedMethodInvoker.create(method);
            } else {
                return ReflectionInvoker.create(method);
            }
        }
    }

    /**
     * Helper class that initializes a runtime-generated invoker based on a code body in a Method Declaration.
     * This is the base class, as the actual class is runtime-generated to implement the non-generic
     * interface for calling this generated method directly without casting or boxing overhead.
     *
     * @param <T>
     */
    public static abstract class InitGeneratedCodeInvoker extends InitInvoker<Object> implements GeneratedInvoker<Object> {
        private final MethodDeclaration method;

        protected InitGeneratedCodeInvoker(Object instance, FieldAccessor<Invoker<Object>> accessor, MethodDeclaration methodDeclaration) {
            super(instance, accessor);
            this.method = methodDeclaration;
        }

        @Override
        protected Invoker<Object> create() {
            return GeneratedCodeInvoker.create(this.method, this.getInterface());
        }

        public static <T> InitGeneratedCodeInvoker generate(Object instance, FieldAccessor<Invoker<T>> accessor, MethodDeclaration methodDeclaration) {
            // Generate the interface implementing the method signature
            Class<?> interfaceClass = GeneratedInvoker.generateInterface(methodDeclaration);

            // Extend InitGeneratedMethodInvoker and implement the interface class
            ExtendedClassWriter<InitGeneratedCodeInvoker> cw = ExtendedClassWriter.builder(InitGeneratedCodeInvoker.class)
                    .addInterface(interfaceClass)
                    .setFlags(ClassWriter.COMPUTE_MAXS)
                    .setAccess(ACC_FINAL).build();
            MethodVisitor mv;

            // Constructor
            {
                String argsDescriptor = "(" + Type.getDescriptor(Object.class) +
                                              Type.getDescriptor(FieldAccessor.class) +
                                              Type.getDescriptor(MethodDeclaration.class) + ")V";

                mv = cw.visitMethod(ACC_PUBLIC, "<init>", argsDescriptor, null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(InitGeneratedCodeInvoker.class), "<init>", argsDescriptor, false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // Implement the generated method: do initializeInvoker() and call same method again on the result
            {
                String methodDesc = methodDeclaration.getASMInvokeDescriptor();
                mv = cw.visitMethod(ACC_PUBLIC, methodDeclaration.name.real(), methodDesc, null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, cw.getInternalName(), "initializeInvoker", "()" + Type.getDescriptor(Invoker.class), false);
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(interfaceClass));

                int stackPos = 1;

                // Instance field
                if (!methodDeclaration.modifiers.isStatic()) {
                    Type instanceType = Type.getType(methodDeclaration.getDeclaringClass());
                    mv.visitVarInsn(instanceType.getOpcode(ILOAD), stackPos);
                    stackPos += instanceType.getSize();
                }

                // Parameters
                for (ParameterDeclaration param : methodDeclaration.parameters.parameters) {
                    Type paramType = Type.getType(param.type.type);
                    mv.visitVarInsn(paramType.getOpcode(ILOAD), stackPos);
                    stackPos += paramType.getSize();
                }

                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(interfaceClass), methodDeclaration.name.real(), methodDesc, true);
                mv.visitInsn(Type.getType(methodDeclaration.returnType.type).getOpcode(IRETURN));
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // Instantiate it using the constructor
            return cw.generateInstance(
                    new Class<?>[] {Object.class, FieldAccessor.class, MethodDeclaration.class},
                    new Object[] {instance, accessor, methodDeclaration}
            );
        }
    }

    /**
     * Helper class for changing a field using reflection. Not making use of SafeField or Generated Field
     * logic because this logic only has to occur once, and generating code for that is a waste of time.
     *
     * @param <T>
     */
    private static final class ReflectionFieldAccessor<T> implements FieldAccessor<Invoker<T>> {
        private final java.lang.reflect.Field field;

        public ReflectionFieldAccessor(Class<?> declaringClass, String fieldName) {
            try {
                this.field = findField(declaringClass, fieldName);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }

        private static java.lang.reflect.Field findField(Class<?> type, String fieldName) throws Throwable {
            try {
                return type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException err) {
                Class<?> supertype = type.getSuperclass();
                if (supertype == null) {
                    throw err;
                } else {
                    return findField(supertype, fieldName);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Invoker<T> get(Object instance) {
            try {
                this.field.setAccessible(true);
                Object value = this.field.get(instance);
                this.field.setAccessible(false);
                return (Invoker<T>) value;
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }

        @Override
        public boolean set(Object instance, Invoker<T> value) {
            try {
                this.field.setAccessible(true);
                this.field.set(instance, value);
                this.field.setAccessible(false);
                return true;
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }
    }
}
