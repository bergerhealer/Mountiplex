package com.bergerkiller.mountiplex.reflection.util;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

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
    private final String typeDescriptor;
    private final GeneratorClassLoader loader;
    private CtClass ctClass = null;

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>(0);
            }
        });
    }

    private ExtendedClassWriter(Builder<T> options) {
        super(options.flags);

        // Get or generate postfix
        String postfix = (options.postfix != null) ? options.postfix : getNextPostfix();

        // Note: initialization must be globally synchronized to prevent multithreading bugs
        // It could accidentally create a duplicate GeneratorClassLoader, or accidentally use the same name twice
        // This synchronized block protects against these issues
        // The actual generate() is not thread-safe (for obvious reasons)
        synchronized (loaders) {
            ClassLoader baseClassLoader = options.superClass.getClassLoader();
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
                    String tmpClassPath = options.superClass.getName() + postfix;
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

            // Extends Object instead of SuperClass when it is an interface
            Class<?> superType = options.superClass.isInterface() ? Object.class : options.superClass;

            // If interfaces are specified, then the signature must be generated also
            String signature = null;
            if (!options.interfaces.isEmpty()) {
                signature = MPLType.getDescriptor(superType);
                for (Class<?> interfaceType : options.interfaces) {
                    signature += MPLType.getDescriptor(interfaceType);
                }
            }

            // Class that is extended, is Object when super type is an interface
            String superName = MPLType.getInternalName(superType);

            // Interfaces List<Class<?>> -> String[] if set
            String[] interfaceNames = null;
            if (!options.interfaces.isEmpty()) {
                interfaceNames = new String[options.interfaces.size()];
                for (int i = 0; i < interfaceNames.length; i++) {
                    interfaceNames[i] = MPLType.getInternalName(options.interfaces.get(i));
                }
            }

            this.name = options.superClass.getName() + postfix;
            this.internalName = MPLType.getInternalName(options.superClass) + postfix;
            this.typeDescriptor = computeNameDescriptor(options.superClass, postfix);
            this.visit(V1_8, options.access, this.internalName, signature, superName, interfaceNames);
        }
    }

    // TODO: make cleaner
    private static String computeNameDescriptor(Class<?> type, String postfix) {
        String basePath = MPLType.getDescriptor(type);
        return basePath.substring(0, basePath.length()-1) + postfix + ";";
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

    public final String getTypeDescriptor() {
        return this.typeDescriptor;
    }

    /**
     * Takes the current ByteCode already generated and finishes it, turning it into
     * a JavaAssist CtClass ready for further modifications. The return CtClass
     * can be further changed, such as adding methods or fields. When done, the
     * {@link #generate()} or {@link #generateInstance(Class[], Object[])} methods will
     * compile the final CtClass result.<br>
     * <br>
     * No more ASM commands (visit*) should be called after calling this method, as they
     * will have no effect.
     * 
     * @return Javassist CtClass
     */
    public CtClass getCtClass() {
        if (this.ctClass == null) {
            this.visitEnd();

            // Convert the current byte representation into a ByteArray, and then into a CtClass
            // Next time generate() is called, we instead generate using JavaAssist
            javassist.ClassPool cp = javassist.ClassPool.getDefault();
            cp.insertClassPath(new javassist.ByteArrayClassPath(this.name, this.toByteArray()));
            try {
                this.ctClass = cp.get(this.name);
            } catch (NotFoundException e) {
                throw new RuntimeException("Failed to instantiate CtClass " + this.name + " from bytecode");
            }
        }
        return this.ctClass;
    }

    @SuppressWarnings("unchecked")
    public Class<T> generate() {
        if (this.ctClass == null) {
            this.visitEnd();
            return (Class<T>) this.loader.defineClass(this.name, this.toByteArray());
        } else {
            try {
                return (Class<T>) this.ctClass.toClass(this.loader, null);
            } catch (CannotCompileException e) {
                throw new RuntimeException("Failed to compile class " + this.name, e);
            }
        }
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
     * If a Class Name is already taken, appends a number until the name is no longer used.
     * This may be needed if the server reloads and stale classes stay behind.
     * 
     * @param name
     * @return available class name
     */
    public static String getAvailableClassName(String name) {
        String resultName = name;
        for (int i = 1;; i++) {
            try {
                Class.forName(resultName);
                resultName = name + "_" + i;
            } catch (ClassNotFoundException ex) {
                return resultName;
            }
        }
    }

    /**
     * Pushes an int value onto the stack, making use of the optimized 0-5 values
     * 
     * @param mv
     * @param value
     */
    public static void visitPushInt(MethodVisitor mv, int value) {
        if (value < 0 || value > 5) {
            mv.visitIntInsn(BIPUSH, value);
        } else {
            mv.visitInsn(ICONST_0 + value);
        }
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
                mv.visitMethodInsn(INVOKESTATIC, MPLType.getInternalName(boxedType),
                        "valueOf", "(" + MPLType.getDescriptor(primType) + ")" + MPLType.getDescriptor(boxedType), false);
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
                mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(boxedType));
                mv.visitMethodInsn(INVOKEVIRTUAL, MPLType.getInternalName(boxedType),
                        outType.getName() + "Value", "()" + MPLType.getDescriptor(outType), false);
            }
        } else if (outType.isArray()) {
            mv.visitTypeInsn(CHECKCAST, MPLType.getDescriptor(outType));
        } else if (!Object.class.equals(outType)) {
            mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(outType));
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
        final String instanceName = MPLType.getInternalName(instanceType);
        final boolean isInterface = instanceType.isInterface();
        if (Modifier.isStatic(method.getModifiers())) {
            mv.visitMethodInsn(INVOKESTATIC, instanceName, method.getName(), MPLType.getMethodDescriptor(method), isInterface);
        } else if (instanceType.isInterface()) {
            mv.visitMethodInsn(INVOKEINTERFACE, instanceName, method.getName(), MPLType.getMethodDescriptor(method), isInterface);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, instanceName, method.getName(), MPLType.getMethodDescriptor(method), isInterface);
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
        final String instanceName = MPLType.getInternalName(instanceType);
        mv.visitMethodInsn(INVOKESPECIAL, instanceName, "<init>", MPLType.getConstructorDescriptor(constructor), false);
    }

    private static final class GeneratorClassLoader extends ClassLoader {
        public GeneratorClassLoader(ClassLoader base) {
            super(base);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    /**
     * Gets the class loader used to generate classes.
     * This method is thread-safe.
     * 
     * @param baseClassLoader The base class loader used by the caller
     * @return generator class loader
     */
    public static ClassLoader getGeneratorClassLoader(ClassLoader baseClassLoader) {
        synchronized (loaders) {
            GeneratorClassLoader theLoader = loaders.get(baseClassLoader);
            if (theLoader == null) {
                theLoader = new GeneratorClassLoader(baseClassLoader);
                loaders.put(baseClassLoader, theLoader);
            }
            return theLoader;
        }
    }

    /**
     * Creates a builder for extending the super class specified.
     * If the super class is an interface, Object is extended instead,
     * adding this super class as an interface to implement.
     * 
     * @param superClass
     */
    public static <T> Builder<T> builder(Class<T> superClass) {
        return new Builder<T>(superClass);
    }

    /**
     * Builder for setting up the extended class writer. Call build() to create the writer
     * and start writing the class.
     *
     * @param <T> Super class type
     */
    public static final class Builder<T> {
        private final Class<T> superClass;
        private List<Class<?>> interfaces = new ArrayList<Class<?>>(0);
        private int flags = 0;
        private int access = ACC_PUBLIC | ACC_STATIC;
        private String postfix = null;

        private Builder(Class<T> superClass) {
            this.superClass = superClass;
            if (superClass.isInterface()) {
                this.interfaces.add(superClass);
            }
        }

        public Builder<T> addInterface(Class<?> interfaceType) {
            this.interfaces.add(interfaceType);
            return this;
        }

        public Builder<T> setFlags(int flags) {
            this.flags |= flags;
            return this;
        }

        public Builder<T> setAccess(int access) {
            this.access |= access;
            return this;
        }

        public Builder<T> setPostfix(String postfix) {
            this.postfix = postfix;
            return this;
        }

        /**
         * Builds the extended class writer to begin writing the class
         * 
         * @return extended class writer
         */
        @SuppressWarnings("unchecked")
        public <TO> ExtendedClassWriter<TO> build() {
            return (ExtendedClassWriter<TO>) new ExtendedClassWriter<T>(this);
        }
    }
}
