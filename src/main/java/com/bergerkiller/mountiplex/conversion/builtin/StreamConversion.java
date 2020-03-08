package com.bergerkiller.mountiplex.conversion.builtin;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Turning Stream<T> into Stream<R> using the .map() function
 */
public class StreamConversion {
    public static void register() {
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Stream.class)) {
                    TypeDeclaration elementType = output.getGenericType(0);

                    // Stream<T> -> Stream<R>
                    converters.add(new StreamConverter(output));

                    // Collection<T> -> Stream<T>
                    converters.add(new CollectionToStreamConverter<Object>(elementType));

                    // T -> Stream<T>
                    converters.add(new ElementToStreamConverter<Object>(elementType));
                }
                if (output.type.equals(List.class)) {
                    // Stream<T> -> List<T>
                    TypeDeclaration elementType = output.getGenericType(0);
                    converters.add(new StreamToListConverter<Object>(elementType));
                }
            }
        });
    }

    private static final class StreamConverter extends InputConverter<Stream<?>> {
        private final TypeDeclaration outputElementType;

        public StreamConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Stream.class), output);
            this.outputElementType = output.getGenericType(0);
        }

        @Override
        public Converter<?, Stream<?>> getConverter(TypeDeclaration input) {
            TypeDeclaration inputElementType = input.castAsType(Stream.class).getGenericType(0);
            Converter<?, ?> elementConverter = Conversion.find(inputElementType, outputElementType);
            if (elementConverter != null) {
                return new StreamConverterMapper(input, output, elementConverter);
            } else {
                return null;
            }
        }

        @Override
        public int getCost() {
            return 10;
        }
    }

    private static final class StreamConverterMapper extends Converter<Stream<?>, Stream<?>> {
        private final Converter<?, ?> _elementConverter;

        public StreamConverterMapper(TypeDeclaration input, TypeDeclaration output, Converter<?, ?> converter) {
            super(input, output);
            this._elementConverter = converter;
        }

        @Override
        public Stream<?> convertInput(Stream<?> value) {
            return value.map(this._elementConverter);
        }
    }

    private static final class ElementToStreamConverter<T> extends Converter<T, Stream<T>> {

        public ElementToStreamConverter(TypeDeclaration elementType) {
            super(elementType, TypeDeclaration.createGeneric(Stream.class, elementType));
        }

        @Override
        public Stream<T> convertInput(T value) {
            return MountiplexUtil.toStream(value);
        }
    }

    private static final class StreamToListConverter<T> extends Converter<Stream<T>, List<T>> {

        public StreamToListConverter(TypeDeclaration elementType) {
            super(TypeDeclaration.createGeneric(Stream.class, elementType),
                  TypeDeclaration.createGeneric(List.class, elementType));
        }

        @Override
        public List<T> convertInput(Stream<T> value) {
            return value.collect(Collectors.toList());
        }

        @Override
        public int getCost() {
            return 100;
        }
    }

    private static final class CollectionToStreamConverter<T> extends Converter<Collection<T>, Stream<T>> {

        public CollectionToStreamConverter(TypeDeclaration elementType) {
            super(TypeDeclaration.createGeneric(Collection.class, elementType),
                  TypeDeclaration.createGeneric(Stream.class, elementType));
        }

        @Override
        public Stream<T> convertInput(Collection<T> value) {
            return value.stream();
        }
    }
}
