package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Method;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;

/**
 * Special Invoker that initializes an Invoker field when first called, allowing for
 * lazy initialization.
 *
 * @param <T>
 */
public abstract class InitInvoker<T> implements Invoker<T>, LazyInitializedObject {
    private static final InitInvoker<?> defaultUnavailableMethod = unavailableMethod("!!UNKNOWN!!");

    /**
     * Gets the field value where this InitInvoker is stored
     * 
     * @return Invoker field
     */
    protected abstract Invoker<T> getField();

    /**
     * Sets the field value where this InitInvoker is stored
     * 
     * @param field
     */
    protected abstract void setField(Invoker<T> field);

    /**
     * Creates the invoker which is meant to be used, initializing it.
     * Should throw an exception when initialization fails.
     * 
     * @return invoker
     */
    public abstract Invoker<T> create();

    /**
     * Initializes this method invoker. If this is a generated method body,
     * the method is compiled. In other cases the method will be found through
     * reflection and optimized accessors compiled. All of this is only performed once.
     * 
     * All calls to invoke() in this class explicitly call init().
     * 
     * @return the new invoker that should be used from now on
     */
    private final Invoker<T> init() {
        Invoker<T> invoker = getField();
        if (invoker == this) {
            synchronized (this) {
                invoker = getField();
                if (invoker == this) {
                    invoker = this.create();
                    this.setField(invoker);
                }
            }
        }
        return invoker;
    }

    @Override
    public final void forceInitialization() {
        init();
    }

    @Override
    public final T invoke(Object instance) {
        return init().invoke(instance);
    }

    @Override
    public final T invoke(Object instance, Object arg0) {
        return init().invoke(instance, arg0);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1) {
        return init().invoke(instance, arg0, arg1);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return init().invoke(instance, arg0, arg1, arg2);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return init().invoke(instance, arg0, arg1, arg2, arg3);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return init().invoke(instance, arg0, arg1, arg2, arg3, arg4);
    }

    @Override
    public final T invokeVA(Object instance, Object... args) {
        return init().invokeVA(instance, args);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the method is not available
     * 
     * @param missingInfo Description of the method that is missing
     * @return init invoker
     */
    public static <T> InitInvoker<T> unavailableMethod(final String missingInfo) {
        return new UnavailableMethodInvoker<T>(missingInfo);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the method is not available. A default description (!!UNKNOWN!!)
     * is in this exception. This can be used to initialize fields before they are assigned.
     * 
     * @return init invoker
     */
    @SuppressWarnings("unchecked")
    public static <T> InitInvoker<T> unavailableMethod() {
        return (InitInvoker<T>) defaultUnavailableMethod;
    }

    /**
     * Forces initialization of an invoker, if the invoker is an InitInvoker,
     * returning the produced invoker or throwing an exception if this failed.
     * 
     * @param invoker
     * @return initialized invoker
     */
    public static <T> Invoker<T> initialize(Invoker<T> invoker) {
        if (invoker instanceof InitInvoker) {
            return ((InitInvoker<T>) invoker).init();
        } else {
            return invoker;
        }
    }

    /**
     * Invoker used by {@link #unavailableMethod(String)}
     *
     * @param <T>
     */
    public static final class UnavailableMethodInvoker<T> extends InitInvoker<T> {
        private final String missingInfo;

        private UnavailableMethodInvoker(String missingInfo) {
            this.missingInfo = missingInfo;
        }

        @Override
        public final Invoker<T> create() {
            throw new UnsupportedOperationException("Method " + missingInfo + " is not available");
        }

        @Override
        protected Invoker<T> getField() {
            return this;
        }

        @Override
        protected void setField(Invoker<T> field) {
        }
    }

    /**
     * Initializes an Invoker from a Method Declaration
     *
     * @param <T>
     */
    public static abstract class MethodInvoker<T> extends InitInvoker<T> {
        protected final MethodDeclaration method;

        public MethodInvoker(Method method) {
            this.method = (method == null) ? null : new MethodDeclaration(ClassResolver.DEFAULT, method);
        }

        public MethodInvoker(MethodDeclaration method) {
            this.method = method;
        }

        @Override
        public Invoker<T> create() {
            if (this.method == null) {
                defaultUnavailableMethod.create();
            }
            if (this.method.body == null) {
                // Calls an existing member method
                method.method.setAccessible(true);
                return ReflectionInvoker.create(method.method);
            } else if (method.getResolver().getDeclaredClass() != null) {
                // Calls a method that is generated at runtime
                return GeneratedCodeInvoker.create(method);
            } else {
                throw new UnsupportedOperationException("The declared class for method " + 
                        method.name.value() + " was not found");
            }
        }
    }

    /**
     * Reuses another invoker, proxying it, while also updating the field this proxy is stored in
     *
     * @param <T>
     */
    public static abstract class ProxyInvoker<T> extends InitInvoker<T> {
        private final Invoker<T> invoker;

        public ProxyInvoker(Invoker<T> invoker) {
            this.invoker = invoker;
        }

        @Override
        public Invoker<T> create() {
            return initialize(invoker);
        }
    }
}
