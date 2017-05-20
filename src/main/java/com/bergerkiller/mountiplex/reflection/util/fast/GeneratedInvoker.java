package com.bergerkiller.mountiplex.reflection.util.fast;

public class GeneratedInvoker implements Invoker<Object> {

    protected final void throwInvalidArgs(int numArgs, int numExpected) {
        throw new IllegalArgumentException("Invalid amount of arguments for method (" +
                numArgs + " given, " + numExpected + " expected)");
    }

    @Override
    public Object invoke(Object instance, Object[] args) {
        return null;
    }

}
