package com.bergerkiller.mountiplex;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.bergerkiller.mountiplex.reflection.util.ArrayHelper;

/*
 * basically just a bunch of functions taken over from BKCommonLib because we need them here
 */
public class MountiplexUtil {
    public static Logger LOGGER = Logger.getLogger("REFLECTION");
    private static ArrayList<Runnable> unloaders = new ArrayList<Runnable>();

    /**
     * Performs cleanup of all statically referenced data used by Mountiplex.
     * This will clear all caches to allow for proper garbage collection and re-loading.
     */
    public static void unloadMountiplex() {
        synchronized (unloaders) {
            for (Runnable unloader : unloaders) {
                unloader.run();
            }
            unloaders.clear();
            unloaders.trimToSize();
        }
    }

    /**
     * Adds a runnable executed when {@link #unloadMountiplex()} is called.
     * 
     * @param runnable to register
     */
    public static void registerUnloader(Runnable runnable) {
        synchronized (unloaders) {
            unloaders.add(runnable);
        }
    }

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
        return getArrayType(componentType, 1);
    }

    /**
     * Obtains the Class instance representing an array of the component type
     * specified. For example:<br>
     * - Integer.class -> Integer[].class<br>
     * - int.class -> int[].class
     *
     * @param componentType to convert
     * @param num_dimensions the number of dimensions to create the array type (e.g. 2=[][])
     * @return array type
     */
    public static Class<?> getArrayType(Class<?> componentType, int num_dimensions) {
        return ArrayHelper.getArrayType(componentType, num_dimensions);
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
     * Creates a Stream from a singleton value.
     * This is here because Stream.of does not work on Java 9+.
     * 
     * @param value
     * @return Stream
     */
    public static <T> java.util.stream.Stream<T> toStream(T value) {
        return Collections.singleton(value).stream();
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

    /**
     * Converts a collection to an Array
     *
     * @param collection to convert
     * @param type of the collection and the array to return (can not be
     * primitive)
     * @return new Array containing the elements in the collection
     */
    public static <T> T[] toArray(Collection<?> collection, Class<T> type) {
        return collection.toArray(createArray(type, collection.size()));
    }

    /**
     * Gets constants of the class type statically defined in the class itself.
     * If the class is an enum, the enumeration constants are returned.
     * Otherwise, only the static fields with theClass type are returned.
     *
     * @param theClass to get the class constants of
     * @return class constants defined in class 'theClass'
     */
    public static <T> T[] getClassConstants(Class<T> theClass) {
        return getClassConstants(theClass, theClass);
    }

    /**
     * Gets constants of the class type statically defined in the class itself.
     * If the type class is an enum, the enumeration constants are returned.
     * Otherwise, only the static fields with the same type as the type
     * parameter are returned.
     *
     * @param theClass to get the class constants of
     * @param type of constants to return from theClass
     * @return class constants defined in class 'theClass'
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] getClassConstants(Class<?> theClass, Class<T> type) {
        if (type.isEnum()) {
            // Get using enum constants
            return type.getEnumConstants();
        } else {
            // Get using reflection
            try {
                Field[] declaredFields = theClass.getDeclaredFields();
                ArrayList<T> constants = new ArrayList<T>(declaredFields.length);
                for (Field field : declaredFields) {
                    if (Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                        T constant = (T) field.get(null);
                        if (constant != null) {
                            constants.add(constant);
                        }
                    }
                }
                return toArray(constants, type);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to find class constants of " + theClass, t);
                return createArray(type, 0);
            }
        }
    }

    /**
     * Tries to parse the text to one of the values in the array specified
     *
     * @param values array to look for a value
     * @param text to parse
     * @param def to return on failure
     * @return Parsed or default value
     */
    public static <T> T parseArray(T[] values, String text, T def) {
        if (text == null || text.isEmpty()) {
            return def;
        }
        text = text.toUpperCase(Locale.ENGLISH).replace("_", "").replace(" ", "");
        String[] names = new String[values.length];
        int i;
        for (i = 0; i < names.length; i++) {
            if (values[i] instanceof Enum) {
                names[i] = ((Enum<?>) values[i]).name();
            } else {
                names[i] = values[i].toString();
            }
            names[i] = names[i].toUpperCase(Locale.ENGLISH).replace("_", "");
            if (names[i].equals(text)) {
                return values[i];
            }
        }
        for (i = 0; i < names.length; i++) {
            if (names[i].contains(text)) {
                return values[i];
            }
        }
        for (i = 0; i < names.length; i++) {
            if (text.contains(names[i])) {
                return values[i];
            }
        }
        return def;
    }

    /**
     * Tries to parse the text to one of the values in the Enum specified<br>
     * <b>The default value is used to obtain the class to look in, it can not
     * be null!</b>
     *
     * @param text to parse
     * @param def to return on failure
     * @return Parsed or default value
     */
    @SuppressWarnings("unchecked")
    public static <T> T parseEnum(String text, T def) {
        return parseEnum((Class<T>) def.getClass(), text, def);
    }

    /**
     * Tries to parse the text to one of the values in the Enum specified
     *
     * @param enumClass to look for a value
     * @param text to parse
     * @param def to return on failure
     * @return Parsed or default value
     */
    public static <T> T parseEnum(Class<T> enumClass, String text, T def) {
        if (!enumClass.isEnum()) {
            throw new IllegalArgumentException("Class '" + enumClass.getSimpleName() + "' is not an Enumeration!");
        }
        return parseArray(enumClass.getEnumConstants(), text, def);
    }

    /**
     * Throws a throwable as an unchecked exception.
     * 
     * @param t throwable
     * @return runtime exception to throw
     */
    public static RuntimeException uncheckedRethrow(Throwable t) {
        if (t instanceof InvocationTargetException) {
            t = t.getCause();
        }
        if (t instanceof Error) {
            throw (Error) t;
        } else if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        } else {
            RuntimeException r = new RuntimeException("An exception occurred", t);
            r.setStackTrace(new StackTraceElement[0]);
            return r;
        }
    }
    
    /**
     * Calculates the similarity (a number within 0.0 and 1.0) between two strings.
     */
    // https://stackoverflow.com/a/16018452
    public static double similarity(String s1, String s2) {
      String longer = s1, shorter = s2;
      if (s1.length() < s2.length()) { // longer should always have greater length
        longer = s2; shorter = s1;
      }
      int longerLength = longer.length();
      if (longerLength == 0) { return 1.0; /* both strings are zero length */ }
      /* // If you have StringUtils, you can use it to calculate the edit distance:
      return (longerLength - StringUtils.getLevenshteinDistance(longer, shorter)) /
                                 (double) longerLength; */
      return (longerLength - editDistance(longer, shorter)) / (double) longerLength;

    }

    // Example implementation of the Levenshtein Edit Distance
    // See http://rosettacode.org/wiki/Levenshtein_distance#Java
    public static int editDistance(String s1, String s2) {
      s1 = s1.toLowerCase(Locale.ENGLISH);
      s2 = s2.toLowerCase(Locale.ENGLISH);

      int[] costs = new int[s2.length() + 1];
      for (int i = 0; i <= s1.length(); i++) {
        int lastValue = i;
        for (int j = 0; j <= s2.length(); j++) {
          if (i == 0)
            costs[j] = j;
          else {
            if (j > 0) {
              int newValue = costs[j - 1];
              if (s1.charAt(i - 1) != s2.charAt(j - 1))
                newValue = Math.min(Math.min(newValue, lastValue),
                    costs[j]) + 1;
              costs[j - 1] = lastValue;
              lastValue = newValue;
            }
          }
        }
        if (i > 0)
          costs[s2.length()] = lastValue;
      }
      return costs[s2.length()];
    }

    /**
     * Stores all the values in a Collection in a new, unmodifiable List
     * 
     * @param values to store
     * @return unmodifiable List
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> createUmodifiableList(Collection<T> values) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        T[] valuesArr = (T[]) new Object[values.size()];
        int index = 0;
        for (T value : values) {
            valuesArr[index++] = value;
        }
        return Arrays.asList(valuesArr);
    }

    /**
     * Decodes the most likely package path given a class path. Assumes a traditional
     * naming convention with first-char-capitalized Class names and first-char-lowercase packages.
     * 
     * @param classPath
     * @return package path
     */
    public static String getPackagePathFromClassPath(String classPath) {
        int lastDotIndex = -100;
        for (int i = 0; i < classPath.length(); i++) {
            char c = classPath.charAt(i);
            if (c == '.') {
                lastDotIndex = i;
            } else if (i == (lastDotIndex+1) && (c == '$' || Character.isUpperCase(c))) {
                break;
            }
        }
        return (lastDotIndex == -100) ? "" : classPath.substring(0, lastDotIndex);
    }

    /**
     * Produces a stream of elements, produced by repeatedly applying the
     * function <i>f</i> to the previous value until null is reached.
     * The seed starts the iteration. If the seed is null, then an empty
     * stream is returned.
     * 
     * @param <T> Type of elements in the stream
     * @param seed Initial value
     * @param f Function applied to the value repeatedly
     * @return stream
     */
    public static <T> Stream<T> iterateNullTerminated(final T seed, UnaryOperator<T> f) {
        if (seed == null) {
            return Stream.empty();
        }
        final Iterator<T> iterator = new Iterator<T>() {
            T current = seed;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public T next() {
                T result = current;
                current = f.apply(current);
                return result;
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED | Spliterator.IMMUTABLE), false);
    }
}
