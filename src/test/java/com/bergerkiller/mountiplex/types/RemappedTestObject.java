package com.bergerkiller.mountiplex.types;

/**
 * Has a method we hook that has been remapped to from a #remap rule in the
 * {@link TestClassDeclarationResolver}
 */
public class RemappedTestObject {

    public String a(String input) {
        return input + ":original";
    }
}
