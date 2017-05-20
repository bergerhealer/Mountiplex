package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Invokes a (static) method or constructor.
 * To invoke static methods or constructors, use a <i>null</i> instance.
 * 
 * @param <T> invoke result type
 */
public interface Invoker<T> {
    T invoke(Object instance);
    T invoke(Object instance, Object arg0);
    T invoke(Object instance, Object arg0, Object arg1);
    T invoke(Object instance, Object arg0, Object arg1, Object arg2);
    T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3);
    T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);
    T invokeVA(Object instance, Object... args);
}
