package com.bergerkiller.mountiplex.reflection.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class BoxedType {
    private static final Map<String, Class<?>> unboxedByName = new HashMap<String, Class<?>>();
    private static final Map<Class<?>, Class<?>> unboxedToBoxed = new HashMap<Class<?>, Class<?>>();
    private static final Map<Class<?>, Class<?>> boxedToUnboxed = new HashMap<Class<?>, Class<?>>();

    static {
        unboxedToBoxed.put(boolean.class, Boolean.class);
        unboxedToBoxed.put(char.class, Character.class);
        unboxedToBoxed.put(byte.class, Byte.class);
        unboxedToBoxed.put(short.class, Short.class);
        unboxedToBoxed.put(int.class, Integer.class);
        unboxedToBoxed.put(long.class, Long.class);
        unboxedToBoxed.put(float.class, Float.class);
        unboxedToBoxed.put(double.class, Double.class);
        unboxedToBoxed.put(void.class, Void.class);
        for (Entry<Class<?>, Class<?>> entry : unboxedToBoxed.entrySet()) {
            boxedToUnboxed.put(entry.getValue(), entry.getKey());
            Class<?> prim = entry.getKey();
            unboxedByName.put(prim.getSimpleName(), prim);
        }
    }

    /**
     * Gets an unboxed primitive type by name
     * 
     * @param name of the primitive type
     * @return class of the type, if found
     */
    public static Class<?> getUnboxedType(String name) {
        return unboxedByName.get(name);
    }

    /**
     * Obtains the unboxed type (int) from a boxed type (Integer)<br>
     * If the input type has no unboxed type, null is returned
     *
     * @param boxedType to convert
     * @return the unboxed type
     */
    public static Class<?> getUnboxedType(Class<?> boxedType) {
        return boxedToUnboxed.get(boxedType);
    }

    /**
     * Obtains the boxed type (Integer) from an unboxed type (int)<br>
     * If the input type has no boxed type, null is returned
     *
     * @param unboxedType to convert
     * @return the boxed type
     */
    public static Class<?> getBoxedType(Class<?> unboxedType) {
        return unboxedToBoxed.get(unboxedType);
    }

    /**
     * Obtains the boxed type (Integer) from an unboxed type (int)<br>
     * If the input type has no boxed type, it is returned as-is.
     * 
     * @param type to get the boxed type for
     * @return boxed type, or the type if it has no boxed type
     */
    public static Class<?> tryBoxType(Class<?> type) {
        Class<?> boxed = unboxedToBoxed.get(type);
        return boxed == null ? type : boxed;
    }

}
