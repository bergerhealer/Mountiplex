package com.bergerkiller.mountiplex.conversion.builtin;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * With voids, any value you put in turns into null
 */
public class VoidTypeConverter {

    public static void register() {
        Conversion.registerConverter(new Converter<Object, Object>(Object.class, void.class) {
            @Override
            public Object convertInput(Object value) {
                return null;
            }
        });
    }

}
