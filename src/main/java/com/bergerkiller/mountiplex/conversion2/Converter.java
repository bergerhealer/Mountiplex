package com.bergerkiller.mountiplex.conversion2;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public abstract class Converter<I, O> {
    public final TypeDeclaration input;
    public final TypeDeclaration output;

    public Converter(Class<I> input, Class<O> output) {
        this(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    public Converter(TypeDeclaration input, TypeDeclaration output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Converts the input value to the converted output.
     * If conversion fails, null is returned instead.
     * 
     * @param value to be converted
     * @return converted output value, null on failure
     */
    public abstract O convert(I value);

    /**
     * Converts the input value to the converted output.
     * If conversion fails, the default value is returned instead.
     * 
     * @param value to convert
     * @param defaultValue to return on failure
     * @return converted result
     */
    @SuppressWarnings("unchecked")
    public final O convert(I value, O defaultValue) {
        O result = null;
        if (value != null) {
            result = convert(value);
        }
        if (result == null) {
            result = defaultValue;
        }
        if (result == null && this.output.type.isPrimitive()) {
            result = (O) BoxedType.getDefaultValue(this.output.type);
        }
        return result;
    }

    /**
     * Gets whether this Converter is lazy. A lazy converter is used as a last resort,
     * when no other converters exist to perform a conversion. If a converter converts
     * from a very common type, such as Object, it should be made lazy to prevent the converter
     * from being used everywhere.<br>
     * <br>
     * For example, a common conversion is to convert an Object to a String using {@link Object#toString()}.
     * This converter is lazy to make sure other types, such as Integer, don't end up converted to a String
     * when parsing to another type.
     * 
     * @return True if lazy, False if not
     */
    public boolean isLazy() {
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + input.toString() + " -> " + output.toString() + "]";
    }
}
