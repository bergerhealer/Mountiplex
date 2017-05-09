package com.bergerkiller.mountiplex.conversion.util;

import java.util.Set;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * Wraps around another set of unknown contents and performs conversions
 * automatically. This can be used to interact with collections that require
 * additional element conversion.
 *
 * @param <T> - exposed type
 */
public class ConvertingSet<T> extends ConvertingCollection<T> implements Set<T> {

    public ConvertingSet(Set<?> collection, DuplexConverter<?, T> converterPair) {
        super(collection, converterPair);
    }
}
