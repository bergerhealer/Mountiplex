package com.bergerkiller.mountiplex.reflection.util.fast;

import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;

/**
 * Invokes a (static) method or constructor.
 * To invoke static methods or constructors, use a <i>null</i> instance.
 * 
 * @param <T> invoke result type
 */
public interface Invoker<T> extends LazyInitializedObject {
    /**
     * Initializes this invoker fully, if not already fully initialized.
     * By default returns <b>this</b>. The returned invoker is ready
     * to be used. If initialization fails, an error is thrown.
     * 
     * @return initialized invoker
     */
    default Invoker<T> initializeInvoker() { return this; }

    /**
     * Calls {@link #initializeInvoker()}
     */
    default void forceInitialization() {
        initializeInvoker();
    }

    default T invoke(Object instance) {
        return invokeVA(instance);
    }

    default T invoke(Object instance, Object arg0) {
        return invokeVA(instance, arg0);
    }

    default T invoke(Object instance, Object arg0, Object arg1) {
        return invokeVA(instance, arg0, arg1);
    }

    default T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return invokeVA(instance, arg0, arg1, arg2);
    }

    default T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return invokeVA(instance, arg0, arg1, arg2, arg3);
    }

    default T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return invokeVA(instance, arg0, arg1, arg2, arg3, arg4);
    }

    T invokeVA(Object instance, Object... args);
}
