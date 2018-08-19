package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.annotations.ConverterMethod;

/**
 * Special type that can only be converted to {@link UniqueType}.
 * Nothing else is possible.
 */
public class OneWayConvertableType {
    private final UniqueType _unique = new UniqueType();

    public OneWayConvertableType() {
        this._unique.name = "OneWayConvertableType::UniqueType";
    }

    public UniqueType getUnique() {
        return this._unique;
    }

    @ConverterMethod
    public static UniqueType oneWayConvertableToUnique(OneWayConvertableType oneWay) {
        return oneWay.getUnique();
    }

    static {
        Conversion.registerConverters(OneWayConvertableType.class);
    }
}
