package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;
import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionInvoker;

public class FastMethod<T> implements Invoker<T> {
    public Invoker<T> invoker;
    private MethodDeclaration method;

    public FastMethod() {
        this.method = null;
        this.invoker = this;
    }

    public FastMethod(java.lang.reflect.Method method) {
        this.method = new MethodDeclaration(ClassResolver.DEFAULT, method);
        this.invoker = this;
    }

    public final void init(java.lang.reflect.Method method) {
        this.method = new MethodDeclaration(ClassResolver.DEFAULT, method);
        this.invoker = this;
    }

    public final void init(MethodDeclaration methodDeclaration) {
        if (methodDeclaration != null && methodDeclaration.body == null && methodDeclaration.method == null) {
            this.method = null;
            this.invoker = this;
        } else {
            this.method = methodDeclaration;
            this.invoker = this;
        }
    }

    /**
     * Checks whether this fast method is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (method == null) {
            throw new UnsupportedOperationException("Method is not available");
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

    private final Invoker<T> init() {
        if (this.invoker == this) {
            checkInit();

            if (this.method.body == null) {
                // Calls an existing member method
                this.method.method.setAccessible(true);
                this.invoker = ReflectionInvoker.create(this.method.method);
            } else if (this.method.getResolver().getDeclaredClass() != null) {
                // Calls a method that is generated at runtime
                this.invoker = GeneratedCodeInvoker.create(this.method);
            } else {
                throw new UnsupportedOperationException("The declared class for method " + 
                        this.getName().toString() + " was not found");
            }
        }
        return this.invoker;
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
