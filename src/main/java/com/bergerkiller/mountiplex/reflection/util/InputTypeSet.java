package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A smart set of types that keeps track of all extensions and implementations of types added.
 * For example, when a Collection type is added to this set, ArrayList, List and all other
 * types that extend or implement Collection will pass the {@link #contains(TypeDeclaration)} check.
 * Adding ArrayList does not make its super types, such as List or Collection, pass.
 */
public class InputTypeSet {
    private final InputTypeMap<Object> map = new InputTypeMap<Object>();

    /**
     * Adds a new type to this set. This type, and all types that
     * extend or implement it, will from now on result in <i>true</i> when querying
     * {@link #contains(TypeDeclaration)}
     * 
     * @param type to add
     */
    public void add(TypeDeclaration type) {
        map.put(type, new Object());
    }

    /**
     * Checks whether a type, or one of the types it extends or implements,
     * are contained in this Input Type Set.
     * 
     * @param type to check
     * @return True if contained, False if not
     */
    public boolean contains(TypeDeclaration type) {
        return map.containsKey(type);
    }

    /**
     * Clears all types added to this set
     */
    public void clear() {
        map.clear();
    }
}
