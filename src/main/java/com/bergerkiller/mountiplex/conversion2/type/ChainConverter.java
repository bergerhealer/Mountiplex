package com.bergerkiller.mountiplex.conversion2.type;

import java.util.List;

import com.bergerkiller.mountiplex.conversion2.Converter;

/**
 * Combines a chain of conversions into a single Converter
 */
public final class ChainConverter<I, O> extends Converter<I, O> {
    private final Converter<Object, Object>[] converters;

    @SuppressWarnings("unchecked")
    public ChainConverter(List<Converter<?, ?>> chain) {
        super(chain.get(0).input, chain.get(chain.size() - 1).output);
        this.converters = chain.toArray(new Converter[chain.size()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public O convertInput(I value) {
        Object v = value;
        for (Converter<Object, Object> converter : this.converters) {
            v = converter.convertInput(v);
            if (v == null) {
                break;
            }
        }
        return (O) v;
    }

}
