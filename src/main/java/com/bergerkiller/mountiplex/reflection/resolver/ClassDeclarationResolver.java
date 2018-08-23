package com.bergerkiller.mountiplex.reflection.resolver;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;

public interface ClassDeclarationResolver {

    /**
     * Resolves the full Class Declaration for a Class pointed at by a certain Class
     * 
     * @param classPath of classType
     * @param classType to resolve
     * @return class declaration, null if it can not be resolved
     */
    public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType);
}
