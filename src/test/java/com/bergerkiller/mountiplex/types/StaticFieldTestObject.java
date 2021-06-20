package com.bergerkiller.mountiplex.types;

/**
 * Declares a bunch of protected static final fields,
 * which require special VM magic to actually modify at runtime.
 */
public class StaticFieldTestObject {
    protected static final byte field_byte = 1;
    protected static final short field_short = 1;
    protected static final int field_int = 1;
    protected static final long field_long = 1;
    protected static final float field_float = 1.0f;
    protected static final double field_double = 1.0;
    protected static final char field_char = '1';
    protected static final boolean field_boolean = true;
    protected static final String field_string = "initial";
}
