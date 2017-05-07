package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.Collection;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;

public class ToStringConversion {
    public static void register() {
        // Object -> String (lazy!)
        Conversion.registerConverter(new Converter<Object, String>(Object.class, String.class) {
            @Override
            public String convertInput(Object value) {
                return value.toString();
            }

            @Override
            public boolean isLazy() {
                return true;
            }
        });

        // CharSequence -> String
        Conversion.registerConverter(new Converter<CharSequence, String>(CharSequence.class, String.class) {
            @Override
            public String convertInput(CharSequence value) {
                return value.toString();
            }
        });

        // char[] -> String
        Conversion.registerConverter(new Converter<char[], String>(char[].class, String.class) {
            @Override
            public String convertInput(char[] value) {
                return String.copyValueOf((char[]) value);
            }
        });

        // Array -> String
        Conversion.registerConverter(new Converter<Object[], String>(Object[].class, String.class) {
            @Override
            public String convertInput(Object[] value) {
                Converter<?, String> elConverter = Conversion.find(value.getClass().getComponentType(), String.class);
                StringBuilder builder = new StringBuilder(value.length * 5);
                builder.append('[');
                for (int i = 0; i < value.length; i++) {
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(elConverter.convert(value[i], ""));
                }
                builder.append(']');
                return builder.toString();
            }
        });

        // Collection -> String
        Conversion.registerConverter(new Converter<Collection<?>, String>(Collection.class, String.class) {
            @Override
            public String convertInput(Collection<?> collection) {
                StringBuilder builder = new StringBuilder(collection.size() * 5);
                Converter<?, String> toStringConv = Conversion.find(String.class);
                boolean first = true;
                builder.append("[");
                for (Object element : collection) {
                    if (!first) {
                        first = false;
                        builder.append(", ");
                    }
                    builder.append(toStringConv.convert(element, "null"));
                }
                builder.append("]");
                return builder.toString();
            }
        });
    }
}
