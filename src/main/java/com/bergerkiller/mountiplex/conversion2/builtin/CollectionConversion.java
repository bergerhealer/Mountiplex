package com.bergerkiller.mountiplex.conversion2.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion2.type.InputConverter;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingCollection;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingList;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingSet;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class CollectionConversion {

    public static void register() {

        // Conversions to a generic List
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                // Only handle conversions to types that are Collections
                if (!Collection.class.isAssignableFrom(output.type)) {
                    return;
                }

                // Converting to a Collection type, from an unknown type
                if (output.type.equals(Collection.class)) {
                    converters.add(new CollectionConverter<Collection<?>>(output) {
                        @Override
                        protected Collection<?> convert(Collection<?> original) {
                            return original;
                        }

                        @Override
                        protected Collection<?> create(Collection<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingCollection<Object>(original, elementConverter);
                        }
                    });
                }

                // Converting to a List type, from an unknown type
                if (output.type.equals(List.class)) {
                    converters.add(new CollectionConverter<List<?>>(output) {
                        @Override
                        protected List<?> convert(Collection<?> original) {
                            return new ArrayList<Object>(original);
                        }

                        @Override
                        protected List<?> create(List<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingList<Object>(original, elementConverter);
                        }
                    });
                }

                // Converting to a Set type, from an unknown type
                if (output.type.equals(Set.class)) {
                    converters.add(new CollectionConverter<Set<?>>(output) {
                        @Override
                        protected Set<?> convert(Collection<?> original) {
                            return new HashSet<Object>(original);
                        }

                        @Override
                        protected Set<?> create(Set<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingSet<Object>(original, elementConverter);
                        }
                    });
                }
            }
        });
    }

    private static abstract class CollectionConverter <T extends Collection<?>> extends InputConverter<T> {

        public CollectionConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Collection.class), output);
        }

        protected abstract T convert(Collection<?> original);

        protected abstract T create(T original, DuplexConverter<Object, Object> elementConverter);

        @Override
        public final Converter<?, T> getConverter(TypeDeclaration input) {
            TypeDeclaration inputElementType = input.getGenericType(0);
            TypeDeclaration outputElementType = this.output.getGenericType(0);
            DuplexConverter<Object, Object> elementConverter = Conversion.findDuplex(inputElementType, outputElementType);
            if (elementConverter != null) {
                return new ElementConverter(input, this.output, elementConverter);
            } else {
                return null;
            }
        }

        private final class ElementConverter extends DuplexConverter<T, T> {
            private final DuplexConverter<Object, Object> elementConverter;
            private final boolean convertCollection;

            public ElementConverter(TypeDeclaration input, TypeDeclaration output, DuplexConverter<Object, Object> elementConverter) {
                super(input, output);
                this.elementConverter = elementConverter;
                this.convertCollection =  !output.type.equals(input.type);
            }

            @Override
            public final T convertInput(T value) {
                if (this.convertCollection) {
                    value = CollectionConverter.this.convert(value);
                }
                return CollectionConverter.this.create(value, elementConverter);
            }

            @Override
            public final T convertOutput(T value) {
                T result = CollectionConverter.this.create(value, elementConverter.reverse());
                if (this.convertCollection) {
                    result = CollectionConverter.this.convert(result);
                }
                return result;
            }
        }
    };

}
