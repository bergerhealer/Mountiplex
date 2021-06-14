package com.bergerkiller.mountiplex.reflection.resolver;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Resolvers that does nothing, that is, what goes in comes back out.
 * When during registering this type is encountered, this type is replaced.
 */
public final class NoOpResolver implements ClassPathResolver, FieldNameResolver, MethodNameResolver,
        CompiledFieldNameResolver, CompiledMethodNameResolver, ClassDeclarationResolver, FieldAliasResolver
{
    public static final NoOpResolver INSTANCE = new NoOpResolver();

    private NoOpResolver() {
    }

    @Override
    public String resolveCompiledMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
        return methodName;
    }

    @Override
    public String resolveCompiledFieldName(Class<?> declaringClass, String fieldName) {
        return fieldName;
    }

    @Override
    public String resolveMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
        return methodName;
    }

    @Override
    public String resolveFieldName(Class<?> declaringClass, String fieldName) {
        return fieldName;
    }

    @Override
    public String resolveClassPath(String classPath) {
        return classPath;
    }

    @Override
    public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
        return null;
    }

    @Override
    public void resolveClassVariables(String classPath, Class<?> classType, Map<String, String> variables) {
    }

    @Override
    public String resolveFieldAlias(Field field, String name) {
        try {
            return field.getName();
        } catch (Throwable t) {
            String decName = MPLType.getName(field.getDeclaringClass());
            MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to retrieve field name alias for " + decName + ":" + name + ":", t);
            return null;
        }
    }
}
