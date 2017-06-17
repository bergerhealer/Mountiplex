package com.bergerkiller.mountiplex.conversion.type;

import java.util.List;

import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * Combines a chain of conversions into a single Converter
 */
public final class ChainConverter<I, O> extends Converter<I, O> {
    private final Converter<Object, Object>[] converters;
    private final boolean acceptsNull;

    @SuppressWarnings("unchecked")
    public ChainConverter(List<Converter<?, ?>> chain) {
        super(chain.get(0).input, chain.get(chain.size() - 1).output);
        this.converters = chain.toArray(new Converter[chain.size()]);
        this.acceptsNull = this.converters[0].acceptsNullInput();
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

    @Override
    public boolean acceptsNullInput() {
        return this.acceptsNull;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString());
        result.append(" {\n");
        for (Converter<Object, Object> converter : this.converters) {
            result.append("  -> ").append(converter.toString()).append("\n");
        }
        result.append("}");
        return result.toString();
    }
}
