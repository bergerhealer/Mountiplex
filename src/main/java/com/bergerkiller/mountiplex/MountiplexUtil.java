package com.bergerkiller.mountiplex;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import com.bergerkiller.mountiplex.reflection.resolver.Resolver;

public class MountiplexUtil {
    public static Logger LOGGER = Logger.getLogger("REFLECTION");

    /**
     * Checks if a list of characters contains the character specified
     *
     * @param value to find
     * @param values to search in
     * @return True if it is contained, False if not
     */
    public static boolean containsChar(char value, char... values) {
        for (char v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all the Class types of the objects in an array
     * 
     * @param values input object array
     * @return class types
     */
    public static Class<?>[] getTypes(Object[] values) {
        Class<?>[] result = new Class<?>[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (values[i] == null) ? null : values[i].getClass();
        }
        return result;
    }

    /**
     * Obtains the Class instance representing an array of the component type
     * specified. For example:<br>
     * - Integer.class -> Integer[].class<br>
     * - int.class -> int[].class
     *
     * @param componentType to convert
     * @return array type
     */
    public static Class<?> getArrayType(Class<?> componentType) {
        if (componentType.isPrimitive()) {
            return Array.newInstance(componentType, 0).getClass();
        } else {
            try {
                return Class.forName("[L" + componentType.getName() + ";");
            } catch (ClassNotFoundException e) {
                return Object[].class;
            }
        }
    }

    /**
     * Obtains the Class instance representing an array of the component type
     * specified. For example:<br>
     * - Integer.class -> Integer[].class<br>
     * - int.class -> int[].class
     *
     * @param componentType to convert
     * @param levels the amount of levels to create the array (e.g. 2=[][])
     * @return array type
     */
    public static Class<?> getArrayType(Class<?> componentType, int levels) {
        Class<?> type = componentType;
        while (levels-- > 0) {
            type = getArrayType(type);
        }
        return type;
    }

    /**
     * Constructs a new 1-dimensional Array of a given type and length
     *
     * @param type of the new Array
     * @param length of the new Array
     * @return new Array
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] createArray(Class<T> type, int length) {
        return (T[]) Array.newInstance(type, length);
    }


    /**
     * A basic retainAll implementation. (does not call collection.retainAll)
     * After this call all elements not contained in elements are removed.
     * Essentially all elements are removed except those contained in the
     * elements Collection.
     *
     * @param collection
     * @param elements to retain
     * @return True if the collection changed, False if not
     */
    public static boolean retainAll(Collection<?> collection, Collection<?> elements) {
        Iterator<?> iter = collection.iterator();
        boolean changed = false;
        while (iter.hasNext()) {
            if (!elements.contains(iter.next())) {
                iter.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * A basic toArray implementation. (does not call collection.toArray) A new
     * array of Objects is allocated and filled with the contents of the
     * collection
     *
     * @param collection to convert to an array
     * @return a new Object[] array
     */
    public static Object[] toArray(Collection<?> collection) {
        Object[] array = new Object[collection.size()];
        Iterator<?> iter = collection.iterator();
        for (int i = 0; i < array.length; i++) {
            array[i] = iter.next();
        }
        return array;
    }

    /**
     * A basic toArray implementation. (does not call collection.toArray) If the
     * array specified is not large enough, a new array with the right size is
     * allocated. If the array specified is larger than the collection, the
     * element right after the last collection element is set to null to
     * indicate the end.
     *
     * @param collection to convert to an array
     * @param array to fill with the contents (can not be null)
     * @return the array filled with the contents, or a new array of the same
     * type
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<?> collection, T[] array) {
        final int size = collection.size();
        if (array.length < size) {
            array = (T[]) createArray(array.getClass().getComponentType(), size);
        }
        Iterator<?> iter = collection.iterator();
        for (int i = 0; i < array.length; i++) {
            if (iter.hasNext()) {
                array[i] = (T) iter.next();
            } else {
                array[i] = null;
                break;
            }
        }
        return array;
    }

    /**
     * Gets the text before the last occurrence of a given separator in a text
     *
     * @param text to use
     * @param delimiter to find
     * @return the text before the delimiter, or an empty String if not found
     */
    public static String getLastBefore(String text, String delimiter) {
        final int index = text.lastIndexOf(delimiter);
        return index >= 0 ? text.substring(0, index) : "";
    }

}
