package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

/**
 * Special type of converter that can also perform the same conversion in reverse,
 * turning an output type into an input type.
 * 
 * @param <A> type A
 * @param <B> type B
 */
public abstract class DuplexConverter<A, B> extends Converter<A, B> {
    protected DuplexConverter<B, A> reverse;

    public DuplexConverter(Class<?> typeA, Class<?> typeB) {
        super(typeA, typeB);
        this.reverse = new ReverseDuplexConverter();
    }

    public DuplexConverter(TypeDeclaration typeA, TypeDeclaration typeB) {
        super(typeA, typeB);
        this.reverse = new ReverseDuplexConverter();
    }

    protected DuplexConverter(TypeDeclaration typeA, TypeDeclaration typeB, DuplexConverter<B, A> reverse) {
        super(typeA, typeB);
        this.reverse = reverse;
    }

    /**
     * Performs the conversion from input type A to output type B
     * 
     * @param value to be converted (A)
     * @return converted value (B)
     */
    @Override
    public abstract B convertInput(A value);

    /**
     * Performs the conversion from output type B to input type A.
     * 
     * @param value to be converted (B)
     * @return reverse conversed value (A)
     */
    public abstract A convertOutput(B value);

    /**
     * Performs the reversed conversion of {@link #convert(value))}.
     * Returns null on failure.
     * 
     * @param value to be converted (B)
     * @return converted value (A)
     */
    @SuppressWarnings("unchecked")
    public final A convertReverse(Object value) {
        A result = null;
        if (value != null) {
            Class<?> outputType = this.output.type;
            if (this.output.isPrimitive) {
                outputType = BoxedType.getBoxedType(outputType);
            }
            if (outputType.isAssignableFrom(value.getClass())) {
                result = this.convertOutput((B) value);
            }
        } else if (this.acceptsNullOutput()) {
            result = this.convertOutput(null);
        }
        if (result == null && this.input.isPrimitive) {
            result = (A) BoxedType.getDefaultValue(this.input.type);
        }
        return result;
    }

    /**
     * Performs the reversed conversion of {@link #convert(value, defaultValue))}
     * 
     * @param value to be converted (B)
     * @param defaultValue to return on failure
     * @return converted value (A)
     */
    @SuppressWarnings("unchecked")
    public final A convertReverse(Object value, A defaultValue) {
        A result = null;
        if (value != null) {
            Class<?> outputType = this.output.type;
            if (this.output.isPrimitive) {
                outputType = BoxedType.getBoxedType(outputType);
            }
            if (outputType.isAssignableFrom(value.getClass())) {
                result = this.convertOutput((B) value);
            }
        } else if (this.acceptsNullOutput()) {
            result = this.convertOutput(null);
        }
        if (result == null) {
            result = defaultValue;
        }
        if (result == null && this.input.isPrimitive) {
            result = (A) BoxedType.getDefaultValue(this.input.type);
        }
        return result;
    }

    /**
     * Gets whether <i>null</i> is allowed as input to the reverse of this converter.
     * By default <i>false</i>, indicating it should return <i>null</i> without
     * performing conversion.
     * 
     * @return True if <i>null</i> is a valid output-input to this converter, False if not
     */
    public boolean acceptsNullOutput() {
        return false;
    }

    /**
     * Gets the reversed version of this Duplex Converter
     * 
     * @return reversed duplex converter
     */
    public final DuplexConverter<B, A> reverse() {
        return this.reverse;
    }

