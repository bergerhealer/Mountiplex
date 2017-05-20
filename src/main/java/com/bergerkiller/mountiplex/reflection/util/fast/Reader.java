package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Gets the field value.
 * For static fields, use a <i>null</i> instance.
 * 
 * @param <T> boxed value type of the field
 */
public interface Reader<T> {
    T       get(Object instance);
    double  getDouble(Object instance);
    float   getFloat(Object instance);
    byte    getByte(Object instance);
    short   getShort(Object instance);
    int     getInteger(Object instance);
    long    getLong(Object instance);
    char    getCharacter(Object instance);
    boolean getBoolean(Object instance);

    /**
     * Gets the backing Java Reflection Field, ensuring that the field has read access
     * 
     * @return field
     */
    java.lang.reflect.Field getReadField();

    /**
     * Checks whether read-access to the field is possible.
     * Throws an exception if that is not the case.
     */
    void checkCanRead();
}