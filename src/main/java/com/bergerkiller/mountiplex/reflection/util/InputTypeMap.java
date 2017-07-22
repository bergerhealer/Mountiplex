package com.bergerkiller.mountiplex.reflection.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;

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

    public void put(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        bin.values = Arrays.asList(value);
        bin.clearCache();
    }

    public boolean amend(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        if (bin.values.isEmpty()) {
            bin.values = Arrays.asList(value);
            bin.clearCache();
            return true;
        } else {
            return false;
        }
    }

    public boolean amendAll(TypeDeclaration type, Collection<T> values) {
        Bin bin = getBin(type);
        if (bin.values.isEmpty()) {
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
            bin = new Bin(type);
            for (Entry<TypeDeclaration, Bin> entry : map.entrySet()) {
                if (entry.getKey().isInstanceOf(type)) {
                    entry.getValue().link(bin);
                } else if (type.isInstanceOf(entry.getKey())) {
                    bin.link(entry.getValue());
                }
            }
            map.put(type, bin);
        }
        return bin;
    }

    private class Bin implements Comparable<Bin> {
        public final TypeDeclaration type;
        public Collection<T> values = Collections.emptyList();
        private final ArrayList<Bin> parents = new ArrayList<Bin>(1);
        private final ArrayList<Bin> children = new ArrayList<Bin>(1);
        private ArrayList<T> cache = null;

        public Bin(TypeDeclaration type) {
            this.type = type;
        }

        public final void link(Bin other) {
            this.parents.add(other);
            Collections.sort(this.parents);
            other.children.add(this);
            other.clearCache();
        }

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

        @Override
        public int compareTo(Bin o) {
            if (o.type.equals(this.type)) {
                return 0;
            } else if (this.type.isAssignableFrom(o.type)) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}
