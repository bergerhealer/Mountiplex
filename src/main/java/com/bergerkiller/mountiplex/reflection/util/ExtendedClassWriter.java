package com.bergerkiller.mountiplex.reflection.util;

import static net.sf.cglib.asm.Opcodes.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;

/**
 * A class writer with the sole aim of extending a class and re-implementing certain methods in it
 * 
 * @param <T> type of base class
 */
public class ExtendedClassWriter<T> extends ClassWriter {
    private static WeakHashMap<ClassLoader, GeneratorClassLoader> loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>();
    private static int generatedClassCtr = 1;
    private final String name;
    private final String internalName;
    private final GeneratorClassLoader loader;

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>(0);
            }
        });
    }

    public ExtendedClassWriter(int flags, Class<T> baseClass) {
        this(flags, baseClass, getNextPostfix());
    }

    public ExtendedClassWriter(int flags, Class<T> baseClass, String postfix) {
        super(flags);
        ClassLoader baseClassLoader = baseClass.getClassLoader();
        GeneratorClassLoader theLoader = loaders.get(baseClassLoader);
        if (theLoader == null) {
            theLoader = new GeneratorClassLoader(baseClassLoader);
            loaders.put(baseClassLoader, theLoader);
        }
        this.loader = theLoader;

        String baseName = Type.getInternalName(baseClass);
        this.name = baseClass.getName() + postfix;
        this.internalName = Type.getInternalName(baseClass) + postfix;
        this.visit(V1_6, ACC_PUBLIC, baseName + postfix, null, baseName, null);
    }

    /**
     * Gets the name of the class being generated
     * 
     * @return class name
     */
    public final String getName() {
        return this.name;
    }

    public final String getInternalName() {
        return this.internalName;
    }
    
    @SuppressWarnings("unchecked")
    public Class<T> generate() {
        this.visitEnd();
        return (Class<T>) loader.defineClass(this.name, this.toByteArray());
    }

    public T generateInstance(Class<?>[] parameterTypes, Object[] initArgs) {
        Class<T> type = this.generate();
        try {
            return (T) type.getConstructor(parameterTypes).newInstance(initArgs);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to generate class", t);
        }
    }

    /**
     * Gets a unique class name postfix to be used for a generated class
     * 
     * @return unique class name postfix
     */
    public static String getNextPostfix() {
        return "$mplgen" + Integer.toHexString(hash(++generatedClassCtr));
    }

    /**
     * Includes instructions to box a primitive value on the stack.
     * <i>void</i> types are turned into <i>null</i>.
     * 
     * @param mv method visitor
     * @param primType primitive type to box (int -> Integer)
     */
    public static void visitBoxVariable(MethodVisitor mv, java.lang.Class<?> primType) {
        if (primType == void.class) {
            mv.visitInsn(ACONST_NULL);
        } else if (primType.isPrimitive()) {
            Class<?> boxedType = BoxedType.getBoxedType(primType);
            if (boxedType != null) {
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(boxedType),
                        "valueOf", "(" + Type.getDescriptor(primType) + ")" + Type.getDescriptor(boxedType));
            }
        }
    }

    /**
     * Includes instructions to unbox a boxed value to a primitive value on the stack.
     * If no primitive type is requested, only a checked cast is performed.
     * 
     * @param mv method visitor
     * @param outType type to unbox or cast to
     */
    public static void visitUnboxVariable(MethodVisitor mv, java.lang.Class<?> outType) {
        if (outType.isPrimitive()) {
            Class<?> boxedType = BoxedType.getBoxedType(outType);
            if (boxedType != null) {
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(boxedType));
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(boxedType),
                        outType.getName() + "Value", "()" + Type.getDescriptor(outType));
            }
        } else if (outType.isArray()) {
            mv.visitTypeInsn(CHECKCAST, Type.getDescriptor(outType));
        } else if (!Object.class.equals(outType)) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(outType));
        }
    }

    /**
     * Includes instructions to invoke a static or member method in a class or interface, automatically
     * choosing the correct opcode.
     * 
     * @param mv method visitor
     * @param instanceType the method should be invoked on
     * @param method to be invoked
     */
    public static void visitInvoke(MethodVisitor mv, Class<?> instanceType, Method method) {
        final String instanceName = Type.getInternalName(instanceType);
        if (Modifier.isStatic(method.getModifiers())) {
            mv.visitMethodInsn(INVOKESTATIC, instanceName, method.getName(), Type.getMethodDescriptor(method));
        } else if (instanceType.isInterface()) {
            mv.visitMethodInsn(INVOKEINTERFACE, instanceName, method.getName(), Type.getMethodDescriptor(method));
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, instanceName, method.getName(), Type.getMethodDescriptor(method));
        }
    }

    /**
     * Includes instructions to invoke a constructor
     * 
     * @param mv method visitor
     * @param instanceType type to construct
     * @param constructor to be invoked
     */
    public static void visitInit(MethodVisitor mv, Class<?> instanceType, java.lang.reflect.Constructor<?> constructor) {
        final String instanceName = Type.getInternalName(instanceType);
        mv.visitMethodInsn(INVOKESPECIAL, instanceName, "<init>", Type.getConstructorDescriptor(constructor));
    }

    private static final class GeneratorClassLoader extends ClassLoader {
        public GeneratorClassLoader(ClassLoader base) {
            super(base);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    // turns a sequential integer into an unique hash number
    private static int hash(int x) {
        final int prime = 2147483647;
        int hash = x ^ 0x5bf03635;
        if (hash >= prime)
            return hash;
        int residue = (int) (((long) hash * hash) % prime);
        return ((hash <= prime / 2) ? residue : prime - residue);
    }
}
