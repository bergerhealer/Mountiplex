package com.bergerkiller.mountiplex.conversion.util;

import java.util.Queue;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

public class ConvertingQueue<T> extends ConvertingCollection<T> implements Queue<T> {

    public ConvertingQueue(Queue<?> queue, DuplexConverter<?, T> converterPair) {
        super(queue, converterPair);
    }

    @Override
    public Queue<Object> getBase() {
        return (Queue<Object>) super.getBase();
    }

    @Override
    public boolean offer(T e) {
        return getBase().offer(converter.convertReverse(e));
    }

    @Override
    public T remove() {
        return converter.convert(getBase().remove());
    }

    @Override
    public T poll() {
        return converter.convert(getBase().poll());
    }

    @Override
    public T element() {
        return converter.convert(getBase().element());
    }

    @Override
    public T peek() {
        return converter.convert(getBase().peek());
    }
}
