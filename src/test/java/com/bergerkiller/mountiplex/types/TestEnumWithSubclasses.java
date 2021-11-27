package com.bergerkiller.mountiplex.types;

/**
 * An enumeration that has subclasses with overrides.
 * This is used to check conversion to this type works
 * properly when a value getClass() is used.
 */
public enum TestEnumWithSubclasses {
    ONE() {
        @Override
        public void test() {
            System.out.println("ONE!");
        }
    },
    TWO() {
        @Override
        public void test() {
            System.out.println("TWO!");
        }
    },
    THREE() {
        @Override
        public void test() {
            System.out.println("THREE!");
        }
    };
    
    public abstract void test();
}
