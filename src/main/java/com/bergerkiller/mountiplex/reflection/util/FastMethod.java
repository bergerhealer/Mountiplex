package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

import java.util.logging.Level;

public class FastMethod<T> implements Invoker<T>, LazyInitializedObject, IgnoresRemapping {
    private MethodDeclaration method;
    private Invoker<T> invoker;

    public FastMethod() {
        this.method = null;
        this.invoker = InitInvoker.unavailableMethod();
    }

    public FastMethod(java.lang.reflect.Method method) {
        this.init(method);
    }

    public FastMethod(java.lang.reflect.Constructor<?> constructor) {
        this.init(constructor);
    }

    public FastMethod(MethodDeclaration method) {
        this.init(method);
    }

    public FastMethod(DelayedInitializer<T> initializer) {
        this.init(initializer);
    }

    public final void init(java.lang.reflect.Method method) {
        if (method == null) {
            this.method = null;
            this.invoker = InitInvoker.unavailableMethod();
        } else {
            this.init(new MethodDeclaration(ClassResolver.DEFAULT, method));
        }
    }

    public final void init(java.lang.reflect.Constructor<?> constructor) {
        if (constructor == null) {
            this.method = null;
            this.invoker = InitInvoker.unavailableMethod();
        } else {
            this.init(new MethodDeclaration(ClassResolver.DEFAULT, constructor));
        }
    }

    public final void init(MethodDeclaration methodDeclaration) {
        if (methodDeclaration == null) {
            this.method = null;
            this.invoker = InitInvoker.unavailableMethod();
        } else if (!methodDeclaration.isRecordFieldChanger &&
                methodDeclaration.body == null &&
                methodDeclaration.method == null &&
                methodDeclaration.constructor == null
        ) {
            this.method = null;
            this.invoker = InitInvoker.unavailable("method", methodDeclaration.toString());
        } else {
            this.method = methodDeclaration;
            this.invoker = InitInvoker.forMethod(this, "invoker", methodDeclaration);
        }
    }

    public final void init(DelayedInitializer<T> initializer) {
        this.init(null, new DelayedInitializationInvoker<>(this, initializer));
    }

    public final void init(MethodDeclaration methodDeclaration, Invoker<T> invoker) {
        this.method = methodDeclaration;
        this.invoker = invoker;
    }

    /**
     * Declares this method to be unavailable, providing a missing information String to later identify it
     * 
     * @param missingInfo to print when trying to access it
     */
    public final void initUnavailable(String missingInfo) {
        this.method = null;
        this.invoker = InitInvoker.unavailable("method", missingInfo);
    }

    /**
     * Checks whether this fast method is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (this.invoker instanceof InitInvoker.UnavailableInvoker) {
            this.invoker.initializeInvoker();
        }
    }

    /**
     * Checks whether this method is available
     * 
     * @return True if the method is available
     */
    public final boolean isAvailable() {
        if (this.method == null) {
            if (this.invoker instanceof DelayedInitializationInvoker) {
                this.invoker.initializeInvoker();
                return this.method != null;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * Gets the backing Java Reflection Method for this Fast Method. If this fast method
     * is not initialized, or executes generated code, this function returns <i>null</i>.
     * To check whether this fast method is initialized, use {@link #isAvailable()}.
     * 
     * @return method
     */
    public final java.lang.reflect.Method getMethod() {
        return isAvailable() ? this.method.method : null;
    }

    /**
     * Gets whether this Fast Method represents a class method exactly
     * 
     * @param method to check
     * @return True if it manages the same Method, False if not
     */
    public final boolean isMethod(java.lang.reflect.Method method) {
        return isAvailable() && this.method.method != null && this.method.method.equals(method);
    }

    /**
     * Gets the name of this method.
     * Returns <i>"null"</i> if this fast method is not initialized.
     * 
     * @return method name
     */
    public final String getName() {
        if (isAvailable()) {
            return this.method.name.value();
        } else {
            return "null";
        }
    }

    /**
     * Gets the invoker that will be called when invoke() is called.
     * Can be used to reduce a single stack trace element.
     * 
     * @return invoker
     */
    public final Invoker<T> getInvoker() {
        return this.invoker;
    }

    @Override
    public void forceInitialization() {
        this.invoker.forceInitialization();
    }

    @Override
    public T invokeVA(Object instance, Object... args) {
        return invoker.invokeVA(instance, args);
    }

    @Override
    public T invoke(Object instance) {
        return invoker.invoke(instance);
    }

    @Override
    public T invoke(Object instance, Object arg0) {
        return invoker.invoke(instance, arg0);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1) {
        return invoker.invoke(instance, arg0, arg1);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return invoker.invoke(instance, arg0, arg1, arg2);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return invoker.invoke(instance, arg0, arg1, arg2, arg3);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return invoker.invoke(instance, arg0, arg1, arg2, arg3, arg4);
    }

    /**
     * Callback that will initialize a FastMethod when it is first invoked, or {@link #forceInitialization()}
     * is called.
     *
     * @param <T> Return type
     */
    @FunctionalInterface
    public interface DelayedInitializer<T> {
        void initialize(FastMethod<T> method) throws Throwable;
    }

    private static class DelayedInitializationInvoker<T> implements Invoker<T> {
        private final FastMethod<T> method;
        private final DelayedInitializer<T> initializer;

        public DelayedInitializationInvoker(FastMethod<T> method, DelayedInitializer<T> initializer) {
            this.method = method;
            this.initializer = initializer;
        }

        @Override
        public Invoker<T> initializeInvoker() {
            try {
                initializer.initialize(method);
                if (method.invoker == this) {
                    method.initUnavailable("(callback-initialized) which did not initialize");
                } else {
                    // Also immediately unwrap a lazy-initialized invoker for reflection
                    // We expect the full invoker to be initialized here after all
                    method.forceInitialization();
                }
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to callback-initialize method", t);
                method.initUnavailable("(callback-initialized) with init error");
            }

            return method.invoker;
        }

        @Override
        public T invokeVA(Object instance, Object... args) {
            return initializeInvoker().invokeVA(instance, args);
        }
    }
}
