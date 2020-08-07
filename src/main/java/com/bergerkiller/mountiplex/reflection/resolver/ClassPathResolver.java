package com.bergerkiller.mountiplex.reflection.resolver;

public interface ClassPathResolver {

    /**
     * Resolves a full class path to a valid class path in the current context
     * 
     * @param classPath
     * @return resolved class path. Should return the same as the input if not resolved.
     */
    String resolveClassPath(String classPath);
}
