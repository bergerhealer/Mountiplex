package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.UnhandledInvokerCheckedException;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

public abstract class ReflectionInvoker<T> implements Invoker<T> {
    private static final Object[] NO_ARGS = new Object[0];

    private static RuntimeException checkInstance(java.lang.reflect.Executable executable, Object instance) {
        // Verify the instance is of the correct type
        if (Modifier.isStatic(executable.getModifiers())) {
            if (instance != null) {
                return new IllegalArgumentException("Instance should be null for static fields, but was " +
                        MPLType.getName(instance.getClass()) + " instead");
            }
        } else {
            if (instance == null) {
                return new IllegalArgumentException("Instance can not be null for member fields declared in " +
                        MPLType.getName(executable.getDeclaringClass()));
            }
            if (!executable.getDeclaringClass().isAssignableFrom(instance.getClass())) {
                return new IllegalArgumentException("Instance of type " + MPLType.getName(instance.getClass()) +
                        " does not contain the field declared in " + MPLType.getName(executable.getDeclaringClass()));
            }
        }
        return null;
    }

    protected static RuntimeException f(java.lang.reflect.Executable executable, Object instance, Object[] args, Throwable t) {
        // Check instance
        RuntimeException iex = checkInstance(executable, instance);
        if (iex != null) {
            return iex;
        }

        // Check argument count
        Class<?>[] paramTypes = executable.getParameterTypes();
        if (paramTypes.length != args.length) {
            return new InvalidArgumentCountException("method", args.length, paramTypes.length);
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
                    return new IllegalArgumentException("Value of type " + MPLType.getName(args[i].getClass()) +
                            " can not be assigned to primitive " + paramTypes[i].getSimpleName() +
                            " method parameter #" + i);
                }
            } else if (args[i] != null) {
                if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                    return new IllegalArgumentException("Value of type " + MPLType.getName(args[i].getClass()) +
                            " can not be assigned to " + MPLType.getName(paramTypes[i]) +
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

    public static <T> Invoker<T> create(java.lang.reflect.Executable executable) {
        executable.setAccessible(true);
        if (executable instanceof java.lang.reflect.Method) {
            return new ReflectionMethodInvoker<T>((java.lang.reflect.Method) executable);
        } else if (executable instanceof java.lang.reflect.Constructor) {
            return new ReflectionConstructorInvoker<T>((java.lang.reflect.Constructor<?>) executable);
        } else {
            throw new IllegalArgumentException("Not a method or constructor");
        }
    }

    private static class ReflectionMethodInvoker<T> extends ReflectionInvoker<T> {
        protected final java.lang.reflect.Method m;

        public ReflectionMethodInvoker(java.lang.reflect.Method method) {
            this.m = method;
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
                    throw new UnhandledInvokerCheckedException(cause);
                }
            } catch (Throwable t) {
                throw f(m, instance, args, t);
            }
        }
    }

    private static class ReflectionConstructorInvoker<T> extends ReflectionInvoker<T> {
        protected final java.lang.reflect.Constructor<T> c;

        @SuppressWarnings("unchecked")
        public ReflectionConstructorInvoker(java.lang.reflect.Constructor<?> constructor) {
            this.c = (Constructor<T>) constructor;
        }

        @Override
        public T invokeVA(Object instance, Object... args) {
            try {
                return (T) c.newInstance(args);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException) {
                    throw ((RuntimeException) cause);
                } else {
                    throw new UnhandledInvokerCheckedException(cause);
                }
            } catch (Throwable t) {
                throw f(c, instance, args, t);
            }
        }
    }
}
