package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public class ReflectionConstructor<T> implements Constructor<T> {
    private static final Object[] NO_ARGS = new Object[0];
    private final java.lang.reflect.Constructor<Object> c;

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ReflectionConstructor(java.lang.reflect.Constructor<?> constructor) {
        this.c = (java.lang.reflect.Constructor) constructor;
    }

    private RuntimeException f(Object[] args, Throwable t) {
        // Check argument count
        Class<?>[] paramTypes = c.getParameterTypes();
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
    public T newInstanceVA(Object... args) {
        try {
            return (T) c.newInstance(args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            } else {
                throw new RuntimeException("An error occurred in the invoked method", cause);
            }
        } catch (Throwable t) {
            throw f(args, t);
        }
    }

    @Override
    public T newInstance() {
        return newInstanceVA(NO_ARGS);
    }

    @Override
    public T newInstance(Object arg0) {
        return newInstanceVA(new Object[] {arg0});
    }

    @Override
    public T newInstance(Object arg0, Object arg1) {
        return newInstanceVA(new Object[] {arg0, arg1});
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2) {
        return newInstanceVA(new Object[] {arg0, arg1, arg2});
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
        return newInstanceVA(new Object[] {arg0, arg1, arg2, arg3});
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return newInstanceVA(new Object[] {arg0, arg1, arg2, arg3, arg4});
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> create(java.lang.reflect.Constructor<?> constructor) {
        int mod = constructor.getModifiers();
        Class<?>[] paramTypes = constructor.getParameterTypes();
        if (Modifier.isPublic(mod) && paramTypes.length <= 5) {
            return (Constructor<T>) GeneratedConstructor.create(constructor);
        } else {
            return new ReflectionConstructor<T>(constructor);
        }
    }

}
