package com.bergerkiller.mountiplex.conversion.builtin;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps a Consumer, performing conversion of the accepted input value before
 * passing it to the original consumer.
 */
class ConsumerConversion {
    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Consumer.class)) {
                    // Consumer<T> -> Consumer<R>
                    converters.add(new ConsumerConverter(output));
                }
            }
        });
    }

    private static final class ConsumerConverter extends InputConverter<Consumer<?>> {
        private final TypeDeclaration outputElementType;

        public ConsumerConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Consumer.class), output);
            this.outputElementType = output.getGenericType(0);
        }

        @Override
        public Converter<?, Consumer<?>> getConverter(TypeDeclaration input) {
            TypeDeclaration inputElementType = input.castAsType(Consumer.class).getGenericType(0);

            // Note: inverse, because we modify argument not result
            Converter<?, ?> elementConverter = Conversion.find(outputElementType, inputElementType);

            if (elementConverter != null) {
                return new ConsumerConverterMapper(input, output, elementConverter);
            } else {
                return null;
            }
        }
    }

    private static final class ConsumerConverterMapper extends Converter<Consumer<?>, Consumer<?>> {
        private final Converter<?, ?> _elementConverter;

        public ConsumerConverterMapper(TypeDeclaration input, TypeDeclaration output, Converter<?, ?> converter) {
            super(input, output);
            this._elementConverter = converter;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Consumer<?> convertInput(Consumer<?> value) {
            final Consumer<Object> valueUnsafe = (Consumer<Object>) value;
            return (Consumer<Object>) o -> valueUnsafe.accept(_elementConverter.apply(o));
        }
    }
}
