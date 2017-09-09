package com.bergerkiller.mountiplex.reflection.util;

/**
 * An object that is initialized lazily; when it is needed.
 * Defines a method to force further initialization right away.
 */
public interface LazyInitializedObject {

    /**
     * Forces this object to initialize right away.
     * After this call no further lazy initialization will be required.
     */
    public void forceInitialization();
}
