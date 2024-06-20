package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class GeneratedConstructor implements Constructor<Object> {
    private final java.lang.reflect.Constructor<Object> c;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public GeneratedConstructor(java.lang.reflect.Constructor<?> constructor) {
        this.c = (java.lang.reflect.Constructor) constructor; 
    }

    @Override
    public Object newInstance() {
        throw failArgs(0);
    }

    @Override
    public Object newInstance(Object arg0) {
        throw failArgs(1);
    }

    @Override
    public Object newInstance(Object arg0, Object arg1) {
        throw failArgs(2);
    }

    @Override
    public Object newInstance(Object arg0, Object arg1, Object arg2) {
        throw failArgs(3);
    }

    @Override
    public Object newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
        throw failArgs(4);
    }

    @Override
    public Object newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        throw failArgs(5);
    }

    protected final InvalidArgumentCountException failArgs(int numArgs) {
        return new InvalidArgumentCountException("constructor", numArgs, c.getParameterTypes().length);
    }

    public static GeneratedConstructor create(java.lang.reflect.Constructor<?> constructor) {
        // Optimize constructor access by generating the code to do it (its a public member)
        ExtendedClassWriter<GeneratedConstructor> cw = ExtendedClassWriter.builder(GeneratedConstructor.class)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL).build();

        MethodVisitor mv;
        Class<?> instanceType = constructor.getDeclaringClass(); //TODO: Find the real base class or interface that declared it!
        String instanceName = MPLType.getInternalName(instanceType);
        Class<?>[] paramTypes = constructor.getParameterTypes();
        if (paramTypes.length > 5) {
            throw new IllegalArgumentException("Constructor has too many parameters to be optimizable");
        }

        // Constructor passing along the Java Reflection Method
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Constructor;)V", "(Ljava/lang/reflect/Constructor<*>;)V", null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(GeneratedConstructor.class), "<init>", "(Ljava/lang/reflect/Constructor;)V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        // create the invoke argument list description String up front (varargs)returntype
        // E.g.: "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        String argsStr_obj_token = "Ljava/lang/Object;";
        StringBuilder argsStr_build = new StringBuilder(argsStr_obj_token.length() * (paramTypes.length + 1));
        argsStr_build.append('(');
        for (int i = 0; i < paramTypes.length; i++) {
            argsStr_build.append(argsStr_obj_token);
        }
        argsStr_build.append(")Ljava/lang/Object;");
        String argsStr = argsStr_build.toString();

        // invokeVA proxy method delegating to the correct method for invocation after checking args length
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "newInstanceVA", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 1);
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
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ARRAYLENGTH);
                mv.visitMethodInsn(INVOKEVIRTUAL, MPLType.getInternalName(GeneratedConstructor.class), "failArgs", "(I)L" + MPLType.getInternalName(InvalidArgumentCountException.class) + ";", false);
                mv.visitInsn(ATHROW);
            }

            // Valid number of arguments; call the appropriate method with the array elements
            mv.visitLabel(l_validArgs);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            mv.visitTypeInsn(NEW, instanceName);
            mv.visitInsn(DUP);
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ICONST_0 + i);
                mv.visitInsn(AALOAD);
                ExtendedClassWriter.visitUnboxObjectVariable(mv, paramTypes[i]);
            }
            ExtendedClassWriter.visitInit(mv, instanceType, constructor);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(5, 2);
            mv.visitEnd();
        }

        // Invoke method that casts the parameters and calls the method
        {
            mv = cw.visitMethod(ACC_PUBLIC, "newInstance", argsStr, null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, instanceName);
            mv.visitInsn(DUP);
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitVarInsn(ALOAD, 1 + i);
                ExtendedClassWriter.visitUnboxObjectVariable(mv, paramTypes[i]);
            }
            ExtendedClassWriter.visitInit(mv, instanceType, constructor);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }

        return cw.generateInstance(new Class<?>[] {java.lang.reflect.Constructor.class}, new Object[] { constructor });
    }
}
