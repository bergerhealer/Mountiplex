package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Invoker that always returns the same, constant value
 * 
 * @param <T>
 */
public final class ConstantReturningInvoker<T> implements Invoker<T> {
    private final T value;

    private ConstantReturningInvoker(T value) {
        this.value = value;
    }

    /**
     * Gets the value returned by this constant-returning invoker
     * 
     * @return value
     */
    public T getValue() {
        return value;
    }

    @Override
    public T invoke(Object instance) {
        return value;
    }

    @Override
    public T invoke(Object instance, Object arg0) {
        return value;
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1) {
        return value;
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return value;
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return value;
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return value;
    }

    @Override
    public T invokeVA(Object instance, Object... args) {
        return value;
    }

    /**
     * Creates a new constant-returning invoker
     * 
     * @param value
     * @return invoker returning value
     */
    public static <T> ConstantReturningInvoker<T> of(T value) {
        return new ConstantReturningInvoker<T>(value);
    }
}
