package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class EnumConversion {

    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            @SuppressWarnings("rawtypes")
            public void getConverters(TypeDeclaration outputType, List<Converter<?, ?>> converters) {
                if (!outputType.isInstanceOf(TypeDeclaration.ENUM)) {
                    return;
                }

                // Used down below
                final Enum[] constants = (Enum[]) outputType.type.getEnumConstants();

                // Parsing an Enumeration from an Integer (by ordinal)
                converters.add(new Converter<Integer, Enum>(Integer.class, Enum.class) {
                    @Override
                    public Enum convertInput(Integer value) {
                        int idx = value.intValue();
                        if (idx >= 0 && idx < constants.length) {
                            return constants[idx];
                        } else {
                            return null;
                        }
                    }
                });

                // Parsing an Enumeration from a String
                converters.add(new Converter<String, Enum>(String.class, Enum.class) {
                    @Override
                    public Enum convertInput(String value) {
                        return MountiplexUtil.parseArray(constants, value, null);
                    }
                });
            }
        });
    }
}
