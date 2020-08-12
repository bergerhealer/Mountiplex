package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Simple class that stores the name and parameter types of a method.
 * Implements hashCode() and equals() so it can be used in HashSets for
 * quick uniqueness checks.
 */
public final class MethodSignature {
    private final String name;
    private final Class<?>[] parameterTypes;

    public MethodSignature(String name, Class<?>[] parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    public MethodSignature(Method method) {
        this.name = MPLType.getName(method);
        this.parameterTypes = method.getParameterTypes();
    }

    /**
     * Gets the name of the method
     * 
     * @return name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the parameter type classes that make up the method signature
     * 
     * @return parameter types
     */
    public Class<?>[] getParameterTypes() {
        return this.parameterTypes;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MethodSignature) {
            MethodSignature other = (MethodSignature) o;
            return this.name.equals(other.name) &&
                   Arrays.equals(this.parameterTypes, other.parameterTypes);
        } else {
            return false;
        }
    }
}
