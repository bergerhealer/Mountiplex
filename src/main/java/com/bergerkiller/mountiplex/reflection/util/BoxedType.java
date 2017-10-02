package com.bergerkiller.mountiplex.reflection.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BoxedType {
    private static final Map<String, Class<?>> unboxedByName = new HashMap<String, Class<?>>();
    private static final Map<Class<?>, Class<?>> unboxedToBoxed = new HashMap<Class<?>, Class<?>>();
    private static final Map<Class<?>, Class<?>> boxedToUnboxed = new HashMap<Class<?>, Class<?>>();
    private static final Map<Class<?>, Object> unboxedDefaults = new HashMap<Class<?>, Object>();

    static {
        register(boolean.class, Boolean.class, false);
        register(char.class, Character.class, '\0');
        register(byte.class, Byte.class, (byte) 0);
        register(short.class, Short.class, (short) 0);
        register(int.class, Integer.class, 0);
        register(long.class, Long.class, 0L);
        register(float.class, Float.class, 0.0F);
        register(double.class, Double.class, 0.0);
        register(void.class, Void.class, null);
    }

    private static final <T> void register(Class<?> unboxed, Class<T> boxed, T defaultValue) {
        unboxedToBoxed.put(unboxed, boxed);
        boxedToUnboxed.put(boxed, unboxed);
        unboxedByName.put(unboxed.getSimpleName(), unboxed);
        unboxedDefaults.put(unboxed, defaultValue);
    }

    /**
     * Gets the default value for a particular Class type.
     * If the type is an unboxed type, the default constant value is returned.
     * 
     * @param type
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T getDefaultValue(Class<T> type) {
        return (T) unboxedDefaults.get(type);
    }

    /**
     * Gets a set containing all the known Java unboxed Class types
     * 
     * @return unboxed types
     */
    public static Set<Class<?>> getUnboxedTypes() {
        return unboxedToBoxed.keySet();
    }

    /**
     * Gets a set containing all the known Java boxed Class types
     * 
     * @return boxed types
     */
    public static Set<Class<?>> getBoxedTypes() {
        return boxedToUnboxed.keySet();
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
