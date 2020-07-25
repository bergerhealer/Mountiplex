package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Utility helper class for array operations
 */
public class ArrayHelper {
    private static int[][] dimensions_cache = new int[][] {{0}};

    /**
     * Gets the class type of an array with the given number of dimensions
     * @param componentType of the array
     * @param num_dimensions Number of dimensions, 0 returns the input component type
     * @return array type of component type with the given number of dimensions
     */
    public static Class<?> getArrayType(Class<?> componentType, int num_dimensions) {
        // Minor optimization
        if (num_dimensions == 0) {
            return componentType;
        }

        // Make sure component type is not an array, unpack of it is
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
            num_dimensions++;
        }

        // Optimization for common Object-type single-dim arrays
        if (num_dimensions == 1 && !componentType.isPrimitive()) {
            try {
                return Class.forName("[L" + componentType.getName() + ";");
            } catch (ClassNotFoundException e) {}
        }

        // Fallback creates the array of the number of dimensions requested
        // This is used for multi-dimensional arrays and primitive-type arrays
        int[][] cache = dimensions_cache;
        int len = cache.length;
        int[] dimensions;
        if (num_dimensions >= len) {
            int[][] new_cache = Arrays.copyOf(cache, num_dimensions+1);
            for (int i = len; i <= num_dimensions; i++) {
                new_cache[i] = new int[i];
            }
            dimensions_cache = new_cache;
            dimensions = new_cache[num_dimensions];
        } else {
            dimensions = cache[num_dimensions];
        }

        return Array.newInstance(componentType, dimensions).getClass();
    }
}
