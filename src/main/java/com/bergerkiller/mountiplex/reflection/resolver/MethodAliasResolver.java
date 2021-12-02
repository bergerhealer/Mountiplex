package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the alias name of an existing method. If the method name is obfuscated,
 * and a remapper is capable of de-obfuscating the field name, this interface
 * can be registered to do exactly that.<br>
 * <br>
 * If no resolver can resolve an alias for the method, then the
 * {@link java.lang.reflect.Method#getName()} is used for an alias, instead. On
 * environments where a remapper is installed that overrides this behavior, a
 * proper alias can be provided that way.
 */
public interface MethodAliasResolver {

    /**
     * Resolves the alias name for a method.
     * 
     * @param method The method
     * @param name The 'true' name of the method (not remapped)
     * @return alias name of the method, or null if no alias is available
     */
    String resolveMethodAlias(java.lang.reflect.Method method, String name);
}
