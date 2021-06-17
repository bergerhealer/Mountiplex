package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

/**
 * An invoker that results in generated code at runtime, allowing for a caller
 * to use a more optimized function that doesn't require casting. The interface
 * produced by {@link #generateInterface(MethodDeclaration)} automatically implements the
 * {@link #getInterface()} also.
 *
 * @param <T>
 */
public interface GeneratedInvoker<T> extends Invoker<T> {

    /**
     * Gets the runtime-generated interface class implemented by this
     * generated invoker. This interface contains the exact method signature
     * of the method, not requiring casting or boxing.<br>
     * <br>
     * If the real method still has to be generated, then
     * that generated invoker will implement the same interface. Calling this
     * method will not create the interface, the method declared must be called first.
     * 
     * @return generated class name
     */
    Class<?> getInterface();

    /**
     * Generates a class that implements this GeneratedInvoker, implementing the underlying invoke methods,
     * as well as it's own method exactly matching the method signature.
     * 
     * @param methodDeclaration The method signature to create an interface for
     * @return generated invoker interface
     */
    public static Class<? extends GeneratedInvoker<?>> generateInterface(MethodDeclaration methodDeclaration) {
        if (!methodDeclaration.isResolved()) {
            throw new IllegalArgumentException("Method declaration is not resolved: " + methodDeclaration);
        }

        MethodVisitor mv;
        ExtendedClassWriter<? extends GeneratedInvoker<?>> cw = ExtendedClassWriter.builder(GeneratedInvoker.class)
                .setAccess(ACC_ABSTRACT | ACC_INTERFACE).build();

        // Method signature itself, not implemented yet
        {
            mv = cw.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, methodDeclaration.name.real(), methodDeclaration.getASMInvokeDescriptor(), null, null);
            mv.visitEnd();
        }

        // Implement getInterface() to return self type
        {
            mv = cw.visitMethod(ACC_PUBLIC, "getInterface", "()Ljava/lang/Class;", "()Ljava/lang/Class<*>;", null);
            mv.visitCode();
            mv.visitLdcInsn(Type.getType(cw.getTypeDescriptor()));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        return cw.generate();
    }
}
