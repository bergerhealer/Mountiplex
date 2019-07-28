package com.bergerkiller.mountiplex.reflection.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a semi-randomized sequence of integers. Is thread-safe.
 */
public class UniqueHash {
    private final AtomicInteger value = new AtomicInteger(0);

    /**
     * Increments the internal state and returns the next unique integer hash
     * 
     * @return next number
     */
    public int next() {
        return hash(value.incrementAndGet());
    }

    /**
     * Increments the internal state and returns the next unique integer hash as a hexidecimal String
     * 
     * @return next number as hexidecimal String
     */
    public String nextHex() {
        return Integer.toHexString(next());
    }

    /**
     * Transforms a number into a more random-looking hash.
     * Every unique value of x has a unique output hash value.
     * No collisions will occur.
     * 
     * @param x
     * @return x transformed to look more random
     */
    public static int hash(int x) {
        final int prime = 2147483647;
        int hash = x ^ 0x5bf03635;
        if (hash >= prime)
            return hash;
        int residue = (int) (((long) hash * hash) % prime);
        return ((hash <= prime / 2) ? residue : prime - residue);
    }
}
