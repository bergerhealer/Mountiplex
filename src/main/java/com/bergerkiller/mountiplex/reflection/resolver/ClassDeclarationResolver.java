package com.bergerkiller.mountiplex.reflection.resolver;

import java.util.Map;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;

public interface ClassDeclarationResolver {

    /**
     * Resolves the full Class Declaration for a Class pointed at by a certain Class
     * 
     * @param classPath of classType
     * @param classType to resolve
     * @return class declaration, null if it can not be resolved
     */
    ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType);

    /**
     * Resolves the environment variables that apply while resolving a class declaration.
     * This is used when parsing generated template declarations.
     * 
     * @param classPath of classType
     * @param classType to resolve
     * @param variables Map of key-value variables that should be filled
     */
    void resolveClassVariables(String classPath, Class<?> classType, Map<String, String> variables);
}
