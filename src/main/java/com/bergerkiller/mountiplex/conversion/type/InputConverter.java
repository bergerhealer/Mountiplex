package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Automatically selects the right converter based on input to convert to a known output type.
 *
 * @param <T> output type
 */
public abstract class InputConverter <T> extends Converter<Object, T> {

    public InputConverter(TypeDeclaration input, TypeDeclaration output) {
        super(input, output);
    }

    public InputConverter(TypeDeclaration output) {
        super(TypeDeclaration.OBJECT, output);
    }

    /**
     * Gets the Converter used to convert null input, to the output type of this
     * InputConverter. If no such converter exists, null can be returned instead.
     * 
     * @return null input converter
     */
    public Converter<?, T> getNullConverter() {
        return null;
    }

    @Override
    public boolean acceptsNullInput() {
        return getNullConverter() != null;
    }

    /**
     * Gets the Converter used to convert from the input type specified, to the output type
     * of this InputConverter.
     * 
     * @param input type to be converted
     * @return converter to use, or null if not possible
     */
    public abstract Converter<?, T> getConverter(TypeDeclaration input);

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
    @SuppressWarnings("unchecked")
    public final T convertInput(Object value) {
        Converter<Object, T> converter;
        if (value == null) {
            converter = (Converter<Object, T>) this.getNullConverter();
        } else {
            // Shortcut: check if value is already an instance of our output type
            // This can save us a lot of cycles looking it up for simple Object <> Type conversions
            if (this.output.isAssignableFrom(value)) {
                return (T) value;
            }

            // Find the converter to convert from the value type to the output
            converter = (Converter<Object, T>) this.getConverter(TypeDeclaration.fromClass(value.getClass()));
        }
        if (converter != null) {
            return converter.convertInput(value);
        } else {
            return null;
        }
    }
}
