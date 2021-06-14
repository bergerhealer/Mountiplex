package com.bergerkiller.mountiplex.reflection.resolver;

/**
 * Resolves the alias name of an existing field. If the field name is obfuscated,
 * and a remapper is capable of de-obfuscating the field name, this interface
 * can be registered to do exactly that.<br>
 * <br>
 * If no resolver can resolve an alias for the field, then the
 * {@link java.lang.reflect.Field#getName()} is used for an alias, instead. On
 * environments where a remapper is installed that overrides this behavior, a
 * proper alias can be provided that way.
 */
public interface FieldAliasResolver {

    /**
     * Resolves the alias name for a field.
     * 
     * @param field The field
     * @param name The 'true' name of the field (not remapped)
     * @return alias name of the field, or null if no alias is available
     */
    String resolveFieldAlias(java.lang.reflect.Field field, String name);
}
