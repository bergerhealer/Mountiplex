package com.bergerkiller.mountiplex.conversion;

/**
 * Represents a converter specified using the @Converter annotation.
 * TODO!
 *
 * @param <T>
 */
@Deprecated
public class AnnotatedConverter<T> extends Converter<T> {

    public AnnotatedConverter(Class<?> outputType) {
        super(outputType);
    }

    @Override
    public T convert(Object value, T def) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isCastingSupported() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isRegisterSupported() {
        // TODO Auto-generated method stub
        return false;
    }

}
