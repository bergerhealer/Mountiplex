package com.bergerkiller.mountiplex.conversion.builtin;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

import java.util.List;
import java.util.function.Predicate;

/**
 * Wraps a Predicate, performing conversion of the tested input value before
 * passing it to the original predicate.
 */
class PredicateConversion {
    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Predicate.class)) {
                    // Predicate<T> -> Predicate<R>
                    converters.add(new PredicateConverter(output));
                }
            }
        });
    }

    private static final class PredicateConverter extends InputConverter<Predicate<?>> {
        private final TypeDeclaration outputElementType;

        public PredicateConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Predicate.class), output);
            this.outputElementType = output.getGenericType(0);
        }

        @Override
        public Converter<?, Predicate<?>> getConverter(TypeDeclaration input) {
            TypeDeclaration inputElementType = input.castAsType(Predicate.class).getGenericType(0);

            // Note: inverse, because we modify argument not result
            Converter<?, ?> elementConverter = Conversion.find(outputElementType, inputElementType);

            if (elementConverter != null) {
                return new PredicateConverterMapper(input, output, elementConverter);
            } else {
                return null;
            }
        }
    }

    private static final class PredicateConverterMapper extends Converter<Predicate<?>, Predicate<?>> {
        private final Converter<?, ?> _elementConverter;

        public PredicateConverterMapper(TypeDeclaration input, TypeDeclaration output, Converter<?, ?> converter) {
            super(input, output);
            this._elementConverter = converter;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Predicate<?> convertInput(Predicate<?> value) {
            final Predicate<Object> valueUnsafe = (Predicate<Object>) value;
            return (Predicate<Object>) o -> valueUnsafe.test(_elementConverter.apply(o));
        }
    }
}
