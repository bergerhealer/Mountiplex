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

                // Parsing an Enumeration from a Number (by ordinal)
                converters.add(new Converter<Number, Enum>(TypeDeclaration.fromClass(Number.class), outputType) {
                    @Override
                    public Enum convertInput(Number value) {
                        int idx = value.intValue();
                        if (idx >= 0 && idx < cache.getConstants().length) {
                            return cache.getConstants()[idx];
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
        private final Class<?> _type;
        private Enum<?>[] _constants;

        public EnumStringCache(Class<?> type) {
            this._type = type;
            this._constants = null;
        }

        public Enum<?>[] getConstants() {
            if (this._constants == null) {
                this._constants = (Enum<?>[]) this._type.getEnumConstants();
                if (this._constants == null) {
                    this._constants = (Enum<?>[]) MountiplexUtil.createArray(this._type, 0);
                }
                for (Enum<?> constant : this._constants) {
                    String name = constant.name();
                    values.put(name.toLowerCase(Locale.ENGLISH), constant);
                    values.put(name.toUpperCase(Locale.ENGLISH), constant);
                    values.put(name, constant);
                }
            }
            return this._constants;
        }

        public Enum get(String key) {
            Enum result = this.values.get(key);
            if (result == null) {
                result = MountiplexUtil.parseArray(this.getConstants(), key, null);
                if (result != null) {
                    this.values.put(key, result);
                }
            }
            return result;
        }

    }
}
