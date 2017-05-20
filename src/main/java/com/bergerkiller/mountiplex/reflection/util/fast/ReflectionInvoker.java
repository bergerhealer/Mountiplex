package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public class ReflectionInvoker<T> implements Invoker<T> {
    private static final Object[] NO_ARGS = new Object[0];
    private final java.lang.reflect.Method m;

    protected ReflectionInvoker(java.lang.reflect.Method method) {
        this.m = method;
    }

    private RuntimeException checkInstance(Object instance) {
        // Verify the instance is of the correct type
        if (Modifier.isStatic(m.getModifiers())) {
            if (instance != null) {
                return new IllegalArgumentException("Instance should be null for static fields, but was " +
                        instance.getClass().getName() + " instead");
            }
        } else {
            if (instance == null) {
                return new IllegalArgumentException("Instance can not be null for member fields declared in " +
                        m.getDeclaringClass().getName());
            }
            if (!m.getDeclaringClass().isAssignableFrom(instance.getClass())) {
                return new IllegalArgumentException("Instance of type " + instance.getClass().getName() +
                        " does not contain the field declared in " + m.getDeclaringClass().getName());
            }
        }
        return null;
    }

    private RuntimeException f(Object instance, Object[] args, Throwable t) {
        // Check instance
        RuntimeException iex = checkInstance(instance);
        if (iex != null) {
            return iex;
        }

        // Check argument count
        Class<?>[] paramTypes = m.getParameterTypes();
        if (paramTypes.length != args.length) {
            return new IllegalArgumentException("Invalid amount of arguments for method (" +
                    args.length + " given, " + paramTypes.length + " expected)");
        }

        // Check argument types
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].isPrimitive()) {
                if (args[i] == null) {
                    return new IllegalArgumentException("Illegal null value used for primitive " +
                            paramTypes[i].getSimpleName() + " method parameter #" + i);
                }
                Class<?> boxed = BoxedType.getBoxedType(paramTypes[i]);
                if (boxed != null && !boxed.isAssignableFrom(args[i].getClass())) {
                    return new IllegalArgumentException("Value of type " + args[i].getClass().getName() +
                            " can not be assigned to primitive " + paramTypes[i].getSimpleName() +
                            " method parameter #" + i);
                }
            } else if (args[i] != null) {
                if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                    return new IllegalArgumentException("Value of type " + args[i].getClass().getName() +
                            " can not be assigned to " + paramTypes[i].getName() +
                            " method parameter #" + i);
                }
            }
        }

        // Don't know, then
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException("Failed to invoke method", t);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T invokeVA(Object instance, Object... args) {
        try {
            return (T) m.invoke(instance, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            } else {
                throw new RuntimeException("An error occurred in the invoked method", cause);
            }
        } catch (Throwable t) {
            throw f(instance, args, t);
        }
    }

    @Override
    public T invoke(Object instance) {
        return invokeVA(instance, NO_ARGS);
    }

    @Override
    public T invoke(Object instance, Object arg0) {
        return invokeVA(instance, new Object[] {arg0});
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1) {
        return invokeVA(instance, new Object[] {arg0, arg1});
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return invokeVA(instance, new Object[] {arg0, arg1, arg2});
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return invokeVA(instance, new Object[] {arg0, arg1, arg2, arg3});
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return invokeVA(instance, new Object[] {arg0, arg1, arg2, arg3, arg4});
    }

    @SuppressWarnings("unchecked")
    public static <T> Invoker<T> create(java.lang.reflect.Method method) {
        int mod = method.getModifiers();
        Class<?>[] paramTypes = method.getParameterTypes();
        if (Modifier.isPublic(mod) && !Modifier.isStatic(mod) && paramTypes.length <= 5) {
            //TODO: Generate a faster alternative to reflection for the exact argument count
            boolean hasPrimitiveTypes = false;
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].isPrimitive()) {
                    hasPrimitiveTypes = true;
                    break;
                }
            }
            if (method.getReturnType().isPrimitive() && method.getReturnType() != void.class) {
                hasPrimitiveTypes = true;
            }

            // If no primitve types exist, optimize the method call by generating it
            // The generated invoker does not yet support boxing/unboxing.
            if (!hasPrimitiveTypes) {
                return (Invoker<T>) GeneratedInvoker.create(method);
            }
        }
        return new ReflectionInvoker<T>(method);
    }

}
