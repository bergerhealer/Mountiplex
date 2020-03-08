package com.bergerkiller.mountiplex.reflection.util.fast;

/**
 * Exception thrown when the number of arguments specified by the caller is not
 * equal to the number of arguments expected by a method
 */
public class InvalidArgumentCountException extends IllegalArgumentException {
    private static final long serialVersionUID = 8575190678319065492L;

    public InvalidArgumentCountException(String type, int given, int expected) {
        super("Invalid amount of arguments for " + type + " (" + given + " given, " + expected + " expected)");
    }
}
