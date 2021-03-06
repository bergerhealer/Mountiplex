package com.bergerkiller.mountiplex.conversion.util;

import java.util.Collection;
import java.util.Iterator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * Wraps around another collection of unknown contents and performs conversions
 * automatically. This can be used to interact with collections that require
 * additional element conversion.
 *
 * @param <T> - exposed type
 */
public class ConvertingCollection<T> implements Collection<T> {

    private final Collection<Object> base;
    protected final DuplexConverter<Object, T> converter;

    @SuppressWarnings("unchecked")
    public ConvertingCollection(Collection<?> collection, DuplexConverter<?, T> converterPair) {
        this.base = (Collection<Object>) collection;
        this.converter = (DuplexConverter<Object, T>) converterPair;
    }

    /**
     * Gets the converter used to translate items inside the collection
     * 
     * @return element converter
     */
    public DuplexConverter<Object, T> getElementConverter() {
        return this.converter;
    }

    /**
     * Gets the base collection that is used
     *
     * @return base collection
     */
    public Collection<Object> getBase() {
        return base;
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
    public void clear() {
        base.clear();
    }

    @Override
    public boolean add(T e) {
        return base.add(converter.convertReverse(e));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        return base.remove(converter.convertReverse((T) o));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return base.contains(converter.convertReverse((T) o));
    }

    @Override
    public Iterator<T> iterator() {
        return new ConvertingIterator<T>(base.iterator(), converter);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return base.containsAll(new ConvertingCollection<Object>(c, converter.reverse()));
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return base.addAll(new ConvertingCollection<Object>(c, converter.reverse()));
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return base.removeAll(new ConvertingCollection<Object>(c, converter.reverse()));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return MountiplexUtil.retainAll(this, c);
    }

    @Override
    public Object[] toArray() {
        return MountiplexUtil.toArray(this);
    }

    @Override
    public <K> K[] toArray(K[] array) {
        return MountiplexUtil.toArray(this, array);
    }
}
