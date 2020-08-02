package com.bergerkiller.mountiplex.types;

/**
 * This object is used to test the workings of the method/field name resolver
 */
@SuppressWarnings("unused")
public class RenameTestObject {
    public int testPublicField;
    private int testPrivateField;
    public final int testFinalField = 5;

    public int testPublicMethod() {
        return 222;
    }

    private int testPrivateMethod() {
        return 333;
    }
}
