package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Invokes a (static) method or constructor.
 * To invoke static methods or constructors, use a <i>null</i> instance.
 * 
 * @param <T> invoke result type
 */
public interface Invoker<T> {
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
