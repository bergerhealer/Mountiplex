package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the name of a field, making sure it matches
 * the name used by the compiler in currently running code.
 * If the names reported by reflection do not match the names
 * used by the JVM, this resolver can correct that.
 */
public interface CompiledFieldNameResolver {

    /**
     * Resolves the compile-time name for a field
     * 
     * @param declaringClass where the field is declared
     * @param fieldName of the field to be resolved
     * @return resolved field name. Should return the same as the input if not resolved.
     */
    String resolveCompiledFieldName(Class<?> declaringClass, String fieldName);
}
