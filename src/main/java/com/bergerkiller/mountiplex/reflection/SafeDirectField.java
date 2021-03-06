package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * A field implementation that allows direct getting and setting
 */
public abstract class SafeDirectField<T> implements FieldAccessor<T> {

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public T transfer(Object from, Object to) {
        T old = get(to);
        set(to, get(from));
        return old;
    }

    @Override
    public <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
        return new TranslatorFieldAccessor<K>(this, converterPair);
    }

    @Override
    public FieldAccessor<T> ignoreInvalid(T defaultValue) {
        if (this.isValid()) {
            return this;
        } else {
            return new IgnoredFieldAccessor<T>(defaultValue);
        }
    }
}
