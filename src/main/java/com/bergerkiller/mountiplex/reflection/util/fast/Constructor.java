package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Invokes a constructor.
 * 
 * @param <T> constructor Class type
 */
public interface Constructor<T> {
    T newInstance();
    T newInstance(Object arg0);
    T newInstance(Object arg0, Object arg1);
    T newInstance(Object arg0, Object arg1, Object arg2);
    T newInstance(Object arg0, Object arg1, Object arg2, Object arg3);
    T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4);
    T newInstanceVA(Object... args);
}
