package com.bergerkiller.mountiplex.reflection.util;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * A class writer with the sole aim of extending a class and re-implementing certain methods in it
 * 
 * @param <T> type of base class
 */
public class ExtendedClassWriter<T> extends ClassWriter {
    private static WeakHashMap<ClassLoader, GeneratorClassLoader> loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>();
    private static final UniqueHash generatedClassCtr = new UniqueHash();
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

        // Note: initialization must be globally synchronized to prevent multithreading bugs
        // It could accidentally create a duplicate GeneratorClassLoader, or accidentally use the same name twice
        // This synchronized block protects against these issues
        // The actual generate() is not thread-safe (for obvious reasons)
        synchronized (loaders) {
            ClassLoader baseClassLoader = baseClass.getClassLoader();
            GeneratorClassLoader theLoader = loaders.get(baseClassLoader);
            if (theLoader == null) {
                theLoader = new GeneratorClassLoader(baseClassLoader);
                loaders.put(baseClassLoader, theLoader);
            }
            this.loader = theLoader;

            // Bugfix: pick a different postfix if another class already exists with this name
            // This can happen by accident as well, when a jar is incorrectly reloaded
            // Namespace clashes are nasty!
            {
                String postfix_original = postfix;
                for (int i = 1;; i++) {
                    String tmpClassPath = baseClass.getName() + postfix;
                    boolean classExists = false;

                    try {
                        theLoader.loadClass(tmpClassPath);
                        classExists = true;
                    } catch (ClassNotFoundException e) {}

                    try {
                        Class.forName(tmpClassPath);
                        classExists = true;
                    } catch (ClassNotFoundException ex) {}

                    if (classExists) {
                        postfix = postfix_original + "_" + i;
                    } else {
                        break;
                    }
                }
            }

            String baseName = Type.getInternalName(baseClass);
            this.name = baseClass.getName() + postfix;
            this.internalName = baseName + postfix;
            this.visit(V1_6, ACC_PUBLIC, baseName + postfix, null, baseName, null);
        }
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
        return "$mplgen" + generatedClassCtr.nextHex();
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
                        "valueOf", "(" + Type.getDescriptor(primType) + ")" + Type.getDescriptor(boxedType), false);
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
                        outType.getName() + "Value", "()" + Type.getDescriptor(outType), false);
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
        final boolean isInterface = instanceType.isInterface();
        if (Modifier.isStatic(method.getModifiers())) {
            mv.visitMethodInsn(INVOKESTATIC, instanceName, method.getName(), Type.getMethodDescriptor(method), isInterface);
        } else if (instanceType.isInterface()) {
            mv.visitMethodInsn(INVOKEINTERFACE, instanceName, method.getName(), Type.getMethodDescriptor(method), isInterface);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, instanceName, method.getName(), Type.getMethodDescriptor(method), isInterface);
        }
    }

    /**
     * Includes instructions to invoke a constructor
     * 
     * @param mv method visitor
     * @param instanceType type to construct, can not be an interface
     * @param constructor to be invoked
     */
    public static void visitInit(MethodVisitor mv, Class<?> instanceType, java.lang.reflect.Constructor<?> constructor) {
        final String instanceName = Type.getInternalName(instanceType);
        mv.visitMethodInsn(INVOKESPECIAL, instanceName, "<init>", Type.getConstructorDescriptor(constructor), false);
    }

    private static final class GeneratorClassLoader extends ClassLoader {
        public GeneratorClassLoader(ClassLoader base) {
            super(base);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

}
