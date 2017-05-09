package com.bergerkiller.mountiplex.conversion.util;

import java.util.ListIterator;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

public class ConvertingListIterator<T> implements ListIterator<T> {

    private final ListIterator<Object> iter;
    private final DuplexConverter<Object, T> converter;

    @SuppressWarnings("unchecked")
    public ConvertingListIterator(ListIterator<?> listIterator, DuplexConverter<?, T> converter) {
        this.iter = (ListIterator<Object>) listIterator;
        this.converter = (DuplexConverter<Object, T>) converter;
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public T next() {
        return converter.convert(iter.next());
    }

    @Override
    public boolean hasPrevious() {
        return iter.hasPrevious();
    }

    @Override
    public T previous() {
        return converter.convert(iter.previous());
    }

    @Override
    public int nextIndex() {
        return iter.nextIndex();
    }

    @Override
    public int previousIndex() {
        return iter.previousIndex();
    }

    @Override
    public void remove() {
        iter.remove();
    }

    @Override
    public void set(T e) {
        iter.set(converter.convertReverse(e));
    }

    @Override
    public void add(T e) {
        iter.add(converter.convertReverse(e));
    }
}
