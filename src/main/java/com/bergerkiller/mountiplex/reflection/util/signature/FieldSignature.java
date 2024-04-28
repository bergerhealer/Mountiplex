package com.bergerkiller.mountiplex.reflection.util.signature;

/**
 * Contains the information to identify a particular field. Only stores the field name.
 * Can be used as a key in hashmap lookup tables.
 */
public final class FieldSignature {
    private final String name;

    public FieldSignature(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof FieldSignature) {
            return name.equals(((FieldSignature) o).name);
        } else {
            return false;
        }
    }
}
