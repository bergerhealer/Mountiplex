package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the name of a method, making sure it matches
 * the name used by the compiler in currently running code.
 * If the names reported by reflection do not match the names
 * used by the JVM, this resolver can correct that.
 */
public interface CompiledMethodNameResolver {

    /**
     * Resolves the compile-time name for a method
     * 
     * @param declaringClass where the method is defined
     * @param methodName of the method to be resolved
     * @param parameterTypes for the method
     * @return resolved method name. Should return the same as the input if not resolved.
     */
    String resolveCompiledMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes);
}
