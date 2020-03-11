package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * Defines the methods to access a certain field
 */
public interface FieldAccessor<T> {

    /**
     * Gets the value of a field from an instance
     *
     * @param instance to get from
     * @return value of the field in the instance
     */
    T get(Object instance);

    /**
     * Sets the value of a field of an instance
     *
     * @param instance to set the field in
     * @param value to set to
     * @return True if setting was successful, False if not
     */
    boolean set(Object instance, T value);

    /**
     * Checks whether this Field accessor is in a valid state<br>
     * Only if this return true can this safe accessor be used without problems
     *
     * @return True if this accessor is valid, False if not
     */
    default boolean isValid() { return true; }

    /**
     * Transfers the value of this field from one instance to another
     *
     * @param from instance to copy from
     * @param to instance to copy to
     * @return the old value in the to instance
     */
    default T transfer(Object from, Object to) {
        T to_previous = get(to);
        set(to, get(from));
        return to_previous;
    }

    /**
     * Translates the get and set types using a converter pair
     *
     * @param converterPair to use for the translation
     * @return translated Field accessor
     */
    default <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
        return new TranslatorFieldAccessor<K>(this, converterPair);
    }

    /**
     * Creates a new Field Accessor that will silently ignore get/set operations
     * when this original Field is invalid.
     * 
     * @param defaultValue to return on get operations
     * @return Field Accessor
     */
    default FieldAccessor<T> ignoreInvalid(T defaultValue) {
        if (this.isValid()) {
            return this;
        } else {
            return new IgnoredFieldAccessor<T>(defaultValue);
        }
    }
}
