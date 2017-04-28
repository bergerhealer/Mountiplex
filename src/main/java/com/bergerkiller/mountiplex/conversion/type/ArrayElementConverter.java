package com.bergerkiller.mountiplex.conversion.type;

import java.util.Collection;
import java.util.Iterator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.BasicConverter;
import com.bergerkiller.mountiplex.conversion.Converter;

/**
 * A generic converter that converts the elements inside of an array to another type
 */
public class ArrayElementConverter<T> extends BasicConverter<T[]> {
	private final Converter<T> elementConverter;

	@Override
	protected T[] convertSpecial(Object value, Class<?> valueType, T[] def) {
		Collection<?> input = CollectionConverter.toCollection.convert(value);
		if (input == null) {
			return def;
		}
		final int length = input.size();
		T[] result = MountiplexUtil.createArray(elementConverter.getOutputType(), length);
		Iterator<?> iter = input.iterator();
		for (int i = 0; i < length; i++) {
			result[i] = elementConverter.convert(iter.next());
		}
		return result;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private ArrayElementConverter(Converter<T> elementConverter) {
		super((Class) MountiplexUtil.getArrayType(elementConverter.getOutputType()));
		this.elementConverter = elementConverter;
	}

    @Override
    public boolean isRegisterSupported() {
        return false;
    }

    /**
     * Creates a new array element converter
     * 
     * @param converter to use during element conversion
     * @return Array element converter
     */
    public static <T> ArrayElementConverter<T> create(Converter<T> elementConverter) {
    	return new ArrayElementConverter<T>(elementConverter);
    }
}
