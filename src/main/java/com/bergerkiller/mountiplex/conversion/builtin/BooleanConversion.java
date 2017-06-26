package com.bergerkiller.mountiplex.conversion.builtin;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * Converts a Boolean type from/to numeric types as 0 and 1
 */
public class BooleanConversion {

    public static void register() {
        Conversion.registerConverter(new Converter<Boolean, Byte>(boolean.class, byte.class) {
            public Byte convertInput(Boolean value) { return value.booleanValue() ? (byte) 1 : (byte) 0; }
        });
        Conversion.registerConverter(new Converter<Boolean, Short>(boolean.class, short.class) {
            public Short convertInput(Boolean value) { return value.booleanValue() ? (short) 1 : (short) 0; }
        });
        Conversion.registerConverter(new Converter<Boolean, Integer>(boolean.class, int.class) {
            public Integer convertInput(Boolean value) { return value.booleanValue() ? 1 : 0; }
        });
        Conversion.registerConverter(new Converter<Boolean, Long>(boolean.class, long.class) {
            public Long convertInput(Boolean value) { return value.booleanValue() ? 1L : 0L; }
        });
        Conversion.registerConverter(new Converter<Number, Boolean>(Number.class, boolean.class) {
            @Override
            public Boolean convertInput(Number value) { return (value.intValue() == 0) ? false : true; }
        });
    }
}
