package com.bergerkiller.mountiplex.conversion.util;

import java.util.Iterator;

import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * Wraps around an Iterable and alters the {@link #iterator()} function to return a
 * wrapped iterator that performs element conversion.
 * 
 * @param <T> element type
 */
public class ConvertingIterable<T> implements Iterable<T> {
    private final Iterable<?> base;
    private final Converter<?, T> converter;

    public ConvertingIterable(Iterable<?> base, Converter<?, T> converter) {
        this.base = base;
        this.converter = converter;
    }

    public Converter<?, T> getConverter() {
        return this.converter;
    }

    @Override
    public Iterator<T> iterator() {
        return new ConvertingIterator<T>(base.iterator(), converter);
    }
}
