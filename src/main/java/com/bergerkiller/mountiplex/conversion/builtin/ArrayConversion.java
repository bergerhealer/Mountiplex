package com.bergerkiller.mountiplex.conversion.builtin;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.conversion.type.RawConverter;
import com.bergerkiller.mountiplex.conversion.util.ConvertingList;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;

public class ArrayConversion {
    public static void register() {
        // int[] <> Integer[] etc. Needed for proper boxing/unboxing of types
        for (Class<?> unboxedType : BoxedType.getUnboxedTypes()) {
            if (unboxedType.equals(void.class)) {
                continue;
            }

            Class<?> boxedType = BoxedType.getBoxedType(unboxedType);
            Class<?> unboxedArray = MountiplexUtil.getArrayType(unboxedType);
            Class<?> boxedArray = MountiplexUtil.getArrayType(boxedType);
            Conversion.registerConverter(new ArrayConverter(unboxedArray, boxedArray));
            Conversion.registerConverter(new ArrayConverter(boxedArray, unboxedArray));
        }

        // Type Object[] -> List<Object> allows for generic conversions to occur in the chain
        Conversion.registerProvider(new ConverterProvider() {
            @Override
            public void getConverters(TypeDeclaration output, List<Converter<?, ?>> converters) {
                if (output.type.equals(List.class)) {
                    final TypeDeclaration outputElementType = output.getGenericType(0);
                    TypeDeclaration input = TypeDeclaration.fromClass(MountiplexUtil.getArrayType(outputElementType.type));
                    input = input.setGenericTypes(outputElementType.genericTypes);
                    converters.add(new Converter<Object[], List<?>>(input, output) {
                        @Override
                        public List<?> convertInput(Object[] value) {
                            return Arrays.asList(value);
                        }
                    });

                    // Conversions from arrays of other types to a List
                    converters.add(new InputConverter<List<?>>(output) {
                        @Override
                        public Converter<?, List<?>> getConverter(TypeDeclaration input) {
                            if (!input.isArray()) {
                                return null;
                            }

                            final TypeDeclaration inputElementType = input.getComponentType();
                            final Converter<Object, Object> elementConverter = Conversion.find(inputElementType, outputElementType);
                            if (elementConverter == null) {
                                return null;
                            }

                            // Arrays like Object[] can be interfaced using Arrays.asList, so use that
                            // This can only be done for non-primitive elements and when a reverse converter is available
                            if (!inputElementType.isPrimitive()) {
                                Converter<Object, Object> reverseConverter = Conversion.find(outputElementType, inputElementType);
                                if (reverseConverter != null) {
                                    final DuplexConverter<Object, Object> duplexConverter = DuplexConverter.pair(elementConverter, reverseConverter);
                                    return new Converter<Object, List<?>>(input, this.output) {
                                        @Override
                                        public List<?> convertInput(Object value) {
                                            return new ConvertingList<Object>(Arrays.asList((Object[]) value), duplexConverter);
                                        }
                                    };
                                }
                            }

                            // Arrays like int[] or that lack a reverse converter can't easily be put into Arrays.asList
                            // Instead convert each element individually
                            // TODO: Perhaps a custom List type that can handle this?
                            return new Converter<Object, List<?>>(input, this.output) {
                                @Override
                                public List<?> convertInput(Object value) {
                                    int arrLen = Array.getLength(value);
                                    ArrayList<Object> result = new ArrayList<Object>(arrLen);
                                    for (int i = 0; i < arrLen; i++) {
                                        result.add(elementConverter.convert(Array.get(value, i)));
                                    }
                                    return result;
                                }
                            };
                        }

                        @Override
                        public boolean isLazy() {
                            return true;
                        }
                    });
                }
                if (output.type.isArray()) {
                    // Direct conversion from List<Type> to Type[]
                    if (output.type.isArray() && !output.type.getComponentType().isPrimitive()) {
                        TypeDeclaration elementType = TypeDeclaration.fromClass(output.type.getComponentType());
                        elementType = elementType.setGenericTypes(output.genericTypes);
                        TypeDeclaration input = TypeDeclaration.fromClass(List.class);
                        input = input.setGenericTypes(elementType);
                        converters.add(new Converter<List<?>, Object[]>(input, output) {
                            @Override
                            public Object[] convertInput(List<?> value) {
                                Object[] result = (Object[]) Array.newInstance(this.output.type.getComponentType(), value.size());
                                return value.toArray(result);
                            }
                        });
                    }

                    // Also handle conversion from Collection<Type1> -> Type2[]
                    // This will also handle strange conversions, such as Set<Type> to Type[]
                    TypeDeclaration collIn = TypeDeclaration.fromClass(Collection.class);
                    final TypeDeclaration outputElementType = output.getComponentType();
                    converters.add(new InputConverter<Object>(collIn, output) {
                        @Override
                        public Converter<?, Object> getConverter(TypeDeclaration input) {
                            if (!Collection.class.isAssignableFrom(input.type)) {
                                return null;
                            }

                            // Find element converter
                            final TypeDeclaration inputElementType = input.getGenericType(0);
                            final Converter<Object, Object> elementConverter = Conversion.find(inputElementType, outputElementType);
                            if (elementConverter == null) {
                                return null;
                            }

                            // Create final converter for List<Type1> to Type2[]
                            return new Converter<Collection<?>, Object>(input, this.output) {
                                @Override
                                public Object convertInput(Collection<?> value) {
                                    Object[] inArray = value.toArray(MountiplexUtil.createArray(inputElementType.type, value.size()));
                                    int arrSize = inArray.length;
                                    Object result = Array.newInstance(outputElementType.type, arrSize);
                                    for (int i = 0; i < inArray.length; i++) {
                                        Array.set(result, i, elementConverter.convert(inArray[i]));
                                    }
                                    return result;
                                }
                            };
                        }
                    });

                    // Conversions from TypeA[] to TypeB[]
                    TypeDeclaration input = TypeDeclaration.parse("Object[]");
                    final TypeDeclaration elementOutput = output.getComponentType();
                    converters.add(new InputConverter<Object>(input, output) {
                        @Override
                        public Converter<?, Object> getConverter(TypeDeclaration input) {
                            if (!input.type.isArray()) {
                                return null;
                            }

                            TypeDeclaration elementInput = input.getComponentType();
                            final Converter<Object, Object> elementConverter = Conversion.find(elementInput, elementOutput);
                            if (elementConverter == null) {
                                return null;
                            }

                            return new Converter<Object, Object>(input, output) {
                                @Override
                                public Object convertInput(Object value) {
                                    int arrSize = Array.getLength(value);
                                    Object result = Array.newInstance(elementOutput.type, arrSize);
                                    for (int i = 0; i < arrSize; i++) {
                                        Array.set(result, i, elementConverter.convert(Array.get(value, i)));
                                    }
                                    return result;
                                }
                            };
                        }
                    });
                }
            }
        });
    }

    private static final class ArrayConverter extends RawConverter {
        private final Converter<Object, Object> componentConverter;

        @SuppressWarnings("unchecked")
        public ArrayConverter(Class<?> input, Class<?> output) {
            super(input, output);
            this.componentConverter = (Converter<Object, Object>) Conversion.find(
                    input.getComponentType(), output.getComponentType());
        }

        @Override
        public Object convertInput(Object value) {
            int len = Array.getLength(value);
            Object result = Array.newInstance(this.output.type.getComponentType(), len);
            for (int i = 0; i < len; i++) {
                Array.set(result, i, componentConverter.convert(Array.get(value, i)));
            }
            return result;
        }
    }
}
