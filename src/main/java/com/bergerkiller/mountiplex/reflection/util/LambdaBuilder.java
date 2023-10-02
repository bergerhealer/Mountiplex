package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.ClassInterceptor;
import com.bergerkiller.mountiplex.reflection.util.fast.ConstantReturningInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for implementing {@link FunctionalInterface} lambda interfaces
 *
 * @param <T> Functional interface type
 */
public class LambdaBuilder<T> {
    private static final Map<Class<?>, LambdaBuilder<?>> cachedBuilders = new ConcurrentHashMap<>();
    private final Class<T> type;
    private final Method abstractMethod;

    private LambdaBuilder(Class<T> type) {
        this.type = type;

        // Type must have an empty constructor if its not an interface type
        if (!type.isInterface()) {
            try {
                Constructor<?> ctor = type.getDeclaredConstructor();
                if (Modifier.isPrivate(ctor.getModifiers())) {
                    throw new IllegalArgumentException("Type " + type + " has a private empty constructor");
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Type " + type + " does not have an empty constructor");
            }
        }

        // Find the abstract method defined in the type
        List<Method> abstractMethods = Stream.of(type.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .collect(Collectors.toList());
        if (abstractMethods.isEmpty()) {
            throw new IllegalArgumentException("Type " + type + " does not have any abstract methods");
        } else if (abstractMethods.size() > 1) {
            throw new IllegalArgumentException("Type " + type + " has too many abstract methods (" +
                    abstractMethods.size() + ")");
        } else {
            abstractMethod = abstractMethods.get(0);
        }
    }

    /**
     * Creates a new instance of the functional interface, where all method calls
     * return the constant value specified.
     *
     * @param constantValue Value to return
     * @return Implemented functional interface
     */
    public T createConstant(Object constantValue) {
        return create(ConstantReturningInvoker.of(constantValue));
    }

    /**
     * Creates a new instance of the functional interface, where all method calls
     * are forwarded to the invoker specified.
     *
     * @param invoker Invoker that is called when the abstract method is called
     * @return Implemented functional interface
     */
    public T create(Invoker<?> invoker) {
        //TODO: Optimize this?
        ClassInterceptor interceptor = new ClassInterceptor() {
            @Override
            protected Invoker<?> getCallback(Method method) {
                if (method.equals(abstractMethod)) {
                    return invoker;
                }
                return null;
            }
        };
        interceptor.setUseGlobalCallbacks(false);
        return interceptor.createInstance(type);
    }

    /**
     * Obtains a LambdaBuilder for creating the specified functional interface Class
     *
     * @param functionalInterfaceType Type of functional interface
     * @return LambdaBuilder
     * @param <C> Type of functional interface (Class Type)
     * @param <T> Type of functional interface
     */
    @SuppressWarnings("unchecked")
    public static <C, T extends C> LambdaBuilder<T> of(Class<C> functionalInterfaceType) {
        return (LambdaBuilder<T>) cachedBuilders.computeIfAbsent(functionalInterfaceType, LambdaBuilder::new);
    }
}
