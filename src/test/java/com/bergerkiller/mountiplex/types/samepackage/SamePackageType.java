package com.bergerkiller.mountiplex.types.samepackage;

/**
 * This is a SamePackageType. It is used to verify that when a requested class exists in the
 * same package as another class, it is not picked when a class name remapping rule
 * requests something else based on imports.
 */
public class SamePackageType {
    public final String arg;

    public SamePackageType(String arg) {
        this.arg = arg;
    }
}
