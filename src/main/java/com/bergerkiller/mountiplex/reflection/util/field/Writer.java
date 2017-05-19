package com.bergerkiller.mountiplex.reflection.util.field;

/**
 * Sets the field value.
 * For static fields, use a <i>null</i> instance.
 *
 * @param <T> boxed value type of the field
 */
public interface Writer<T> {
    void set(Object instance, T value);
    void setDouble(Object instance, double value);
    void setFloat(Object instance, float value);
    void setByte(Object instance, byte value);
    void setShort(Object instance, short value);
    void setInteger(Object instance, int value);
    void setLong(Object instance, long value);
    void setCharacter(Object instance, char value);
    void setBoolean(Object instance, boolean value);

    /**
     * Gets the backing Java Reflection Field, ensuring that the field has write access
     * 
     * @return field
     */
    java.lang.reflect.Field getWriteField();

    /**
     * Checks whether write-access to the field is possible.
     * Throws an exception if that is not the case.
     */
    void checkCanWrite();
}