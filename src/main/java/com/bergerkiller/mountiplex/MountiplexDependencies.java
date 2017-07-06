package com.bergerkiller.mountiplex;

/**
 * This unused class ensures that classes are included in the final jar that the Maven Shade plugin fails to find
 */
class MountiplexDependencies {

    static {
        include(net.sf.cglib.proxy.InvocationHandler.class);
        include(net.sf.cglib.proxy.LazyLoader.class);
        include(net.sf.cglib.proxy.Dispatcher.class);
        include(net.sf.cglib.proxy.ProxyRefDispatcher.class);
    }

    private static void include(Class<?> type) {
    }
}
