package com.bergerkiller.mountiplex.reflection.util.fast;

import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;

/**
 * An invoker that results in generated code at runtime, allowing for a caller
 * to use a more optimized function that doesn't require casting. The class
 * name returned by {@link #getInvokerClassInternalName()}} can be class-loaded,
 * after which the true invoker class is lazily generated.
 *
 * @param <T>
 */
public interface GeneratedExactSignatureInvoker<T> extends Invoker<T> {

    /**
     * Gets the internal class name of the invoker class that stores a static method
     * with an exactly matching method signature. The class name returned here
     * may or may not already have been loaded/generated.
     *
     * @return Internal class name
     */
    String getInvokerClassInternalName();

    /**
     * Gets the Type Descriptor format of {@link #getInvokerClassInternalName()}
     *
     * @return Invoker class Type Descriptor
     */
    String getInvokerClassTypeDescriptor();
}
