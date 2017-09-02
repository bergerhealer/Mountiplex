package com.bergerkiller.mountiplex.conversion.builtin;

import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
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
                if (constants == null) {
                    return;
                }

                // Parsing an Enumeration from a Number (by ordinal)
                converters.add(new Converter<Number, Enum>(TypeDeclaration.fromClass(Number.class), outputType) {
                    @Override
                    public Enum convertInput(Number value) {
                        int idx = value.intValue();
                        if (idx >= 0 && idx < constants.length) {
                            return constants[idx];
                        } else {
                            return null;
                        }
                    }
                });

                // Parsing an Enumeration from a String
                converters.add(new Converter<String, Enum>(TypeDeclaration.fromClass(String.class), outputType) {
                    @Override
                    public Enum convertInput(String value) {
                        return MountiplexUtil.parseArray(constants, value, null);
                    }
                });

                // Parsing an Enumeration from a Boolean (String)
                converters.add(new Converter<Boolean, Enum>(TypeDeclaration.fromClass(Boolean.class), outputType) {
                    @Override
                    public Enum convertInput(Boolean value) {
                        return MountiplexUtil.parseArray(constants, value.toString(), null);
                    }
                });
            }
        });
    }
}
