package com.bergerkiller.mountiplex.types;

// Tests support for Java 1.8 default interface methods added to TestObject
public interface TestObjectDefInterface {

    default int defaultInterfaceMethod() {
        return 12;
    }
}
