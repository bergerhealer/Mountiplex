package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.List;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class ToStringConversion {
    public static void register() {
        //if (true) return;
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(TypeDeclaration outputType, List<Converter<?, ?>> converters) {
                if (!outputType.type.equals(String.class)) {
                    return;
                }

                // Fallback: Object.toString()
                converters.add(new Converter<Object, String>(Object.class, String.class) {
                    @Override
                    public String convert(Object value) {
                        return value.toString();
                    }

                    @Override
                    public boolean isLazy() {
                        return true;
                    }
                });
            }
        });
    }
}
