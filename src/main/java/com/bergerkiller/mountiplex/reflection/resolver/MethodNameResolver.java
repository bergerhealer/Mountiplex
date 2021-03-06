package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the name of a method, making sure it matches
 * the name returned through {@link java.lang.reflect.Method}.
 */
public interface MethodNameResolver {

    /**
     * Resolves the name for a method
     * 
     * @param declaringClass where the method is defined
     * @param methodName of the method to be resolved
     * @param parameterTypes for the method
     * @return resolved method name. Should return the same as the input if not resolved.
     */
    String resolveMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes);
}
