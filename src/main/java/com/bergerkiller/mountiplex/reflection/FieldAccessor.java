package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.reflection.declarations.Template;

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

    /**
     * Wraps a getter and setter template method to get and set a 'field' value.
     * If the getter/setter is not available, set() returns false and get()
     * returns the default value specified.
     *
     * @param <T> Value type
     * @param getter Getter template method
     * @param setter Setter template method
     * @param defaultValue Value get() returns when the method is not available
     * @return field accessor
     */
    public static <T> FieldAccessor<T> wrapOptionalMethods(final Template.AbstractMethod<T> getter,
                                                             final Template.AbstractMethod<Void> setter,
                                                             final T defaultValue) {
        if (getter.isAvailable() && setter.isAvailable()) {
            return wrapMethods(getter, setter);
        } else if (!getter.isAvailable() && !setter.isAvailable()) {
            return new SafeDirectField<T>() {
                @Override
                public T get(Object instance) {
                    return defaultValue;
                }

                @Override
                public boolean set(Object instance, T value) {
                    return false;
                }
            };
        } else {
            return new SafeDirectField<T>() {
                @Override
                public T get(Object instance) {
                    if (getter.isAvailable()) {
                        return getter.invoker.invoke(instance);
                    } else {
                        return defaultValue;
                    }
                }

                @Override
                public boolean set(Object instance, T value) {
                    if (setter.isAvailable()) {
                        setter.invoker.invoke(instance, value);
                        return true;
                    } else {
                        return false;
                    }
                }
            };
        }
    }

    /**
     * Wraps a getter and setter template method to get and set a 'field' value
     *
     * @param <T> Value type
     * @param getter Getter template method
     * @param setter Setter template method
     * @return field accessor
     */
    public static <T> FieldAccessor<T> wrapMethods(final Template.AbstractMethod<T> getter, final Template.AbstractMethod<Void> setter) {
        final boolean valid = getter.isAvailable() && setter.isAvailable();
        return new SafeDirectField<T>() {
            @Override
            public T get(Object instance) {
                return getter.invoker.invoke(instance);
            }

            @Override
            public boolean set(Object instance, T value) {
                setter.invoker.invoke(instance, value);
                return true;
            }

            @Override
            public boolean isValid() {
                return valid;
            }
        };
    }

    /**
     * Wraps a getter and setter converted template method to get and set a 'field' value
     *
     * @param <T> Value type
     * @param getter Getter template method
     * @param setter Setter template method
     * @return field accessor
     */
    public static <T> FieldAccessor<T> wrapMethods(final Template.Method.Converted<T> getter, final Template.Method.Converted<Void> setter) {
        final boolean valid = getter.isAvailable() && setter.isAvailable();
        return new SafeDirectField<T>() {
            @Override
            public T get(Object instance) {
                return getter.invoke(instance);
            }

            @Override
            public boolean set(Object instance, T value) {
                setter.invoke(instance, value);
                return true;
            }

            @Override
            public boolean isValid() {
                return valid;
            }
        };
    }

    /**
     * Wraps a getter and setter method accessor to get and set a 'field' value
     *
     * @param <T> Value type
     * @param getter Getter method accessor
     * @param setter Setter method accessor
     * @return field accessor
     */
    public static <T> FieldAccessor<T> wrapMethods(final MethodAccessor<T> getter, final MethodAccessor<Void> setter) {
        final boolean valid = getter.isValid() && setter.isValid();
        return new SafeDirectField<T>() {
            @Override
            public T get(Object instance) {
                return getter.invoke(instance);
            }

            @Override
            public boolean set(Object instance, T value) {
                setter.invoke(instance, value);
                return true;
            }

            @Override
            public boolean isValid() {
                return valid;
            }
        };
    }
}