    /**
     * Creates a Duplex Converter by combining a Converter with its reversed version
     * 
     * @param converter for converting from A to B
     * @param reverse converter for converting from B back to A
     * @return duplex converter
     */
    public static <A, B> DuplexConverter<A, B> pair(Converter<?, B> converter, Converter<?, A> reverse) {
        // Verify the converters are not null
        if (converter == null || reverse == null) {
            return null;
        }

        // Verify that the output of one converter can be assigned to the other, and vice-versa
        // This check is very important, because an out-of-control duplex converter can wreak havoc
        if (!converter.output.isInstanceOf(reverse.input)) {
            throw new RuntimeException("Converter output of " + converter.toString() +
                    " can not be assigned to the input of " + reverse.toString());
        }
        if (!reverse.output.isInstanceOf(converter.input)) {
            throw new RuntimeException("Reverse converter output of " + converter.toString() +
                    " can not be assigned to the input of " + reverse.toString());
        }

        // Eliminate null converters
        if (isNullConverter(converter)) {
            if (isNullConverter(reverse)) {
                return createNull(converter.output);
            } else if (reverse instanceof CastingConverter) {
                return new DuplexCastingAdapter<B, A>(reverse.output, converter.output).reverse();
            } else {
                return new DuplexHalfAdapter<B, A>(reverse, converter.output).reverse();
            }
        }
        if (isNullConverter(reverse)) {
            if (converter instanceof CastingConverter) {
                return new DuplexCastingAdapter<A, B>(converter.output, reverse.output);
            } else {
                return new DuplexHalfAdapter<A, B>(converter, reverse.output);
            }
        }

        // If the converter already is a duplex converter, do not create a new one
        if (converter instanceof DuplexConverter) {
            @SuppressWarnings("unchecked")
            DuplexConverter<A, B> dupl = (DuplexConverter<A, B>) converter;
            if (dupl.reverse() == reverse) {
                return dupl;
            }
        }

        // If both converters are annotated, create a new custom type that prevents the converter in between
        if (converter instanceof AnnotatedConverter && reverse instanceof AnnotatedConverter) {
            AnnotatedConverter a = (AnnotatedConverter) converter;
            AnnotatedConverter b = (AnnotatedConverter) reverse;
            return new DuplexAnnotatedConverter<A, B>(a, b);
        }

        // Fallback: An adapter that calls the convert() method on the converters
        return new DuplexAdapter<A, B>(converter, reverse);
    }

    /**
     * Creates a new duplex null converter, where both input<>output return the same input value
     * 
     * @param typeA input type
     * @param typeB output type
     * @return duplex null converter
     */
    @SuppressWarnings("unchecked")
    public static <A, B> DuplexConverter<A, B> createNull(TypeDeclaration type) {
        return (DuplexConverter<A, B>) new DuplexNullConverter<Object>(type);
    }

    private static boolean isNullConverter(Converter<?, ?> converter) {
        return (converter instanceof NullConverter) || (converter instanceof DuplexNullConverter);
    }

    private final class ReverseDuplexConverter extends DuplexConverter<B, A> {

        public ReverseDuplexConverter() {
            super(DuplexConverter.this.output, DuplexConverter.this.input, DuplexConverter.this);
        }

        @Override
        public A convertInput(B value) {
            return DuplexConverter.this.convertOutput(value);
        }

        @Override
        public B convertOutput(A value) {
            return DuplexConverter.this.convertInput(value);
        }

        @Override
        public boolean acceptsNullInput() {
            return DuplexConverter.this.acceptsNullOutput();
        }

        @Override
        public boolean acceptsNullOutput() {
            return DuplexConverter.this.acceptsNullInput();
        }
    }

    private static final class DuplexAdapter<A, B> extends DuplexConverter<A, B> {
        private final Converter<A, B> input_converter;
        private final Converter<B, A> output_converter;

        public DuplexAdapter(Converter<?, B> converter, Converter<?, A> reverse) {
            this(converter, reverse, null);
            this.reverse = new DuplexAdapter<B, A>(reverse, converter, this);
        }

        @SuppressWarnings("unchecked")
        private DuplexAdapter(Converter<?, B> input_converter, Converter<?, A> output_converter, DuplexAdapter<B, A> reverseDuplex) {
            super(output_converter.output, input_converter.output, reverseDuplex);
            this.input_converter = (Converter<A, B>) input_converter;
            this.output_converter = (Converter<B, A>) output_converter;
        }

        @Override
        public B convertInput(A value) {
            return input_converter.convertInput(value);
        }

        @Override
        public A convertOutput(B value) {
            return output_converter.convertInput(value);
        }

        @Override
        public boolean acceptsNullInput() {
            return this.input_converter.acceptsNullInput();
        }

        @Override
        public boolean acceptsNullOutput() {
            return this.output_converter.acceptsNullInput();
        }

