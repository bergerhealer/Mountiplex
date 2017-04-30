package com.bergerkiller.mountiplex.conversion.type;

import java.util.List;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class ListConverter extends Converter<List<?>> {

    public ListConverter() {
        super(List.class);
    }

    @Override
    public List<?> convert(Object value, List<?> def) {
        
        
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Converter<List<?>> getConverter(TypeDeclaration input, TypeDeclaration output) {
        
        
        return null;
    }

    @Override
    public boolean isCastingSupported() {
        return false;
    }

    @Override
    public boolean isRegisterSupported() {
        return true;
    }

}
