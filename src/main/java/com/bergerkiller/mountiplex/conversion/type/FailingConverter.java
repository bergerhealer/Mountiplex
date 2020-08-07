package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Converter that doesn't convert, simply throws an exception instead.
 * 
 * @param <I> - Input type
 * @param <O> - Output type
 */
public final class FailingConverter<I, O> extends Converter<I, O> {
    private final String message;

    private FailingConverter(TypeDeclaration input, TypeDeclaration output, String message) {
        super(input, output);
        this.message = message;
    }

    @Override
    public O convertInput(I value) {
        throw new UnsupportedOperationException(this.message);
    }

    /**
     * Gets the message displayed for this failing converter used in the exception thrown when called
     * 
     * @param converter
     * @return message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Creates a new instance of the Failing Converter for converting the input to an output.
     * When conversion is attempted, an error is thrown.
     * 
     * @param input The input type of the conversion that failed
     * @param output The output type of the conversion that failed
     * @param message The message displayed when the converter is called
     * @return failing converter
     */
    public static <I, O> FailingConverter<I, O> create(TypeDeclaration input, TypeDeclaration output, String message) {
        return new FailingConverter<I, O>(input, output, message);
    }

    @SuppressWarnings("rawtypes")
    private static final FailingConverter UNINITIALIZED = new FailingConverter(
            TypeDeclaration.OBJECT,
            TypeDeclaration.OBJECT,
            "Converter has not been initialized");

    /**
     * Creates a Failing Converter for an uninitialized field.
     * 
     * @return failing converter
     */
    @SuppressWarnings("unchecked")
    public static <I, O> FailingConverter<I, O> uninitialized() {
        return UNINITIALIZED;
    }
}
