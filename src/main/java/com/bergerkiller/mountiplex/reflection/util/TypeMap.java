package com.bergerkiller.mountiplex.reflection.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public abstract class TypeMap<T> {
    private final HashMap<TypeDeclaration, Bin> map = new HashMap<TypeDeclaration, Bin>();

    /**
     * Gets the first value stored at a particular type
     * 
     * @param type to get at
     * @return value stored at this type, null if not stored
     */
    public T get(Class<?> type) {
        return get(TypeDeclaration.fromClass(type));
    }

    /**
     * Gets the first value stored at a particular type
     * 
     * @param type to get at
     * @return value stored at this type, null if not stored
     */
    public T get(TypeDeclaration type) {
        for (T value : getBin(type).getCache()) {
            return value;
        }
        return null;
    }

    /**
     * Gets all values stored at a particular type
     * 
     * @param type to get at
     * @return all values stored at this type (immutable and thread-safe)
     */
    public Collection<T> getAll(Class<?> type) {
        return getAll(TypeDeclaration.fromClass(type));
    }

    /**
     * Gets all values stored at a particular type
     * 
     * @param type to get at
     * @return all values stored at this type (immutable and thread-safe)
     */
    public Collection<T> getAll(TypeDeclaration type) {
        return getBin(type).getCache();
    }

    /**
     * Adds a value to the mapping of a particular type.
     * The value will be returned from future calls to {@link #getAll(TypeDeclaration)}
     * 
     * @param type to add at
     * @param value to add
     */
    public void add(Class<?> type, T value) {
        add(TypeDeclaration.fromClass(type), value);
    }

    /**
     * Adds a value to the mapping of a particular type.
     * The value will be returned from future calls to {@link #getAll(TypeDeclaration)}
     * 
     * @param type to add at
     * @param value to add
     */
    @SuppressWarnings("unchecked")
    public void add(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        if (bin.values.isEmpty()) {
            bin.values = Collections.singletonList(value);
        } else {
            T[] newValues = (T[]) new Object[bin.values.size() + 1];
            bin.values.toArray(newValues);
            newValues[newValues.length - 1] = value;
            bin.values = Arrays.asList(newValues);
        }
        bin.invalidateCache(1);
    }

    /**
     * Removes a value from the mapping of a particular type.
     * The value will no longer be returned from future calls to {@link #getAll(TypeDeclaration)}
     * 
     * @param type to remove at
     * @param value to remove
     */
    public void remove(Class<?> type, T value) {
        remove(TypeDeclaration.fromClass(type), value);
    }
    
    /**
     * Removes a value from the mapping of a particular type.
     * The value will no longer be returned from future calls to {@link #getAll(TypeDeclaration)}
     * 
     * @param type to remove at
     * @param value to remove
     */
    @SuppressWarnings("unchecked")
    public void remove(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        List<T> values = bin.values;
        int index = values.indexOf(value);
        if (index != -1) {
            int numValues = values.size();
            if (numValues == 1) {
                bin.values = Collections.emptyList();
            } else {
                T[] newValues = (T[]) new Object[numValues - 1];
                for (int i = 0; i < index; i++) {
                    newValues[i] = values.get(i);
                }
                for (int i = index + 1; i < numValues; i++) {
                    newValues[i - 1] = values.get(i);
                }
                bin.values = Arrays.asList(newValues);
            }
            bin.invalidateCache(-1);
        }
    }

    /**
     * Stores a single value at a particular type, replacing any original values
     * 
     * @param type to put at
     * @param value to put
     */
    public void put(Class<?> type, T value) {
        put(TypeDeclaration.fromClass(type), value);
    }

    /**
     * Stores a single value at a particular type, replacing any original values
     * 
     * @param type to put at
     * @param value to put
     */
    public void put(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        int sizeChange = 1 - bin.values.size();
        bin.values = Collections.singletonList(value);
        bin.invalidateCache(sizeChange);
    }

    /**
     * Stores multiple values at a particular type, replacing any original values
     * 
     * @param type to put at
     * @param values to put
     */
    public void putAll(Class<?> type, Collection<T> values) {
        putAll(TypeDeclaration.fromClass(type), values);
    }

    /**
     * Stores multiple values at a particular type, replacing any original values
     * 
     * @param type to put at
     * @param values to put
     */
    public void putAll(TypeDeclaration type, Collection<T> values) {
        Bin bin = getBin(type);
        int sizeChange = -bin.values.size();
        bin.values = MountiplexUtil.createUmodifiableList(values);
        sizeChange += bin.values.size();
        bin.invalidateCache(sizeChange);
    }

    /**
     * Puts a value at a particular type, only when that type not yet stores any values
     * 
     * @param type to put at
     * @param value to amend
     * @return true if the value was added
     */
    public boolean amend(TypeDeclaration type, T value) {
        Bin bin = getBin(type);
        if (bin.values.isEmpty()) {
            bin.values = Collections.singletonList(value);
            bin.invalidateCache(1);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Puts all the values specified at a particular type, only when that type not yet stores any values
     * 
     * @param type to put at
     * @param values to amend
     * @return true if the values were added
     */
    public boolean amendAll(TypeDeclaration type, Collection<T> values) {
        Bin bin = getBin(type);
        if (bin.values.isEmpty()) {
            bin.values = MountiplexUtil.createUmodifiableList(values);
            bin.invalidateCache(bin.values.size());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if any values are contained at a particular type.
     * This does not mean {@link #get(TypeDeclaration)} or {@link #getAll(TypeDeclaration)} will return
     * nothing. Extended or superclass types could store values as well.
     * 
     * @param type to check
     * @return True if values are stored at this type
     */
    public boolean containsKey(TypeDeclaration type) {
        return !getBin(type).isEmpty();
    }

    /**
     * Clears everything stored in this map
     */
    public void clear() {
        map.clear();
    }

    /**
     * Gets all the values stored in this map
     * 
     * @return all values
     */
    public Collection<T> values() {
        ArrayList<T> result = new ArrayList<T>();
        for (Bin bin : map.values()) {
            result.addAll(bin.values);
        }
        return result;
    }

    private final Bin getBin(TypeDeclaration type) {
        Bin bin = map.get(type);
        if (bin == null) {
            bin = new Bin(type);
            for (Entry<TypeDeclaration, Bin> entry : map.entrySet()) {
                if (isParentTypeOf(type, entry.getKey())) {
                    entry.getValue().link(bin);
                } else if (isParentTypeOf(entry.getKey(), type)) {
                    bin.link(entry.getValue());
                }
            }
            map.put(type, bin);
        }
        return bin;
    }

    /**
     * Checks if one type is a parent type of another.
     * Parent types will add to the values of their children.
     * 
     * @param parent type
     * @param child type
     * @return True if the parent type is a parent of the child
     */
    protected abstract boolean isParentTypeOf(TypeDeclaration parent, TypeDeclaration child);

    private class Bin implements Comparable<Bin> {
        public final TypeDeclaration type;
        public List<T> values = Collections.emptyList();
        private final ArrayList<Bin> parents = new ArrayList<Bin>(1);
        private final ArrayList<Bin> children = new ArrayList<Bin>(1);
        private ArrayList<T> cache = null;
        private int expectedCacheSize = 0;

        public Bin(TypeDeclaration type) {
            this.type = type;
        }

        public final void link(Bin other) {
            this.parents.add(other);
            Collections.sort(this.parents);
            this.invalidateOwnCache(other.values.size());
            other.children.add(this);
        }

        public void invalidateCache(int cacheChange) {
            this.invalidateOwnCache(cacheChange);
            for (Bin parent : this.children) {
                parent.invalidateOwnCache(cacheChange);
            }
        }

        private void invalidateOwnCache(int cacheChange) {
            this.cache = null;
            this.expectedCacheSize += cacheChange;
        }

        public boolean isEmpty() {
            return this.values.isEmpty() && getCache().isEmpty();
        }

        public Collection<T> getCache() {
            if (this.cache == null) {
                this.cache = new ArrayList<T>(this.expectedCacheSize);
                this.cache.addAll(this.values);
                for (Bin parent : this.parents) {
                    this.cache.addAll(parent.values);
                }
                this.expectedCacheSize = this.cache.size(); // Sync up in case of error
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
