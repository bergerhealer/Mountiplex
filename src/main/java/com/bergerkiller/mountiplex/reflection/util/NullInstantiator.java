package com.bergerkiller.mountiplex.reflection.util;

import org.objenesis.ObjenesisHelper;
import org.objenesis.instantiator.ObjectInstantiator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Creates Class instances without calling any constructors, leaving all member
 * fields null or the equivalent primitive value (0, false, etc.).
 * This class is thread-safe.
 */
public class NullInstantiator<T> {
    private final Class<?> type;
    private ObjectInstantiator<?> instantiator = null;

    private NullInstantiator(Class<? extends T> type) {
        this.type = type;
    }

    /**
     * Creates a new class instance
     * 
     * @return created class instance
     */
    @SuppressWarnings("unchecked")
    public T create() {
        if (this.instantiator == null) {
            synchronized (this) {
                if (this.instantiator == null) {
                    if (this.type == null) {
                        throw new IllegalStateException("Class is unavailable");
                    }
                    this.instantiator = ObjenesisHelper.getInstantiatorOf(this.type);
                    if (this.instantiator == null) {
                        throw new IllegalStateException("Class of type " + MPLType.getName(this.type) + " could not be instantiated");
                    }
                }
            }
        }
        try {
            return (T) this.instantiator.newInstance();
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Creates a NullInstantiator for the given Class type. The NullInstantiator
     * can be used to create new instances of the type without calling a constructor.
     * 
     * @param <T>
     * @param type Class type
     * @return NullInstantiator that can construct instances of the type
     */
    public static <T> NullInstantiator<T> of(Class<? extends T> type) {
        return new NullInstantiator<T>(type);
    }
}
