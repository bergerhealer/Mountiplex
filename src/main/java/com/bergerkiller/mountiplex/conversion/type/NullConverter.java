package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * A converter that always fails
 */
@Deprecated
@SuppressWarnings("rawtypes")
public class NullConverter extends Converter {

    @SuppressWarnings("unchecked")
    public NullConverter() {
        super(Object.class);
    }

    @Override
    public Object convert(Object value, Object def) {
        return def;
    }

    @Override
    public boolean isCastingSupported() {
        return false;
    }

    @Override
    public boolean isRegisterSupported() {
        return false;
    }

}
