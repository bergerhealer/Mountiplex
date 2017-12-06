package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;
import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionInvoker;

public class FastMethod<T> implements Invoker<T>, LazyInitializedObject {
    private Invoker<T> invoker;
    private MethodDeclaration method;
    private String missingInfo = "!!UNKNOWN!!"; // stored info for when method is null

    public FastMethod() {
        this.method = null;
        this.invoker = new FastMethodInitProxy();
    }

    public FastMethod(java.lang.reflect.Method method) {
        this.method = new MethodDeclaration(ClassResolver.DEFAULT, method);
        this.invoker = new FastMethodInitProxy();
    }

    public final void init(java.lang.reflect.Method method) {
        this.method = new MethodDeclaration(ClassResolver.DEFAULT, method);
        this.invoker = new FastMethodInitProxy();
    }

    public final void init(MethodDeclaration methodDeclaration) {
        if (methodDeclaration != null && methodDeclaration.body == null && methodDeclaration.method == null) {
            this.method = null;
            this.invoker = new FastMethodInitProxy();
        } else {
            this.method = methodDeclaration;
            this.invoker = new FastMethodInitProxy();
        }
    }

    /**
     * Declares this method to be unavailable, providing a missing information String to later identify it
     * 
     * @param missingInfo to print when trying to access it
     */
    public final void initUnavailable(String missingInfo) {
        this.method = null;
        this.invoker = new FastMethodInitProxy();
        this.missingInfo = missingInfo;
    }

    /**
     * Checks whether this fast method is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (method == null) {
            throw new UnsupportedOperationException("Method " + this.missingInfo + " is not available");
        }
    }

    /**
     * Checks whether this method is available
     * 
     * @return True if the method is available
     */
    public final boolean isAvailable() {
        return this.method != null;
    }

    /**
     * Gets the backing Java Reflection Method for this Fast Method. If this fast method
     * is not initialized, or executes generated code, this function returns <i>null</i>.
     * To check whether this fast method is initialized, use {@link #isAvailable()}.
     * 
     * @return method
     */
    public final java.lang.reflect.Method getMethod() {
        return this.method == null ? null : this.method.method;
    }

    /**
     * Gets whether this Fast Method represents a class method exactly
     * 
     * @param method to check
     * @return True if it manages the same Method, False if not
     */
    public final boolean isMethod(java.lang.reflect.Method method) {
        return this.method != null && this.method.method != null && this.method.method.equals(method);
    }

    /**
     * Gets the name of this method.
     * Returns <i>"null"</i> if this fast method is not initialized.
     * 
     * @return method name
     */
    public final String getName() {
        if (this.method == null) {
            return "null";
        } else {
            return this.method.name.value();
        }
    }

    @Override
    public void forceInitialization() {
        if (invoker instanceof FastMethod.FastMethodInitProxy) {
            ((FastMethodInitProxy) invoker).init();
        }
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

    // This object is used at the first call to initialize the method
    private final class FastMethodInitProxy implements Invoker<T> {
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
            if (invoker == this) {
                synchronized (FastMethod.this) {
                    if (invoker == this) {
                        checkInit();

                        if (method.body == null) {
                            // Calls an existing member method
                            method.method.setAccessible(true);
                            invoker = ReflectionInvoker.create(method.method);
                        } else if (method.getResolver().getDeclaredClass() != null) {
                            // Calls a method that is generated at runtime
                            invoker = GeneratedCodeInvoker.create(method);
                        } else {
                            throw new UnsupportedOperationException("The declared class for method " + 
                                    getName().toString() + " was not found");
                        }
                    }
                }
            }
            return invoker;
        }

        @Override
        public T invokeVA(Object instance, Object... args) {
            return init().invokeVA(instance, args);
        }

        @Override
        public T invoke(Object instance) {
            return init().invoke(instance);
        }

        @Override
        public T invoke(Object instance, Object arg0) {
            return init().invoke(instance, arg0);
        }

        @Override
        public T invoke(Object instance, Object arg0, Object arg1) {
            return init().invoke(instance, arg0, arg1);
        }

        @Override
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
            return init().invoke(instance, arg0, arg1, arg2);
        }

        @Override
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
            return init().invoke(instance, arg0, arg1, arg2, arg3);
        }

        @Override
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            return init().invoke(instance, arg0, arg1, arg2, arg3, arg4);
        }
    }
}
