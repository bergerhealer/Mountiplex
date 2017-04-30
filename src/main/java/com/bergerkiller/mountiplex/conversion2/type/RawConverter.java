package com.bergerkiller.mountiplex.conversion2.type;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public abstract class RawConverter extends Converter<Object, Object> {

    public RawConverter(Class<?> input, Class<?> output) {
        super(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    public RawConverter(TypeDeclaration input, TypeDeclaration output) {
        super(input, output);
    }

    @Override
    public abstract Object convertInput(Object value);
}
