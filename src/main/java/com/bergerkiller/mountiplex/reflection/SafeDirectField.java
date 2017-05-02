package com.bergerkiller.mountiplex.reflection;

import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;

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
}
