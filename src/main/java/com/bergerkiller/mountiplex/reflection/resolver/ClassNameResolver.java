package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the at-runtime Class name of a Class.
 * This might be required if Class.getName() produces something invalid.
 */
public interface ClassNameResolver {
    /**
     * Gets the name of a Class. If no special rules exist for this Class,
     * then null should be returned.
     * 
     * @param clazz
     * @return class name, returns null if the default class name should be used
     */
    String resolveClassName(Class<?> clazz);
}
