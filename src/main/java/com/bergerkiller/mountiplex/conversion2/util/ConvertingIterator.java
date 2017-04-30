package com.bergerkiller.mountiplex.conversion2.util;

import java.util.Iterator;

import com.bergerkiller.mountiplex.conversion2.Converter;

/**
 * An iterator that dynamically converts the elements as they are iterated
 *
 * @param <T> - returned element type
 */
public class ConvertingIterator<T> implements Iterator<T> {

    private final Iterator<?> iter;
    private final Converter<Object, T> converter;

    @SuppressWarnings("unchecked")
    public ConvertingIterator(Iterator<?> iterator, Converter<?, T> converter) {
        this.iter = iterator;
        this.converter = (Converter<Object, T>) converter;
    }

    @Override
    public boolean hasNext() {
        return this.iter.hasNext();
    }

    @Override
    public T next() {
        return this.converter.convert(this.iter.next());
    }

    @Override
    public void remove() {
        this.iter.remove();
    }
}
