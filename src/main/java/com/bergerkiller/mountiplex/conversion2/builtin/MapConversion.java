package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.List;
import java.util.Map;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion2.type.InputConverter;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingMap;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class MapConversion {

    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Map.class)) {
                    converters.add(new MapConverter(output));
                }
            }
        });
    }

    private static final class MapConverter extends InputConverter<Map<?, ?>> {
        private final TypeDeclaration outputKeyType, outputValueType;

        public MapConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Map.class), output);
            TypeDeclaration mapOutput = output.castAsType(Map.class);
            outputKeyType = mapOutput.getGenericType(0);
            outputValueType = mapOutput.getGenericType(1);
        }

        @Override
        public final Converter<?, Map<?, ?>> getConverter(TypeDeclaration input) {
            TypeDeclaration mapInput = input.castAsType(Map.class);
            TypeDeclaration inputKeyType = mapInput.getGenericType(0);
            TypeDeclaration inputValueType = mapInput.getGenericType(1);
            boolean hasKeyConverter = !inputKeyType.equals(outputKeyType);
            boolean hasValueConverter = !inputValueType.equals(outputValueType);
            if (!hasKeyConverter && !hasValueConverter) {
                return null;
            }

            DuplexConverter<Object, Object> keyConverter, valueConverter;
            if (hasKeyConverter) {
                keyConverter = Conversion.findDuplex(inputKeyType, outputKeyType);
            } else {
                keyConverter = DuplexConverter.createNull(outputKeyType);
            }
            if (hasValueConverter) {
                valueConverter = Conversion.findDuplex(inputValueType, outputValueType);
            } else {
                valueConverter = DuplexConverter.createNull(outputValueType);
            }

            System.out.println("KEY: " + keyConverter);
            System.out.println("VALUE: " + valueConverter);
            if (keyConverter != null && valueConverter != null) {
                return new ElementConverter(input, this.output, keyConverter, valueConverter);
            } else {
                return null;
            }
        }

        private final class ElementConverter extends DuplexConverter<Map<?, ?>, Map<?, ?>> {
            private final DuplexConverter<Object, Object> keyConverter;
            private final DuplexConverter<Object, Object> valueConverter;

            public ElementConverter(TypeDeclaration input, TypeDeclaration output,
                    DuplexConverter<Object, Object> keyConverter,
                    DuplexConverter<Object, Object> valueConverter) {
                super(input, output);
                this.keyConverter = keyConverter;
                this.valueConverter = valueConverter;
            }

            @Override
            public final Map<?, ?> convertInput(Map<?, ?> value) {
                return new ConvertingMap<Object, Object>(value, keyConverter, valueConverter);
            }

            @Override
            public final Map<?, ?> convertOutput(Map<?, ?> value) {
                return new ConvertingMap<Object, Object>(value, keyConverter.reverse(), valueConverter.reverse());
            }
        }
    }
}
