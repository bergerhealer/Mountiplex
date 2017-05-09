package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.conversion.annotations.ConverterMethod;

public class AnnotatedConverters {

    @ConverterMethod(output="T extends com.bergerkiller.mountiplex.types.UniqueType")
    public static UniqueType getUniqueType(UniqueTypeWrap wrap) {
        return wrap.uniqueType;
    }

}
