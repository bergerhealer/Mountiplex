package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Copies the member field value from one object to another.
 * Static fields do not support copying.
 */
public interface Copier {
    /**
     * Copies the member field value from one object instance to another.
     * 
     * @param instanceFrom to read the field from
     * @param instanceTo to write the field to
     */
    void copy(Object instanceFrom, Object instanceTo);

    /**
     * Gets the backing Java Reflection Field, ensuring that the field has read and write access
     * 
     * @return field
     */
    java.lang.reflect.Field getCopyField();

    /**
     * Checks whether both read and write access to the field is possible.
     * Throws an exception if that is not the case.
     */
    void checkCanCopy();
}