package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Invokes a (static) method or constructor.
 * To invoke static methods or constructors, use a <i>null</i> instance.
 * 
 * @param <T> invoke result type
 */
public interface Invoker<T> {

    T invoke(Object instance, Object[] args);

}
