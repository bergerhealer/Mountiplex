package com.bergerkiller.mountiplex.conversion2.type;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Automatically selects the right converter based on input to convert to a known output type.
 *
 * @param <T> output type
 */
public abstract class InputConverter <T> extends Converter<Object, T> {

    public InputConverter(TypeDeclaration output) {
        super(TypeDeclaration.OBJECT, output);
    }

    /**
     * Gets the Converter used to convert from the input type specified, to the output type
     * of this InputConverter.
     * 
     * @param input type to be converted
     * @return converter to use, or null if not possible
     */
    public abstract Converter<Object, T> getConverter(TypeDeclaration input);

    /**
     * Gets the Converter used to convert from the input Class type specified, to the output
     * type of this InputConverter.
     * 
     * @param inputType to be converted
     * @return converter to use, or null if not possible
     */
    @SuppressWarnings("unchecked")
    public final <I> Converter<I, T> getConverter(Class<I> inputType) {
        return (Converter<I, T>) getConverter(TypeDeclaration.fromClass(inputType));
    }

    /**
     * Gets whether this Converter can convert to the output type of this InputConverter
     * from the input type specified
     * 
     * @param input type to check
     * @return True if it can be converted, False if not
     */
    public boolean canConvert(TypeDeclaration input) {
        return getConverter(input) != null;
    }

    /**
     * Gets whether this Converter can convert to the output type of this InputConverter
     * from the input Class type specified
     * 
     * @param inputType to check
     * @return True if it can be converted, False if not
     */
    public boolean canConvert(Class<?> inputType) {
        return getConverter(inputType) != null;
    }

    @Override
    public final T convert(Object value) {
        Converter<Object, T> converter = this.getConverter(TypeDeclaration.fromClass(value.getClass()));
        if (converter != null) {
            return converter.convert(value);
        } else {
            return null;
        }
    }

}
