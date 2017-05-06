package com.bergerkiller.mountiplex.conversion2.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;

/**
 * Special type of converter that can also perform the same conversion in reverse,
 * turning an output type into an input type.
 * 
 * @param <A> type A
 * @param <B> type B
 */
public abstract class DuplexConverter<A, B> extends Converter<A, B> {
    private DuplexConverter<B, A> reverse;

    public DuplexConverter(Class<?> typeA, Class<?> typeB) {
        super(typeA, typeB);
        this.reverse = new ReverseDuplexConverter();
    }

    public DuplexConverter(TypeDeclaration typeA, TypeDeclaration typeB) {
        super(typeA, typeB);
        this.reverse = new ReverseDuplexConverter();
    }

    private DuplexConverter(TypeDeclaration typeA, TypeDeclaration typeB, DuplexConverter<B, A> reverse) {
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
     * Performs the reversed conversion of {@link #convert(value))}
     * 
     * @param value to be converted (B)
     * @return converted value (A)
     */
    @SuppressWarnings("unchecked")
    public final A convertReverse(Object value) {
        A result = null;
        Class<?> outputType = this.output.type;
        if (outputType.isPrimitive()) {
            outputType = BoxedType.getBoxedType(outputType);
        }
        if (value != null && outputType.isAssignableFrom(value.getClass())) {
            result = convertOutput((B) value);
        }
        if (result == null && this.input.type.isPrimitive()) {
            result = (A) BoxedType.getDefaultValue(this.input.type);
        }
        return result;
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
    public static <A, B> DuplexConverter<A, B> pair(Converter<A, B> converter, Converter<B, A> reverse) {
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

        // If the converter already is a duplex converter, do not create a new one
        if (converter instanceof DuplexConverter) {
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
        NullConverter conv = new NullConverter(type, type);
        return (DuplexConverter<A, B>) pair(conv, conv);
    }

    @Deprecated
    public static <A, B> DuplexConverter<A, B> fromLegacy(final com.bergerkiller.mountiplex.conversion.ConverterPair<A, B> pair) {
        return new DuplexConverter<A, B>(pair.getOutputTypeA(), pair.getOutputTypeB()) {
            @Override
            public B convertInput(A value) {
                return pair.convertB(value);
            }

            @Override
            public A convertOutput(B value) {
                return pair.convertA(value);
            }
        };
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
    }

    private static final class DuplexAdapter<A, B> extends DuplexConverter<A, B> {
        private final Converter<A, B> converter;
        private final Converter<B, A> reverse;

        public DuplexAdapter(Converter<A, B> converter, Converter<B, A> reverse) {
            super(reverse.output, converter.output);
            this.converter = converter;
            this.reverse = reverse;
        }

        @Override
        public B convertInput(A value) {
            return converter.convertInput(value);
        }

        @Override
        public A convertOutput(B value) {
            return reverse.convertInput(value);
        }
    }

    private static final class DuplexAnnotatedConverter<A, B> extends DuplexConverter<A, B> {
        private final Method converterMethod;
        private final Method reverseMethod;

        public DuplexAnnotatedConverter(AnnotatedConverter converter, AnnotatedConverter reverse) {
            super(reverse.output, converter.output);
            this.converterMethod = converter.method;
            this.reverseMethod = reverse.method;
        }

        @Override
        @SuppressWarnings("unchecked")
        public B convertInput(A value) {
            try {
                return (B) this.converterMethod.invoke(null, value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public A convertOutput(B value) {
            try {
                return (A) this.reverseMethod.invoke(null, value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }

    }

}
