package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

public abstract class GeneratedInvoker implements Invoker<Object> {
    public final java.lang.reflect.Method m;

    public GeneratedInvoker(java.lang.reflect.Method method) {
        this.m = method;
    }

    @Override
    public Object invoke(Object instance) {
        throw failArgs(0);
    }

    @Override
    public Object invoke(Object instance, Object arg0) {
        throw failArgs(1);
    }

    @Override
    public Object invoke(Object instance, Object arg0, Object arg1) {
        throw failArgs(2);
    }

    @Override
    public Object invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        throw failArgs(3);
    }

    @Override
    public Object invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        throw failArgs(4);
    }

    @Override
    public Object invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        throw failArgs(5);
    }

    protected final InvalidArgumentCountException failArgs(int numArgs) {
        return new InvalidArgumentCountException("method", numArgs, m.getParameterTypes().length);
    }

    public static GeneratedInvoker create(java.lang.reflect.Method method) {
        ExtendedClassWriter<GeneratedInvoker> cw = new ExtendedClassWriter<GeneratedInvoker>(ClassWriter.COMPUTE_MAXS, GeneratedInvoker.class);
        MethodVisitor mv;
        Class<?> instanceType = method.getDeclaringClass(); //TODO: Find the real base class or interface that declared it!
        String instanceName = Type.getInternalName(instanceType);
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (paramTypes.length > 5) {
            throw new IllegalArgumentException("Method has too many parameters to be optimizable");
        }

        // Constructor passing along the Java Reflection Method
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Method;)V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, "com/bergerkiller/mountiplex/reflection/util/fast/GeneratedInvoker", "<init>", "(Ljava/lang/reflect/Method;)V", false);
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
                mv.visitInsn(ICONST_0 + paramTypes.length);
                mv.visitJumpInsn(IF_ICMPEQ, l_validArgs);
            } else {
                mv.visitJumpInsn(IFEQ, l_validArgs);
            }
            {
                // Invalid number of arguments
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitInsn(ARRAYLENGTH);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(GeneratedInvoker.class), "failArgs", "(I)Lcom/bergerkiller/mountiplex/reflection/util/fast/InvalidArgumentCountException;", false);
                mv.visitInsn(ATHROW);
            }

            // Valid number of arguments; call the appropriate method with the array elements
            mv.visitLabel(l_validArgs);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            if (!isStatic) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, instanceName);
            }
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitInsn(ICONST_0 + i);
                mv.visitInsn(AALOAD);
                ExtendedClassWriter.visitUnboxVariable(mv, paramTypes[i]);
            }
            ExtendedClassWriter.visitInvoke(mv, instanceType, method);
            ExtendedClassWriter.visitBoxVariable(mv, returnType);
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
            if (!isStatic) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, instanceName);
            }
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 2 + i);
                ExtendedClassWriter.visitUnboxVariable(mv, paramTypes[i]);
            }
            ExtendedClassWriter.visitInvoke(mv, instanceType, method);
            ExtendedClassWriter.visitBoxVariable(mv, returnType);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        return cw.generateInstance(new Class<?>[] {java.lang.reflect.Method.class}, new Object[] { method });
    }
}
