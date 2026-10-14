package com.bergerkiller.mountiplex.conversion.builtin;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

import java.util.List;
import java.util.function.Supplier;

/**
 * Wraps a Supplier, performing conversion of the returned supplied output value from
 * the original supplier.
 */
class SupplierConversion {
    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Supplier.class)) {
                    // Supplier<T> -> Supplier<R>
                    converters.add(new SupplierConverter(output));
                }
            }
        });
    }

    private static final class SupplierConverter extends InputConverter<Supplier<?>> {
        private final TypeDeclaration outputElementType;

        public SupplierConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Supplier.class), output);
            this.outputElementType = output.getGenericType(0);
        }

        @Override
        public Converter<?, Supplier<?>> getConverter(TypeDeclaration input) {
            TypeDeclaration returnedElementType = input.castAsType(Supplier.class).getGenericType(0);
            Converter<?, ?> returnValueConverter = Conversion.find(returnedElementType, outputElementType);
            if (returnValueConverter != null) {
                return new SupplierConverterMapper(input, output, returnValueConverter);
            } else {
                return null;
            }
        }
    }

    private static final class SupplierConverterMapper extends Converter<Supplier<?>, Supplier<?>> {
        private final Converter<?, ?> _returnValueConverter;

        public SupplierConverterMapper(TypeDeclaration input, TypeDeclaration output, Converter<?, ?> converter) {
            super(input, output);
            this._returnValueConverter = converter;
        }

        @Override
        public Supplier<?> convertInput(Supplier<?> value) {
            return (Supplier<Object>) () -> _returnValueConverter.convert(value.get());
        }
    }
}
