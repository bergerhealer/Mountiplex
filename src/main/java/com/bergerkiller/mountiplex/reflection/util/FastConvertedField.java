package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;

/**
 * Wraps a FastField in a converter
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class FastConvertedField<T> {
    private final FastField<Object> field;
    private final DuplexConverter<Object, T> converter;

    public FastConvertedField(FastField<?> field, DuplexConverter<?, T> converter) {
        this.field = (FastField) field;
        this.converter = (DuplexConverter) converter;
    }

    /**
     * Gets the converted field value
     * 
     * @param instance, null for a static field
     * @return converted field value
     */
    public T get(Object instance) {
        return converter.convertInput(field.get(instance));
    }

    /**
     * Sets the field value to the value, converted to the field's type
     * 
     * @param instance, null for a static field
     * @param value to set to (is converted to the field's type)
     */
    public void set(Object instance, T value) {
        field.set(instance, converter.convertOutput(value));
    }

    // Bunch of wrappers for getting primitive types
    public byte getByte(Object instance) { return ((Byte) get(instance)).byteValue(); }
    public short getShort(Object instance) { return ((Short) get(instance)).shortValue(); }
    public int getInteger(Object instance) { return ((Integer) get(instance)).intValue(); }
    public long getLong(Object instance) { return ((Long) get(instance)).longValue(); }
    public char getCharacter(Object instance) { return ((Character) get(instance)).charValue(); }
    public float getFloat(Object instance) { return ((Float) get(instance)).floatValue(); }
    public double getDouble(Object instance) { return ((Double) get(instance)).doubleValue(); }

    // Bunch of wrappers for setting primitive types
    public void setByte(Object instance, byte value) { set(instance, (T) Byte.valueOf(value)); }
    public void setShort(Object instance, short value) { set(instance, (T) Short.valueOf(value)); }
    public void setInteger(Object instance, int value) { set(instance, (T) Integer.valueOf(value)); }
    public void setLong(Object instance, long value) { set(instance, (T) Long.valueOf(value)); }
    public void setCharacter(Object instance, char value) { set(instance, (T) Character.valueOf(value)); }
    public void setFloat(Object instance, float value) { set(instance, (T) Float.valueOf(value)); }
    public void setDouble(Object instance, double value) { set(instance, (T) Double.valueOf(value)); }
}
