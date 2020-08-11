package com.bergerkiller.mountiplex.reflection.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

import javassist.CtField;

/**
 * Stores argument values temporarily while an object is constructed. Singleton.
 * Values can only be retrieved once when stored once.
 * The class is thread-safe.
 * When a value is fetched twice, null is returned.
 */
public class GeneratorArgumentStore {
    private static final Map<Integer, Object> _constructionArgs = new ConcurrentHashMap<Integer, Object>();
    private static final AtomicInteger _constructionArgCtr = new AtomicInteger(0);

    /**
     * Uses this store to create a one-time field initializer
     * 
     * @param value
     * @return initializer
     */
    public static CtField.Initializer initializeField(Object value) {
        if (value == null) {
            return CtField.Initializer.byExpr("null");
        }

        // TODO: This is ew. Is there no more efficient way to do it?
        int record = store(value);
        return CtField.Initializer.byExpr("(" + MPLType.getName(value.getClass()) + ") "+
            GeneratorArgumentStore.class.getName() + ".fetch(" + record + ");");
    }

    /**
     * Fetches a value by the record returned by @link {@link #store(Object)}
     * 
     * @param record index
     * @return value
     */
    public static Object fetch(int record) {
        return _constructionArgs.remove(Integer.valueOf(record));
    }

    /**
     * Stores a value so that it can later be fetched again
     * 
     * @param value to store
     * @return record index by which the value can be obtained again using {@link #fetch(int)}
     */
    public static int store(Object value) {
        int record = _constructionArgCtr.incrementAndGet();
        _constructionArgs.put(Integer.valueOf(record), value);
        return record;
    }
}
