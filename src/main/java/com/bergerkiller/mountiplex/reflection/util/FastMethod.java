package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;
import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionInvoker;

public final class FastMethod<T> implements Invoker<T> {
    public Invoker<T> invoker = this;
    private java.lang.reflect.Method method = null;

    public final void init(java.lang.reflect.Method method) {
        this.method = method;
        this.invoker = this;
    }

    /**
     * Checks whether this fast method is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (method == null) {
            throw new UnsupportedOperationException("Method is not available");
        }
    }

    /**
     * Gets the backing Java Reflection Method for this Fast Method. If this fast method
     * is not initialized, this function returns <i>null</i>.
     * 
     * @return method
     */
    public final java.lang.reflect.Method getMethod() {
        return this.method;
    }

    /**
     * Gets the name of this method.
     * Returns <i>"null"</i> if this fast method is not initialized.
     * 
     * @return method name
     */
    public final String getName() {
        if (this.method == null) {
            return "null";
        } else {
            return this.method.getName();
        }
    }

    private final Invoker<T> init() {
        if (this.invoker == this) {
            checkInit();
            this.method.setAccessible(true);
            this.invoker = ReflectionInvoker.create(method);
        }
        return this.invoker;
    }

    @Override
    public T invoke(Object instance, Object[] args) {
        return this.init().invoke(instance, args);
    }
}