        @Override
        public String toString() {
            return "DuplexAdapter{" + this.output_converter.output.toString() + " <> " + this.input_converter.output.toString() + "}";
        }
    }

    // Converts only one way, the other way is a simple (down)cast
    private static final class DuplexHalfAdapter<A, B> extends DuplexConverter<A, B> {
        private final Converter<A, B> converter;
        private final TypeDeclaration reverseOutput;

        @SuppressWarnings("unchecked")
        public DuplexHalfAdapter(Converter<?, B> converter, TypeDeclaration reverseOutput) {
            super(reverseOutput, converter.output);
            this.converter = (Converter<A, B>) converter;
            this.reverseOutput = reverseOutput;
        }

        @Override
        public B convertInput(A value) {
            return converter.convertInput(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public A convertOutput(B value) {
            return (A) value;
        }

        @Override
        public boolean acceptsNullInput() {
            return this.converter.acceptsNullInput();
        }

        @Override
        public boolean acceptsNullOutput() {
            return true;
        }

        @Override
        public String toString() {
            return "DuplexHalfAdapter{" + this.reverseOutput.toString() + " <> " + this.converter.output.toString() + "}";
        }
    }

    // No conversion occurs at all, both input and output types can be assigned (Null Converters)
    private static final class DuplexNullConverter<A> extends DuplexConverter<A, A> {

        public DuplexNullConverter(TypeDeclaration type) {
            super(type, type, null);
            this.reverse = this;
        }

        @Override
        public A convertInput(A value) {
            return value;
        }

        @Override
        public A convertOutput(A value) {
            return value;
        }

        @Override
        public boolean acceptsNullInput() {
            return true;
        }

        @Override
        public boolean acceptsNullOutput() {
            return true;
        }

        @Override
        public String toString() {
            return "DuplexNullConverter{" + this.input + "}";
        }
    }

    // Casts a value up and down, a duplex combination of a CastingConverter and NullConverter
    private static final class DuplexCastingAdapter<A, B> extends DuplexConverter<A, B> {

        public DuplexCastingAdapter(TypeDeclaration typeA, TypeDeclaration typeB) {
            super(typeA, typeB);
        }

        @Override
        @SuppressWarnings("unchecked")
        public B convertInput(A value) {
            if (output.isAssignableFrom(value)) {
                return (B) value;
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public A convertOutput(B value) {
            return (A) value;
        }

        @Override
        public boolean acceptsNullInput() {
            return false;
        }

        @Override
        public boolean acceptsNullOutput() {
            return true;
        }

        @Override
        public String toString() {
            return "DuplexCastingAdapter{" + this.input + " <> " + this.output.toString() + "}";
        }
    }

    private static final class DuplexAnnotatedConverter<A, B> extends DuplexConverter<A, B> {
        private final Invoker<Object> converterInvoker;
        private final Invoker<Object> reverseInvoker;
        private final boolean converterAcceptNull;
        private final boolean reverseAcceptNull;

        public DuplexAnnotatedConverter(AnnotatedConverter converter, AnnotatedConverter reverse) {
            this(converter, reverse, null);
            this.reverse = new DuplexAnnotatedConverter<B, A>(reverse, converter, this);
        }

        public DuplexAnnotatedConverter(AnnotatedConverter converter, AnnotatedConverter reverse, DuplexAnnotatedConverter<B, A> reverseDupl) {
            super(reverse.output, converter.output, reverseDupl);
            this.converterInvoker = InitInvoker.proxy(this, "converterInvoker", converter.invoker);
            this.reverseInvoker = InitInvoker.proxy(this, "reverseInvoker", reverse.invoker);
            this.converterAcceptNull = converter.acceptsNullInput();
            this.reverseAcceptNull = reverse.acceptsNullInput();
        }

        @Override
        @SuppressWarnings("unchecked")
        public B convertInput(A value) {
            return (B) this.converterInvoker.invoke(null, value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public A convertOutput(B value) {
            return (A) this.reverseInvoker.invoke(null, value);
        }

        @Override
        public boolean acceptsNullInput() {
            return this.converterAcceptNull;
        }

        @Override
        public boolean acceptsNullOutput() {
            return this.reverseAcceptNull;
        }
    }

}
