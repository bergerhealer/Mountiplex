package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;

/**
 * A field accessor that can translate from one type to another to expose a
 * different type than is stored
 *
 * @param <T> Type exposed
 */
public class TranslatorFieldAccessor<T> implements FieldAccessor<T> {

    private final FieldAccessor<Object> base;
    private final DuplexConverter<Object, T> converterPair;

    @SuppressWarnings("unchecked")
    public TranslatorFieldAccessor(FieldAccessor<?> base, Converter<?, ?> setConverter, Converter<?, T> getConverter) {
        this(base, DuplexConverter.pair((Converter<Object, T>) getConverter, (Converter<T, Object>) setConverter));
    }

    @SuppressWarnings("unchecked")
    public TranslatorFieldAccessor(FieldAccessor<?> base, DuplexConverter<?, T> converterPair) {
        if (base == null) {
            throw new IllegalArgumentException("Can not construct using a null base");
        }
        if (converterPair == null) {
            throw new IllegalArgumentException("Can not construct using a null converter pair");
        }
        this.base = (FieldAccessor<Object>) base;
        this.converterPair = (DuplexConverter<Object, T>) converterPair;
    }

    @Override
    public boolean isValid() {
        return base.isValid();
    }

    /**
     * Gets the internally stored value from an instance
     *
     * @param instance containing this Field
     * @return field value from instance
     */
    public Object getInternal(Object instance) {
        return base.get(instance);
    }

    /**
     * Sets the internally stored value for an instance
     *
     * @param instance containing this Field
     * @param value to set the field to
     * @return True if successful, False if not
     */
    public boolean setInternal(Object instance, Object value) {
        return base.set(instance, value);
    }

    @Override
    public T get(Object instance) {
        return converterPair.convert(getInternal(instance));
    }

    @Override
    public boolean set(Object instance, T value) {
        return setInternal(instance, converterPair.convertReverse(value));
    }

    @Override
    public T transfer(Object from, Object to) {
        return converterPair.convert(base.transfer(from, to));
    }

    @Override
    public <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
        return new TranslatorFieldAccessor<K>(this, converterPair);
    }
}
