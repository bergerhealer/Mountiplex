package com.bergerkiller.mountiplex.conversion.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.bergerkiller.mountiplex.conversion.builtin.EntryConverter;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * Wraps around another Map of unknown contents and performs key/value
 * conversions automatically. This can be used to interact with maps that
 * require additional element conversion.
 *
 * @param <K> - key type
 * @param <V> - value type
 */
public class ConvertingMap<K, V> implements Map<K, V> {

    private final Map<Object, Object> base;
    protected final DuplexConverter<Object, K> keyConverter;
    protected final DuplexConverter<Object, V> valueConverter;

    @SuppressWarnings("unchecked")
    public ConvertingMap(Map<?, ?> map, DuplexConverter<?, K> keyConverter, DuplexConverter<?, V> valueConverter) {
        this.base = (Map<Object, Object>) map;
        this.keyConverter = (DuplexConverter<Object, K>) keyConverter;
        this.valueConverter = (DuplexConverter<Object, V>) valueConverter;
    }

    @Override
    public int size() {
        return base.size();
    }

    @Override
    public boolean isEmpty() {
        return base.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return base.containsKey(keyConverter.convertReverse(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return base.containsValue(valueConverter.convertReverse(value));
    }

    @Override
    public V get(Object key) {
        return valueConverter.convert(base.get(keyConverter.convertReverse(key)));
    }

    @Override
    public V put(K key, V value) {
        return valueConverter.convert(base.put(keyConverter.convertReverse(key), valueConverter.convertReverse(value)));
    }

    @Override
    public V remove(Object key) {
        return valueConverter.convert(base.remove(keyConverter.convertReverse(key)));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        base.clear();
    }

    @Override
    public Set<K> keySet() {
        return new ConvertingSet<K>(base.keySet(), keyConverter);
    }

    @Override
    public Collection<V> values() {
        return new ConvertingCollection<V>(base.values(), valueConverter);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new ConvertingSet<Entry<K, V>>(base.entrySet(), EntryConverter.create(keyConverter, valueConverter));
    }
}
