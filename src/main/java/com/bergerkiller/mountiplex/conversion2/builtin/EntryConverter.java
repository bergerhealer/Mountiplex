package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.Map.Entry;

import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingEntry;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Converter that uses a separate key and value converter to convert incoming
 * entries. Because the generic types are also duplex converters, the Entry Converter
 * is duplex by nature.
 *
 * @param <K> - entry converter key type
 * @param <V> - entry converter value type
 */
public class EntryConverter<K, V> extends DuplexConverter<Entry<?, ?>, Entry<K, V>> {
    private final DuplexConverter<Object, K> keyConverter;
    private final DuplexConverter<Object, V> valueConverter;

    @SuppressWarnings("unchecked")
    protected EntryConverter(DuplexConverter<?, K> keyConverter, DuplexConverter<?, V> valueConverter) {
        super(entryType(keyConverter.input, valueConverter.input), entryType(valueConverter.output, valueConverter.output));
        this.keyConverter = (DuplexConverter<Object, K>) keyConverter;
        this.valueConverter = (DuplexConverter<Object, V>) valueConverter;
    }

    @Override
    public Entry<K, V> convertInput(Entry<?, ?> value) {
        return new ConvertingEntry<K, V>(value, keyConverter, valueConverter);
    }

    @Override
    public Entry<?, ?> convertOutput(Entry<K, V> value) {
        return new ConvertingEntry<Object, Object>(value, keyConverter.reverse(), valueConverter.reverse());
    }

    private static TypeDeclaration entryType(TypeDeclaration keyType, TypeDeclaration valueType) {
        return TypeDeclaration.fromClass(Entry.class).setGenericTypes(keyType, valueType);
    }

    public static <K, V> EntryConverter<K, V> create(DuplexConverter<?, K> keyConverter, DuplexConverter<?, V> valueConverter) {
        return new EntryConverter<K, V>(keyConverter, valueConverter);
    }
}
