package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A disabled converter that throws a {@link UnsupportedOperationException} when called,
 * showing the message passed through in the constructor.<br>
 * <br>
 * This differs from the {@link FailingConverter} because it does not mean a fail condition
 * when this converter is used.
 * 
 * @param <I> input type
 * @param <O> output type
 */
public class DisabledConverter<I, O> extends Converter<I, O> {
    private final String _message;

    public DisabledConverter(Class<?> input, Class<?> output, String message) {
        super(input, output);
        this._message = message;
    }

    public DisabledConverter(TypeDeclaration input, TypeDeclaration output, String message) {
        super(input, output);
        this._message = message;
    }

    @Override
    public final O convertInput(I value) {
        throw new UnsupportedOperationException(this._message);
    }

    @Override
    public final boolean acceptsNullInput() {
        return true;
    }
}
