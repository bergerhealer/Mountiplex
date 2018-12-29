package com.bergerkiller.mountiplex.reflection;

import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.util.FastConstructor;

/**
 * A safe version of the Constructor
 *
 * @param <T> type of Class to construct
 */
public class SafeConstructor<T> {
    private final FastConstructor<T> constructor;

    public SafeConstructor(FastConstructor<T> constructor) {
        this.constructor = constructor;
    }

    public SafeConstructor(java.lang.reflect.Constructor<?> constructor) {
        this.constructor = new FastConstructor<T>();
        this.constructor.init(constructor);
    }

    public SafeConstructor(Class<T> type, Class<?>... parameterTypes) {
        this.constructor = new FastConstructor<T>();
        try {
            if (type == null) {
                MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to find constructor because type is null");
                this.constructor.initUnavailable("NULL_UNKNOWN_TYPE()");
            } else {
                this.constructor.init(type.getDeclaredConstructor(parameterTypes));
            }
        } catch (SecurityException e) {
            MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to access constructor", e);
        } catch (NoSuchMethodException e) {
            MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to find constructor", e);
        }
    }

    /**
     * Checks whether this Constructor is in a valid state<br>
     * Only if this return true can this Constructor be used without problems
     *
     * @return True if this constructor is valid, False if not
     */
    public boolean isValid() {
        return constructor.isAvailable();
    }

    /**
     * Constructs a new Instance
     *
     * @param parameters to use for this Constructor
     * @return A constructed type
     * @throws RuntimeException if something went wrong while constructing
     */
    public T newInstance(Object... parameters) {
        return constructor.newInstanceVA(parameters);
    }

    /**
     * Obtains a new Class Contructor that uses this contructor and converts the
     * output
     *
     * @param converter to use for the output
     * @return translated output
     */
    @SuppressWarnings("unchecked")
    public <K> SafeConstructor<K> translateOutput(final Converter<?, K> converter) {
        return new SafeConstructor<K>((FastConstructor<K>) this.constructor) {
            @Override
            public K newInstance(Object... parameters) {
                return converter.convert(super.newInstance(parameters));
            }
        };
    }

    public static <T> SafeConstructor<T> create(Class<T> type, Class<?>... parameterTypes) {
        return new SafeConstructor<T>(type, parameterTypes);
    }
}
