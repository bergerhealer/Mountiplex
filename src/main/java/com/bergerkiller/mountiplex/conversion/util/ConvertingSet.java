package com.bergerkiller.mountiplex.conversion.util;

import java.util.Set;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterPair;

/**
 * Wraps around another set of unknown contents and performs conversions
 * automatically. This can be used to interact with collections that require
 * additional element conversion.
 *
 * @param <T> - exposed type
 */
public class ConvertingSet<T> extends ConvertingCollection<T> implements Set<T> {

    public ConvertingSet(Set<?> collection, Converter<?> converterSet, Converter<T> converterGet) {
        super(collection, converterSet, converterGet);
    }

    public ConvertingSet(Set<?> collection, ConverterPair<?, T> converterPair) {
        super(collection, converterPair);
    }
}
