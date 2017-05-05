package com.bergerkiller.mountiplex.conversion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.type.EmptyConverter;
import com.bergerkiller.mountiplex.conversion.type.EnumConverter;
import com.bergerkiller.mountiplex.conversion.type.ObjectArrayConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;

/**
 * Tracks all the converters that are in use
 */
@Deprecated
public class ConverterRegistry {
    private static final Map<TypeDeclaration, Converter<Object>> convertersReg = new ConcurrentHashMap<TypeDeclaration, Converter<Object>>();
    private static final Map<TypeTuple, Converter<Object>> converters = new ConcurrentHashMap<TypeTuple, Converter<Object>>();

    /**
     * Registers all available static convertor constants found in the Class or
     * enum
     *
     * @param convertorConstants class container to register
     */
    public static void registerAll(Class<?> convertorConstants) {
        for (Object convertor : MountiplexUtil.getClassConstants(convertorConstants, Converter.class)) {
            if (convertor instanceof Converter) {
                register((Converter<?>) convertor);
            }
        }
    }

    /**
     * Registers a converter so it can be used to convert to the output type it
     * represents. If the converter does not support registration, it is ignored
     *
     * @param converter to register
     */
    @SuppressWarnings("unchecked")
    public static void register(Converter<?> converter) {
        if (!converter.hasOutput()) {
            return;
        }
        if (!converter.isRegisterSupported()) {
            return;
        }
        if (converter.getOutputType() == null) {
            return;
        }
        convertersReg.put(converter.getOutput(), (Converter<Object>) converter);
    }

    /**
     * Obtains the converter pair used to convert between the two type declarations specified
     * 
     * @param typeA type declaration
     * @param typeB type declaration
     * @return converter pair between typeA and typeB
     */
    public static ConverterPair<?, ?> getConverterPair(TypeDeclaration typeA, TypeDeclaration typeB) {
        return getConverter(typeA, typeB).formPair(getConverter(typeB, typeA));
    }

    /**
     * Obtains the converter used to convert to the type specified<br>
     * If none is available yet for the type, a new one is created
     *
     * @param type to convert to
     * @return converter
     */
    public static <T> Converter<T> getConverter(Class<T> type) {
        return getConverter(TypeDeclaration.OBJECT, TypeDeclaration.fromClass(type));
    }

    /**
     * Obtains the converter used to convert to the type specified<br>
     * If none is available yet for the type, a new one is created
     *
     * @param input type to be converted
     * @param output type to be converted to
     * @return converter
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Converter<T> getConverter(TypeDeclaration input, TypeDeclaration output) {
        TypeTuple key = new TypeTuple(input, output);
        Converter<T> converter = (Converter<T>) converters.get(key);
        if (converter == null) {
            // Find this converter in the conversion output type registry
            converter = (Converter<T>) convertersReg.get(output);

            // Handle standard Java types such as arrays and enumerations
            if (converter == null) {
                Class<?> type = output.type;
                if (type.isPrimitive()) {
                    type = (Class<T>) BoxedType.getBoxedType(type);
                }
                if (type.isArray()) {
                    // Maybe converting to an Object array of a certain component type?
                    // Note: Primitives are already dealt with and registered in the map
                    final Class<?> componentType = type.getComponentType();
                    if (!componentType.isPrimitive()) {
                        // Use the ObjectArrayConvertor to deal with this
                        converter = new ObjectArrayConverter(componentType);
                    }
                } else if (type.isEnum()) {
                    // Converting to an enum type - construct a new EnumConverter
                    converter = new EnumConverter<T>(type);
                } else {
                    // Maybe the requested type is an extension?
                    // If so, put a new casting converter in place to deal with it
                    for (Converter<Object> conv : convertersReg.values()) {
                        if (conv.isCastingSupported() && conv.getOutputType().isAssignableFrom(type)) {
                            converter = new CastingConverter(type, conv);
                            break;
                        }
                    }
                }
                // Resolve to the default casting-based converter if not found
                if (converter == null) {
                    converter = new EmptyConverter(type);
                }
            }

            // Process child-converters that can further handle the input as demanded
            converter = converter.getConverter(input, output);

            // Found. Put into map for faster look-up
            converters.put(key, (Converter<Object>) converter);
        }
        return (Converter<T>) converter;
    }

    /**
     * Converts an object to the given type using previously registered
     * converters
     *
     * @param value to convert
     * @param def value to return on failure (can not be null)
     * @return the converted value
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, T def) {
        return convert(value, (Class<T>) def.getClass(), def);
    }

    /**
     * Converts an object to the given type using previously registered
     * converters
     *
     * @param value to convert
     * @param type to convert to
     * @return the converted value, or null on failure
     */
    public static <T> T convert(Object value, Class<T> type) {
        return convert(value, type, null);
    }

    /**
     * Converts an object to the given type using previously registered
     * converters
     *
     * @param value to convert
     * @param type to convert to
     * @param def value to return on failure
     * @return the converted value
     */
    public static <T> T convert(Object value, Class<T> type, T def) {
        if (value == null) {
            return def;
        }
        final Class<?> valueType = value.getClass();
        if (type.isAssignableFrom(valueType)) {
            return type.cast(value);
        }
        return getConverter(type).convert(value, def);
    }

    private static final class TypeTuple {
        public final TypeDeclaration t1, t2;

        public TypeTuple(TypeDeclaration t1, TypeDeclaration t2) {
            this.t1 = t1;
            this.t2 = t2;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other instanceof TypeTuple) {
                TypeTuple tuple = (TypeTuple) other;
                return tuple.t1.equals(this.t1) && tuple.t2.equals(this.t2); 
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return (t1.hashCode() >> 1) + (t2.hashCode() >> 1);
        }
    }
}
