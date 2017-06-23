package com.bergerkiller.mountiplex.conversion.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.conversion.util.ConvertingCollection;
import com.bergerkiller.mountiplex.conversion.util.ConvertingIterable;
import com.bergerkiller.mountiplex.conversion.util.ConvertingList;
import com.bergerkiller.mountiplex.conversion.util.ConvertingQueue;
import com.bergerkiller.mountiplex.conversion.util.ConvertingSet;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class CollectionConversion {

    public static void register() {

        // Conversions to a generic List
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(final TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(Iterable.class)) {
                    converters.add(new CollectionConverter<Iterable<?>>(output) {
                        @Override
                        protected Iterable<?> change(Collection<?> original) {
                            return original; // all collections are iterables
                        }

                        @Override
                        protected Iterable<?> create(Iterable<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingIterable<Object>(original, elementConverter);
                        }
                    });
                }

                // Converting to a Collection type, from an unknown type
                if (output.type.equals(Collection.class)) {
                    converters.add(new CollectionConverter<Collection<?>>(output) {
                        @Override
                        protected Collection<?> change(Collection<?> original) {
                            return original;
                        }

                        @Override
                        protected Collection<?> create(Collection<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingCollection<Object>(original, elementConverter);
                        }
                    });
                }

                // Converting to a Queue type, from an unknown type
                if (output.type.equals(Queue.class)) {
                    converters.add(new CollectionConverter<Queue<?>>(output) {
                        @Override
                        protected Queue<?> change(Collection<?> original) {
                            return new LinkedList<Object>(original);
                        }

                        @Override
                        protected Queue<?> create(Queue<?> original, DuplexConverter<Object, Object> elementConverter) {
                            return new ConvertingQueue<Object>(original, elementConverter);
                        }
                    });
                }

                // Converting to a List type, from an unknown type
                if (output.type.equals(List.class)) {
                    converters.add(new CollectionConverter<List<?>>(output) {
                        @Override
                        protected List<?> change(Collection<?> original) {
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
                        protected Set<?> change(Collection<?> original) {
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

    private static abstract class CollectionConverter <T extends Iterable<?>> extends InputConverter<T> {

        public CollectionConverter(TypeDeclaration output) {
            super(TypeDeclaration.fromClass(Collection.class), output);
        }

        protected abstract T change(Collection<?> original);

        protected abstract T create(T original, DuplexConverter<Object, Object> elementConverter);

        @Override
        public final Converter<?, T> getConverter(TypeDeclaration input) {
            // Converting something Input<Type> to Output<Type>
            TypeDeclaration inputElementType = input.getGenericType(0);
            TypeDeclaration outputElementType = this.output.getGenericType(0);
            if (inputElementType.equals(outputElementType)) {
                return new ElementConverter(input, this.output, DuplexConverter.createNull(inputElementType));
            }
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
                    if (value instanceof Collection) {
                        value = CollectionConverter.this.change((Collection<?>) value);
                    } else {
                        return null;
                    }
                }
                return CollectionConverter.this.create(value, elementConverter);
            }

            @Override
            public final T convertOutput(T value) {
                T result = CollectionConverter.this.create(value, elementConverter.reverse());
                if (this.convertCollection) {
                    if (result instanceof Collection) {
                        result = CollectionConverter.this.change((Collection<?>) result);
                    } else {
                        return null;
                    }
                }
                return result;
            }
        }
    }

}
