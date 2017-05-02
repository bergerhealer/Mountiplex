package com.bergerkiller.mountiplex.conversion2.builtin;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.conversion2.type.RawConverter;
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
                    TypeDeclaration elementType = output.getGenericType(0);
                    TypeDeclaration input = TypeDeclaration.fromClass(MountiplexUtil.getArrayType(elementType.type));
                    input = input.setGenericTypes(elementType.genericTypes);
                    converters.add(new Converter<Object[], List<?>>(input, output) {
                        @Override
                        public List<?> convertInput(Object[] value) {
                            return Arrays.asList(value);
                        }
                    });
                }
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
