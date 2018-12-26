package com.bergerkiller.mountiplex.types;

/**
 * Test class that has hidden visibility. Handles generated for this type of object
 * should use reflection only, not attempting to use generated code to touch it.
 */
class PrivateTestObject {
    public String field;

    public String method() {
        return field;
    }
}
