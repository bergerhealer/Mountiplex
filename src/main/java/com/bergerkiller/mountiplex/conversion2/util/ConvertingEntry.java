package com.bergerkiller.mountiplex.conversion2.util;

import java.util.Map.Entry;

import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;

/**
 * Wraps around another Entry of unknown contents and performs conversions
 * automatically. This can be used to interact with entries that require
 * additional element conversion.
 *
 * @param <K> - entry key type
 * @param <V> - entry value type
 */
public class ConvertingEntry<K, V> implements Entry<K, V> {

    private final Entry<Object, Object> base;
    private final DuplexConverter<Object, K> keyConverter;
    private final DuplexConverter<Object, V> valueConverter;

    @SuppressWarnings("unchecked")
    public ConvertingEntry(Entry<?, ?> entry, DuplexConverter<?, K> keyConverter, DuplexConverter<?, V> valueConverter) {
        this.base = (Entry<Object, Object>) entry;
        this.keyConverter = (DuplexConverter<Object, K>) keyConverter;
        this.valueConverter = (DuplexConverter<Object, V>) valueConverter;
    }

    @Override
    public K getKey() {
        return keyConverter.convert(base.getKey());
    }

    @Override
    public V getValue() {
        return valueConverter.convert(base.getValue());
    }

    @Override
    public V setValue(V value) {
        return valueConverter.convert(base.setValue(valueConverter.convertReverse(value)));
    }
}
