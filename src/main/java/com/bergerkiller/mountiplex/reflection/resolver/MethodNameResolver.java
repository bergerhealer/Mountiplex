package com.bergerkiller.mountiplex.reflection.resolver;

public interface MethodNameResolver {

    /**
     * Resolves the name for a method
     * 
     * @param declaredClass where the method is defined
     * @param methodName of the method to be resolved
     * @param parameterTypes for the method
     * @return resolved method name. Should return the same as the input if not resolved.
     */
    String resolveMethodName(Class<?> declaredClass, String methodName, Class<?>[] parameterTypes);
}
