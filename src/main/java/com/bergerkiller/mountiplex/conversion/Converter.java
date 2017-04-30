package com.bergerkiller.mountiplex.conversion;

import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.type.ArrayElementConverter;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;


/**
 * Represents a data type converter
 *
 * @param <T> output type
 */
public abstract class Converter<T> {
    private final TypeDeclaration _output;

    public Converter(Class<?> outputType) {
        this(new TypeDeclaration(ClassResolver.DEFAULT, outputType));
    }

    public Converter(ClassResolver resolver, String declaration) {
        this(new TypeDeclaration(resolver, declaration));
    }

    public Converter(TypeDeclaration outputType) {
        if (!outputType.isValid()) {
            this._output = null;
            MountiplexUtil.LOGGER.log(Level.WARNING, "Converter output declaration is invalid: " + outputType.toString(), new Exception());
        } else if (!outputType.isResolved()) {
            this._output = null;
            MountiplexUtil.LOGGER.log(Level.WARNING, "Converter output could not be resolved: " + outputType.toString(), new Exception());
        } else {
            this._output = outputType;
        }
    }

    /**
     * Converts the input value to the output type
     *
     * @param value to convert
     * @param def value to return when conversion fails
     * @return converted output type
     */
    public abstract T convert(Object value, T def);

    /**
     * Converts the input value to the output type<br>
     * If conversion fails, null is returned instead
     *
     * @param value to convert
     * @return converted output type
     */
    public T convert(Object value) {
        return convert(value, null);
    }

    /**
     * Gets the full generics-supporting Class type declaration returned by convert
     * 
     * @return output Class type declaration
     */
    public final TypeDeclaration getOutput() {
        return this._output;
    }

    /**
     * Gets whether this converter has any output at all.
     * If this returns False, the converter must be considered unusable.
     * 
     * @return True if output is set, False if not
     */
    public final boolean hasOutput() {
        return this._output != null;
    }

    /**
     * Gets the Class type returned by convert
     *
     * @return output Class type
     */
    @SuppressWarnings("unchecked")
    public Class<T> getOutputType() {
        return (Class<T>) this._output.type;
    }

    /**
     * Gets a child converter used to convert a narrowed input type to an output type.
     * Conversion type narrowing optimization can be performed here. If the converted type
     * uses generics, the generic type information should be further handled here.
     * By default this function returns 'this'.
     * 
     * @param input type to be converted
     * @return proper converter for this generic type
     */
    public Converter<T> getConverter(TypeDeclaration input, TypeDeclaration output) {
        return this;
    }

    /**
     * Checks whether the returned output value can be casted to another
     * type<br>
     * This should only be supported when the returned type can be an extension
     * of the output type<br>
     * Typically, interfaces do not support this, as they can conflict with
     * other converters<br><br>
     * <p/>
     * <b>Do not give a converter for multipurpose types this property! For
     * example, an Object converter would end up being used for all cases,
     * rendering isCastingSupported unusable globally.</b>
     *
     * @return True if casting is supported, False if not
     */
    public abstract boolean isCastingSupported();

    /**
     * Checks whether this converter supports registering in the Conversion
     * look-up table. If this converter produces something that has to do with
     * reading a field or method, and not actual conversion, this should be set
     * to False. If an unique type is produced on the output, not bound to generics,
     * this should be set to True to allow for automatic conversion to that type.
     *
     * @return True if Conversion table registration is enabled, False if not
     */
    public abstract boolean isRegisterSupported();

    /**
     * Creates a new ConverterPair with this converter as A and the specified
     * converter as B
     *
     * @param converterB to form a pair with
     * @return new ConverterPair
     */
    public <K> ConverterPair<T, K> formPair(Converter<K> converterB) {
    	return new ConverterPair<T, K>(this, converterB);
    }

    /**
     * Creates a new Converter that uses this base converter, but attempts to
     * cast the result to the type specified
     *
     * @param type to cast to
     * @return new Casting Converter
     */
    public <K> Converter<K> cast(Class<K> type) {
    	return new CastingConverter<K>(type, this);
    }

    /**
     * Applies this Conversion to a collection of elements and converts each element
     * 
     * @return new Array element converter
     */
    public Converter<T[]> toArray() {
    	return ArrayElementConverter.create(this);
    }
}
