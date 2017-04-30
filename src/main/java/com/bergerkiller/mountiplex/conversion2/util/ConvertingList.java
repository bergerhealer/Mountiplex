package com.bergerkiller.mountiplex.conversion2.util;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;

/**
 * Wraps around another list of unknown contents and performs conversions
 * automatically. This can be used to interact with collections that require
 * additional element conversion.
 *
 * @param <T> - exposed type
 */
public class ConvertingList<T> extends ConvertingCollection<T> implements List<T> {

    public ConvertingList(List<?> list, DuplexConverter<?, T> converter) {
        super(list, converter);
    }

    @Override
    public List<Object> getBase() {
        return (List<Object>) super.getBase();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return getBase().addAll(index, new ConvertingCollection<Object>(c, converter.reverse()));
    }

    @Override
    public T get(int index) {
        return converter.convert(getBase().get(index));
    }

    @Override
    public T set(int index, T element) {
        return converter.convert(getBase().set(index, converter.convertReverse(element)));
    }

    @Override
    public void add(int index, T element) {
        getBase().add(index, converter.convert(element));
    }

    @Override
    public T remove(int index) {
        return converter.convert(getBase().remove(index));
    }

    @Override
    @SuppressWarnings("unchecked")
    public int indexOf(Object o) {
        return getBase().indexOf(converter.convertReverse((T) o));
    }

    @Override
    @SuppressWarnings("unchecked")
    public int lastIndexOf(Object o) {
        return getBase().lastIndexOf(converter.convertReverse((T) o));
    }

    @Override
    public ListIterator<T> listIterator() {
        return new ConvertingListIterator<T>(getBase().listIterator(), converter);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return new ConvertingListIterator<T>(getBase().listIterator(index), converter);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return new ConvertingList<T>(getBase().subList(fromIndex, toIndex), converter);
    }
}
