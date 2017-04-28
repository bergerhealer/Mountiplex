package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.BasicConverter;

/**
 * Converter implementation for converting to enum Class types<br>
 * Dynamically constructed when finding a suitable converter
 *
 * @param <T> - type of Enum
 */
public class EnumConverter<T> extends BasicConverter<T> {

    public EnumConverter(Class<?> outputType) {
        super(outputType);
    }

    @Override
    public T convertSpecial(Object value, Class<?> valueType, T def) {
        String text = value.toString();
        if (text != null) {
            return MountiplexUtil.parseEnum(getOutputType(), text, def);
        } else {
            return def;
        }
    }

    @Override
    public boolean isCastingSupported() {
        return false;
    }
}
