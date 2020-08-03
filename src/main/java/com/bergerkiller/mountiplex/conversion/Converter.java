package com.bergerkiller.mountiplex.conversion;

import java.util.function.Function;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public abstract class Converter<I, O> implements Function<Object, O> {
    public final TypeDeclaration input;
    public final TypeDeclaration output;

    public Converter(Class<?> input, Class<?> output) {
        this(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    public Converter(TypeDeclaration input, TypeDeclaration output) {
        this.input = input;
        this.output = output;
    }

    /**
     * Converts the typed input value to the converted output.
     * If conversion fails, null is returned instead.
     * 
     * @param value to be converted
     * @return converted value, or null if failed
     */
    public abstract O convertInput(I value);

    /**
     * Converts the input value to the converted output.
     * If conversion fails, null is returned instead.
     * For primitive output types, their default value is returned. (0, false, etc.)
     * 
     * @param value to be converted
     * @return converted output value, null on failure
     */
    @SuppressWarnings("unchecked")
    public final O convert(Object value) {
        O result = null;
        if (value != null) {
            Class<?> inputType = this.input.type;
            if (inputType.isPrimitive()) {
                inputType = BoxedType.getBoxedType(inputType);
            }
            if (inputType.isAssignableFrom(value.getClass())) {
                result = this.convertInput((I) value);
            }
        } else if (this.acceptsNullInput()) {
            result = this.convertInput(null);
        }
        if (result == null && this.output.isPrimitive) {
            result = (O) BoxedType.getDefaultValue(this.output.type);
        }
        return result;
    }

    /**
     * Same as {@link #convert(Object)} to implement {@link Function} interface
     */
    @Override
    @SuppressWarnings("unchecked")
    public final O apply(Object value) {
        O result = null;
        if (value != null) {
            Class<?> inputType = this.input.type;
            if (inputType.isPrimitive()) {
                inputType = BoxedType.getBoxedType(inputType);
            }
            if (inputType.isAssignableFrom(value.getClass())) {
                result = this.convertInput((I) value);
            }
        } else if (this.acceptsNullInput()) {
            result = this.convertInput(null);
        }
        if (result == null && this.output.isPrimitive) {
            result = (O) BoxedType.getDefaultValue(this.output.type);
        }
        return result;
    }

    /**
     * Converts the input value to the converted output.
     * If conversion fails, the default value is returned instead.
     * For primitive output types, their default value is returned for null. (0, false, etc.)
     * 
     * @param value to convert
     * @param defaultValue to return on failure
     * @return converted result
     */
    @SuppressWarnings("unchecked")
    public final O convert(Object value, O defaultValue) {
        O result = null;
        if (value != null) {
            Class<?> inputType = this.input.type;
            if (inputType.isPrimitive()) {
                inputType = BoxedType.getBoxedType(inputType);
            }
            if (inputType.isAssignableFrom(value.getClass())) {
                result = this.convertInput((I) value);
            }
        } else if (this.acceptsNullInput()) {
            result = this.convertInput(null);
        }
        if (result == null) {
            result = defaultValue;
            if (result == null && this.output.isPrimitive) {
                result = (O) BoxedType.getDefaultValue(this.output.type);
            }
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

    public int getCost() {
        return isLazy() ? 100 : 1;
    }

    /**
     * Gets whether <i>null</i> is allowed as input to this converter.
     * By default <i>false</i>, indicating it should return <i>null</i> without
     * performing conversion.
     * 
     * @return True if <i>null</i> is a valid input to this converter, False if not
     */
    public boolean acceptsNullInput() {
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[cost=" + getCost() + ", " + input.toString() + " -> " + output.toString() + "]";
    }

    /**
     * Produces a converter when {@link #get()} is called
     * 
     * @param <I> Input type
     * @param <O> Output type
     */
    public static interface Supplier<I, O> {
        /**
         * Gets the Converter. Returns null if it could not be supplied.
         * 
         * @return Converter
         */
        Converter<I, O> get();

        /**
         * Gets a supplier for a constant value
         * 
         * @param converter
         * @return supplier that returns the converter
         */
        public static <I, O> Supplier<I, O> of(final Converter<I, O> converter) {
            return () -> converter;
        }
    }
}
