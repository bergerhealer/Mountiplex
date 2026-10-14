package com.bergerkiller.mountiplex.conversion.builtin;

/**
 * Only public interface to register. Implementations are package-private.
 */
public class BuiltinTypeConversion {
    public static void register() {
        NumberConversion.register();
        ToStringConversion.register();
        EnumConversion.register();
        CollectionConversion.register();
        MapConversion.register();
        ArrayConversion.register();
        VoidTypeConverter.register();
        BooleanConversion.register();
        StreamConversion.register();
        PredicateConversion.register();
        ConsumerConversion.register();
        SupplierConversion.register();
    }
}
