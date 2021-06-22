package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.*;

import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Class is generated at runtime to invoke a method directly.
 * The input instance and arguments are unpacked, then the method is called,
 * without using any reflection to do so.
 */
public abstract class GeneratedInvoker<T> implements Invoker<T> {

    /**
     * Checks whether a method or constructor is compatible with the GeneratedInvoker.
     * Only public methods/constructors with 5 arguments or less can be called.
     * 
     * @param executable The method or constructor to check
     * @return True if compatible and create() will succeed.
     */
    public static boolean canCreate(java.lang.reflect.Executable executable) {
        Class<?>[] paramTypes = executable.getParameterTypes();
        if (paramTypes.length > 5) {
            return false;
        } else if (executable instanceof java.lang.reflect.Method) {
            return Resolver.isPublic((java.lang.reflect.Method) executable);
        } else if (executable instanceof java.lang.reflect.Constructor) {
            return Resolver.isPublic((java.lang.reflect.Constructor<?>) executable);
        } else {
            return false;
        }
    }

    /**
     * Generates a new method invoker. Internal use only. Method may only have 5 arguments or less,
     * and must be public. Check using {@link #canCreate(java.lang.reflect.Method)} first.
     * 
     * @param method The method to invoke
     * @return generated invoker
     */
    public static <T> GeneratedInvoker<T> create(java.lang.reflect.Executable executable) {
        Class<?> instanceType = executable.getDeclaringClass(); //TODO: Find the real base class or interface that declared it!

        ExtendedClassWriter<GeneratedInvoker<T>> cw = ExtendedClassWriter.builder(GeneratedInvoker.class)
                .setClassLoader(instanceType.getClassLoader())
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL).build();

        MethodVisitor mv;
        String instanceName = MPLType.getInternalName(instanceType);
        Class<?>[] paramTypes = executable.getParameterTypes();
        if (paramTypes.length > 5) {
            throw new IllegalArgumentException("Method has too many parameters to be optimizable");
        }

        Class<?> returnType;
        boolean isStatic;
        if (executable instanceof java.lang.reflect.Constructor) {
            returnType = executable.getDeclaringClass();
            isStatic = true;
        } else if (executable instanceof java.lang.reflect.Method) {
            returnType = ((java.lang.reflect.Method) executable).getReturnType();
            isStatic = Modifier.isStatic(executable.getModifiers());
        } else {
            throw new IllegalArgumentException("Not a constructor or method");
        }

        // Empty constructor
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(GeneratedInvoker.class), "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        // create the invoke argument list description String up front (instance, varargs)returntype
        // E.g.: "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        String argsStr_obj_token = "Ljava/lang/Object;";
        StringBuilder argsStr_build = new StringBuilder(argsStr_obj_token.length() * (paramTypes.length + 1));
        argsStr_build.append('(');
        for (int i = 0; i <= paramTypes.length; i++) {
            argsStr_build.append(argsStr_obj_token);
        }
        argsStr_build.append(")Ljava/lang/Object;");
        String argsStr = argsStr_build.toString();

        // invokeVA proxy method delegating to the correct method for invocation after checking args length
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 2);
            mv.visitInsn(ARRAYLENGTH);
            Label l_validArgs = new Label();
            if (paramTypes.length > 0) {
                ExtendedClassWriter.visitPushInt(mv, paramTypes.length);
                mv.visitJumpInsn(IF_ICMPEQ, l_validArgs);
            } else {
                mv.visitJumpInsn(IFEQ, l_validArgs);
            }
            {
                // Invalid number of arguments
                mv.visitTypeInsn(NEW, MPLType.getInternalName(InvalidArgumentCountException.class));
                mv.visitInsn(DUP);
                mv.visitLdcInsn("method");
                mv.visitVarInsn(ALOAD, 2);
                mv.visitInsn(ARRAYLENGTH);
                ExtendedClassWriter.visitPushInt(mv, paramTypes.length);
                mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(InvalidArgumentCountException.class), "<init>", "(Ljava/lang/String;II)V", false);
                mv.visitInsn(ATHROW);
            }

            // Valid number of arguments; call the appropriate method with the array elements
            mv.visitLabel(l_validArgs);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            if (executable instanceof java.lang.reflect.Constructor) {
                mv.visitTypeInsn(NEW, MPLType.getInternalName(instanceType));
                mv.visitInsn(DUP);
            }
            if (!isStatic) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, instanceName);
            }
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 2);
                ExtendedClassWriter.visitPushInt(mv, i);
                mv.visitInsn(AALOAD);
                ExtendedClassWriter.visitUnboxVariable(mv, paramTypes[i]);
            }
            if (executable instanceof java.lang.reflect.Method) {
                ExtendedClassWriter.visitInvoke(mv, instanceType, (java.lang.reflect.Method) executable);
            } else if (executable instanceof java.lang.reflect.Constructor) {
                mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(instanceType), "<init>",
                        MPLType.getConstructorDescriptor((java.lang.reflect.Constructor<?>) executable), false);
            }
            MPLType.visitBoxVariable(mv, returnType);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // Old code: delegates to the invoke function with the right amount of arguments
            // Replaced with one that calls the method directly to save a stack frame
            /*
            mv.visitLabel(l_validArgs);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitInsn(ICONST_0 + i);
                mv.visitInsn(AALOAD);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, cw.getInternalName(), "invoke", argsStr);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            */
        }

        // Invoke method that casts the parameters and calls the method
        {
            mv = cw.visitMethod(ACC_PUBLIC, "invoke", argsStr, null, null);
            mv.visitCode();
            if (executable instanceof java.lang.reflect.Constructor) {
                mv.visitTypeInsn(NEW, MPLType.getInternalName(instanceType));
                mv.visitInsn(DUP);
            }
            if (!isStatic) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, instanceName);
            }
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 2 + i);
                ExtendedClassWriter.visitUnboxVariable(mv, paramTypes[i]);
            }
            if (executable instanceof java.lang.reflect.Method) {
                ExtendedClassWriter.visitInvoke(mv, instanceType, (java.lang.reflect.Method) executable);
            } else if (executable instanceof java.lang.reflect.Constructor) {
                mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(instanceType), "<init>",
                        MPLType.getConstructorDescriptor((java.lang.reflect.Constructor<?>) executable), false);
            }
            MPLType.visitBoxVariable(mv, returnType);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        return cw.generateInstance();
    }
}
