package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

public class IgnoredFieldAccessor<T> implements FieldAccessor<T> {
    private final T defaultValue;

    public IgnoredFieldAccessor(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public T get(Object instance) {
        return this.defaultValue;
    }

    @Override
    public boolean set(Object instance, T value) {
        return false;
    }

    @Override
    public T transfer(Object from, Object to) {
        return this.defaultValue;
    }

    @Override
    public <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
        return new TranslatorFieldAccessor<K>(this, converterPair);
    }

    @Override
    public FieldAccessor<T> ignoreInvalid(T defaultValue) {
        return new IgnoredFieldAccessor<T>(defaultValue);
    }
}
