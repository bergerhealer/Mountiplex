package com.bergerkiller.mountiplex.types;

/**
 * This object is used to test the workings of the method/field name resolver
 */
@SuppressWarnings("unused")
public class RenameTestObject {
    public int        testPublicField;
    private int       testPrivateField;
    public final int  testFinalField = Integer.valueOf(5).intValue();

    public static int        testStaticPublicField;
    private static int       testStaticPrivateField;
    public static final int  testStaticFinalField = Integer.valueOf(5).intValue();

    public int originalTestPublicField;

    public int testPublicMethod() {
        return 222;
    }

    private int testPrivateMethod() {
        return 333;
    }

    public static int testStaticPublicMethod() {
        return 444;
    }

    private static int testStaticPrivateMethod() {
        return 555;
    }

    public int originalTestPublicMethod() {
        return 666;
    }
}
