package com.bergerkiller.mountiplex.reflection;

/**
 * Error thrown inside invoke/invokeVA methods when the called method throws
 * a checked exception. Normal runtime exceptions are re-thrown as they are,
 * but you can not do that with checked exceptions.
 */
public class UnhandledInvokerCheckedException extends RuntimeException {

    public UnhandledInvokerCheckedException(Throwable cause) {
        super("An error occurred in the invoked method", cause);
    }
}
