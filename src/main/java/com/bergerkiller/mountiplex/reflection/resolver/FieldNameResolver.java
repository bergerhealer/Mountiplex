package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the name of a field, making sure it matches
 * the name returned through {@link java.lang.reflect.Field}.
 */
public interface FieldNameResolver {

    /**
     * Resolves the name for a field
     * 
     * @param declaringClass where the field is declared
     * @param fieldName of the field to be resolved
     * @return resolved field name. Should return the same as the input if not resolved.
     */
    String resolveFieldName(Class<?> declaringClass, String fieldName);
}
