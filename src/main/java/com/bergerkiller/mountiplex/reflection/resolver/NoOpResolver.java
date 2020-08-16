package com.bergerkiller.mountiplex.reflection.resolver;

import java.util.Map;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;

/**
 * Resolvers that does nothing, that is, what goes in comes back out.
 * When during registering this type is encountered, this type is replaced.
 */
public final class NoOpResolver implements ClassPathResolver, FieldNameResolver, MethodNameResolver,
        CompiledFieldNameResolver, CompiledMethodNameResolver, ClassDeclarationResolver
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
}
