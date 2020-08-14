package com.bergerkiller.mountiplex.reflection.resolver;

public interface ClassPathResolver {

    /**
     * Resolves a full class path to a valid class path in the current context
     * 
     * @param classPath
     * @return resolved class path. Should return the same as the input if not resolved.
     */
    String resolveClassPath(String classPath);

    /**
     * Checks whether .class data can be loaded directly from the class path specified.
     * When this returns true, and the .class file can be loaded as a resource, then
     * this class data is used when compiling at-runtime generated methods. When this
     * returns false, then a mock version of the class data is generated using reflection,
     * which only gives access to method/field/etc. signatures.
     * 
     * @param classPath
     * @return True if the .class data at this classpath can be loaded.
     */
    default boolean canLoadClassPath(String classPath) { return true; }
}
