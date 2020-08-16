package com.bergerkiller.mountiplex.reflection.resolver;

import java.util.Map;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;

/**
 * Chains resolvers so that one is called after the other, without requiring a List.
 */
public class ChainResolver {
    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static ClassDeclarationResolver chain(final ClassDeclarationResolver previous, final ClassDeclarationResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : new ClassDeclarationResolver() {
            @Override
            public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
                ClassDeclaration result = previous.resolveClassDeclaration(classPath, classType);
                if (result == null) {
                    result = next.resolveClassDeclaration(classPath, classType);
                }
                return result;
            }

            @Override
            public void resolveClassVariables(String classPath, Class<?> classType, Map<String, String> variables) {
                previous.resolveClassVariables(classPath, classType, variables);
                next.resolveClassVariables(classPath, classType, variables);
            }
        };
    }

    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static ClassPathResolver chain(final ClassPathResolver previous, final ClassPathResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : new ClassPathResolver() {
            @Override
            public String resolveClassPath(String classPath) {
                classPath = previous.resolveClassPath(classPath);
                classPath = next.resolveClassPath(classPath);
                return classPath;
            }

            @Override
            public boolean canLoadClassPath(String classPath) {
                return previous.canLoadClassPath(classPath) &&
                       next.canLoadClassPath(classPath);
            }
        };
    }

    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static CompiledFieldNameResolver chain(final CompiledFieldNameResolver previous, final CompiledFieldNameResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : (declaringClass, fieldName) -> {
            fieldName = previous.resolveCompiledFieldName(declaringClass, fieldName);
            fieldName = next.resolveCompiledFieldName(declaringClass, fieldName);
            return fieldName;
        };
    }

    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static CompiledMethodNameResolver chain(final CompiledMethodNameResolver previous, final CompiledMethodNameResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : (declaringClass, methodName, parameterTypes) -> {
            methodName = previous.resolveCompiledMethodName(declaringClass, methodName, parameterTypes);
            methodName = next.resolveCompiledMethodName(declaringClass, methodName, parameterTypes);
            return methodName;
        };
    }

    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static FieldNameResolver chain(final FieldNameResolver previous, final FieldNameResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : (declaringClass, fieldName) -> {
            fieldName = previous.resolveFieldName(declaringClass, fieldName);
            fieldName = next.resolveFieldName(declaringClass, fieldName);
            return fieldName;
        };
    }

    /**
     * Chains two resolvers so that first the previous one is called, then the second.
     * If the previous resolver is a no-op, then next is returned instead.
     * 
     * @param previous
     * @param next
     * @return chained
     */
    public static MethodNameResolver chain(final MethodNameResolver previous, final MethodNameResolver next) {
        return (previous == NoOpResolver.INSTANCE) ? next : (declaringClass, methodName, parameterTypes) -> {
            methodName = previous.resolveMethodName(declaringClass, methodName, parameterTypes);
            methodName = next.resolveMethodName(declaringClass, methodName, parameterTypes);
            return methodName;
        };
    }
}
