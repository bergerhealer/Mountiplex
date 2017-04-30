package com.bergerkiller.mountiplex.conversion2;

import java.util.List;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public interface ConverterProvider {

    /**
     * Loads a list with converters that can be used to convert to a given output type.
     * If the output type is not supported, this provider should leave the list empty.
     * 
     * @param outputType to be converted to
     * @param converters that can be used to convert to the Output Type
     */
    public void getConverters(TypeDeclaration outputType, List<Converter<?, ?>> converters);
}
