package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A mapping from Type to a collection of elements, where the contents of extended types
 * show up in their supertype classes and interfaces. For example, the contents retrieved
 * from type 'Long' show everything set for 'Long', and its superclasses, 'Number' and 'Object'.
 * 
 * @param <T> element type
 */
public class InputTypeMap<T> extends TypeMap<T> implements Cloneable {

    public InputTypeMap() {
    }

    protected InputTypeMap(InputTypeMap<T> map) {
        super(map);
    }

    @Override
    protected boolean isParentTypeOf(TypeDeclaration parent, TypeDeclaration child) {
        return child.isInstanceOf(parent);
    }

    @Override
    public InputTypeMap<T> clone() {
        return new InputTypeMap<>(this);
    }
}
