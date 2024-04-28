package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A mapping from Type to a collection of elements, where the contents of super types
 * show up in their extended classes and interfaces. For example, the contents retrieved
 * from type 'Number' show everything set for 'Number', and its extended classes, 'Long' and 'Float'.
 * 
 * @param <T> element type
 */
public class OutputTypeMap<T> extends TypeMap<T> implements Cloneable {

    public OutputTypeMap() {
    }

    protected OutputTypeMap(OutputTypeMap<T> map) {
        super(map);
    }

    @Override
    protected boolean isParentTypeOf(TypeDeclaration parent, TypeDeclaration child) {
        return parent.isInstanceOf(child);
    }

    @Override
    public OutputTypeMap<T> clone() {
        return new OutputTypeMap<>(this);
    }
}
