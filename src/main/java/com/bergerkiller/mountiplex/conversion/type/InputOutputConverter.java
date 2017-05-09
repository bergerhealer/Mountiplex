package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public abstract class InputOutputConverter <I, O> extends InputConverter<O> {

    public InputOutputConverter(TypeDeclaration input, TypeDeclaration output) {
        super(input, output);
    }

    public abstract O convert(I value, TypeDeclaration input, TypeDeclaration output);

    @Override
    @SuppressWarnings("unchecked")
    public Converter<?, O> getConverter(TypeDeclaration input) {
        if (input.isInstanceOf(this.output)) {
            return (Converter<?, O>) new NullConverter(input, this.output);
        } else {
            return new ElementConverter(input, this.output);
        }
    }

    private final class ElementConverter extends Converter<I, O> {

        public ElementConverter(TypeDeclaration input, TypeDeclaration output) {
            super(input, output);
        }

        @Override
        public O convertInput(I value) {
            return InputOutputConverter.this.convert(value, this.input, this.output);
        }
    }
}
