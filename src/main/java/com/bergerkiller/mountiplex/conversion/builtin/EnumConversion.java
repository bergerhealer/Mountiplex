package com.bergerkiller.mountiplex.conversion.builtin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

@SuppressWarnings("rawtypes")
public class EnumConversion {

    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(TypeDeclaration outputType, List<Converter<?, ?>> converters) {
                if (!outputType.isInstanceOf(TypeDeclaration.ENUM)) {
                    return;
                }

                // Used down below
                final EnumStringCache cache = new EnumStringCache(outputType.type);
                if (cache.constants == null) {
                    return;
                }

                // Parsing an Enumeration from a Number (by ordinal)
                converters.add(new Converter<Number, Enum>(TypeDeclaration.fromClass(Number.class), outputType) {
                    @Override
                    public Enum convertInput(Number value) {
                        int idx = value.intValue();
                        if (idx >= 0 && idx < cache.constants.length) {
                            return cache.constants[idx];
                        } else {
                            return null;
                        }
                    }
                });

                // Parsing an Enumeration from a String
                converters.add(new Converter<String, Enum>(TypeDeclaration.fromClass(String.class), outputType) {
                    @Override
                    public Enum convertInput(String value) {
                        return cache.get(value);
                    }
                });

                // Parsing an Enumeration from a Boolean (String)
                converters.add(new Converter<Boolean, Enum>(TypeDeclaration.fromClass(Boolean.class), outputType) {
                    @Override
                    public Enum convertInput(Boolean value) {
                        return cache.get(value.toString());
                    }
                });
            }
        });
    }

    // caches information for converting from a String to an Enum type
    // fixes a detected performance issue with the very slow parseArray method
    private static class EnumStringCache {
        private final Map<String, Enum> values = new HashMap<String, Enum>();
        public final Enum<?>[] constants;

        public EnumStringCache(Class<?> type) {
            this.constants = (Enum<?>[]) type.getEnumConstants();
            if (this.constants != null) {
                for (Enum<?> constant : this.constants) {
                    String name = constant.name();
                    values.put(name.toLowerCase(Locale.ENGLISH), constant);
                    values.put(name.toUpperCase(Locale.ENGLISH), constant);
                    values.put(name, constant);
                }
            }
        }

        public Enum get(String key) {
            Enum result = this.values.get(key);
            if (result == null) {
                result = MountiplexUtil.parseArray(this.constants, key, null);
                if (result != null) {
                    this.values.put(key, result);
                }
            }
            return result;
        }

    }
}
