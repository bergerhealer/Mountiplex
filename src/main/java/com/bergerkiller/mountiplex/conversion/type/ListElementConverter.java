package com.bergerkiller.mountiplex.conversion.type;

import java.util.List;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterPair;
import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion2.util.ConvertingList;

/**
 * A generic converter that converts the elements back and forth inside a List
 */
@Deprecated
public class ListElementConverter<A, B> extends Converter<List<B>> {
	private final ConverterPair<A, B> pair;

	private ListElementConverter(ConverterPair<A, B> converterPair) {
	    super(List.class);
		this.pair = converterPair;
	}

	@Override
	public List<B> convert(Object value, List<B> def) {
		List<B> result = convert(value);
		if (result == null) {
			return def;
		} else {
			return result;
		}
	}

	@Override
	public List<B> convert(Object value) {
		List<?> inputList = CollectionConverter.toList.convert(value);
		if (inputList == null) {
			return null;
		} else {
			return new ConvertingList<B>(inputList, DuplexConverter.fromLegacy(pair));
		}
	}

	@Override
	public boolean isCastingSupported() {
		return false;
	}
	
    @Override
    public boolean isRegisterSupported() {
        return false;
    }

    /**
     * Reverses the element conversion
     *
     * @return new List Element Converter with element types swapped
     */
    public ListElementConverter<B, A> reverse() {
    	return create(pair.reverse());
    }

    /**
     * Creates a new list element converter
     * 
     * @param converterPair to use during conversion
     * @return List element converter
     */
    public static <A, B> ListElementConverter<A, B> create(ConverterPair<A, B> converterPair) {
    	return new ListElementConverter<A, B>(converterPair);
    }

    /**
     * Creates a new list element converter
     * 
     * @param convA converter in one direction
     * @param convB converter in the other direction
     * @return List element converter
     */
    public static <A, B> ListElementConverter<A, B> create(Converter<A> convA, Converter<B> convB) {
    	return new ListElementConverter<A, B>(convA.formPair(convB));
    }

}
