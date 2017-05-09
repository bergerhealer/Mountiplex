package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A converter that does nothing, simply returns the input value.
 * This is used as a link between converters that does absolutely nothing.
 * It is also critical for bridging unboxed and boxed types.
 * Null Converters should be removed from Conversion Chains.
 */
public final class NullConverter extends RawConverter {

    public NullConverter(Class<?> input, Class<?> output) {
        super(input, output);
    }

    public NullConverter(TypeDeclaration input, TypeDeclaration output) {
        super(input, output);
    }

    @Override
    public final Object convertInput(Object value) {
        return value;
    }
}
