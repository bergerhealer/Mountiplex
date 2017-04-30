package com.bergerkiller.mountiplex.conversion2.type;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Special type of converter that can also perform the same conversion in reverse,
 * turning an output type into an input type.
 * 
 * @param <A> type A
 * @param <B> type B
 */
public abstract class DuplexConverter<A, B> extends Converter<A, B> {
    private final DuplexConverter<B, A> reverse;

    public DuplexConverter(Class<A> typeA, Class<B> typeB) {
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
    public abstract B convert(A value);

    /**
     * Performs the reversed conversion of {@link #convert(value))}
     * 
     * @param value to be converted (B)
     * @return reverse conversed value (A)
     */
    public abstract A convertReverse(B value);

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
    public static <A, B> DuplexConverter<A, B> create(Converter<A, B> converter, Converter<B, A> reverse) {
        if (converter instanceof DuplexConverter) {
            DuplexConverter<A, B> dupl = (DuplexConverter<A, B>) converter;
            if (dupl.reverse() == reverse) {
                return dupl;
            }
        }
        return new DuplexAdapter<A, B>(converter, reverse);
    }

    private final class ReverseDuplexConverter extends DuplexConverter<B, A> {

        public ReverseDuplexConverter() {
            super(DuplexConverter.this.output, DuplexConverter.this.input, DuplexConverter.this);
        }

        @Override
        public A convert(B value) {
            return DuplexConverter.this.convertReverse(value);
        }

        @Override
        public B convertReverse(A value) {
            return DuplexConverter.this.convert(value);
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
        public B convert(A value) {
            return converter.convert(value);
        }

        @Override
        public A convertReverse(B value) {
            return reverse.convert(value);
        }
    }
}
