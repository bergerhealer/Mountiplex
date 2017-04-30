package com.bergerkiller.mountiplex.reflection.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A mapping from Type to a collection of elements, where the contents of extended types
 * show up in their supertype classes and interfaces. For example, the contents retrieved
 * from type 'Long' show everything set for 'Long', and its superclasses, 'Number' and 'Object'.
 * 
 * @param <T> element type
 */
public class InputTypeMap<T> {
    private final HashMap<TypeDeclaration, Bin> map = new HashMap<TypeDeclaration, Bin>();

    public Collection<T> getAll(TypeDeclaration type) {
        return getBin(type).getCache();
    }

    public T get(TypeDeclaration type) {
        for (T value : getBin(type).getCache()) {
            return value;
        }
        return null;
    }

    public void putAll(TypeDeclaration type, Collection<T> values) {
        Bin bin = getBin(type);
        bin.values = values;
        bin.clearCache();
    }

    @SuppressWarnings("unchecked")
    public void put(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        bin.values = Arrays.asList(value);
        bin.clearCache();
    }

    @SuppressWarnings("unchecked")
    public boolean amend(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        if (bin.isEmpty()) {
            bin.values = Arrays.asList(value);
            bin.clearCache();
            return true;
        } else {
            return false;
        }
    }

    public boolean amendAll(TypeDeclaration type, Collection<T> values) {
        Bin bin = getBin(type);
        if (bin.isEmpty()) {
            bin.values = values;
            bin.clearCache();
            return true;
        } else {
            return false;
        }
    }

    public boolean containsKey(TypeDeclaration type) {
        return !getBin(type).isEmpty();
    }

    public void clear() {
        map.clear();
    }

    private final Bin getBin(TypeDeclaration type) {
        Bin bin = map.get(type);
        if (bin == null) {
            bin = new Bin();
            map.put(type, bin);
            for (TypeDeclaration superType : type.getSuperTypes()) {
                Bin superBin = getBin(superType);
                bin.parents.add(superBin);
                superBin.children.add(bin);
                superBin.clearCache();
            }
        }
        return bin;
    }

    private class Bin {
        public Collection<T> values = Collections.emptyList();
        public final ArrayList<Bin> parents = new ArrayList<Bin>(1);
        public final ArrayList<Bin> children = new ArrayList<Bin>(1);
        private ArrayList<T> cache = null;

        public void clearCache() {
            this.cache = null;
            for (Bin parent : this.children) {
                parent.clearCache();
            }
        }

        public boolean isEmpty() {
            return this.values.isEmpty() && getCache().isEmpty();
        }

        public Collection<T> getCache() {
            if (this.cache == null) {
                this.cache = new ArrayList<T>(this.values);
                for (Bin parent : this.parents) {
                    this.cache.addAll(parent.getCache());
                }
            }
            return this.cache;
        }
    }
}
