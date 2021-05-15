package com.bergerkiller.mountiplex.reflection.util.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;
import com.bergerkiller.mountiplex.reflection.util.UniqueHash;

/**
 * Generates the GeneratorClassLoader type and instance, which is the root
 * of all generated classes
 */
public class MPLGeneratorClassLoaderBuilder {

    /**
     * Generate a class type implementation of GeneratorClassLoader that correctly
     * calls the underlying defineClass() method. We generate it in such a way that
     * the external environment will not inject a custom handler that will alter the
     * bytecode before it is loaded as a Class.
     *
     * @return GeneratorClassLoader implementation type
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends GeneratorClassLoader> buildImplementation() {
        // Generate a class type implementation of GeneratorClassLoader that correctly
        // calls the underlying defineClass() method. We generate it in such a way that
        // the external environment will not inject a custom handler that will alter the
        // bytecode before it is loaded as a Class.
        //
        // Now we need to turn this bytecode into a generated class. For JDK9 and later, we can use
        // MethodHandles.Lookup.defineClass() to do this without generating warnings or errors.
        // For JDK8, we call the defineClass method on the current class loader context directly.
        //
        // We do both operations by calling the methods using reflection to prevent an eager environment
        // from rewriting the reflective call to inject their own bytecode manipulation. Avoid that!

        // Detect whether JDK9-compatibility is possible
        Method methodHandlesAPIDefineClass = getMethodHandlesAPIDefineClassMethod();
        if (methodHandlesAPIDefineClass != null) {
            // Build source (name + bytecode)
            ImplementationSource source = buildSource(MPLGeneratorClassLoaderBuilder.class, "GeneratorClassLoaderImpl");

            // Call MethodHandles.Lookup.defineClass(byte[]) using reflection
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                return (Class<? extends GeneratorClassLoader>) methodHandlesAPIDefineClass.invoke(lookup, source.byteCode);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        } else {
            // Build source (name + bytecode)
            ImplementationSource source = buildSource(GeneratorClassLoader.class, "impl");

            // Call defineClass on the ClassLoader of GeneratorClassLoader using reflection
            ClassLoader loader = GeneratorClassLoader.class.getClassLoader();
            Method classLoaderDefineClassMethod = getClassLoaderAPIDefineClassMethod();
            ProtectionDomain protectionDomain = null; //TODO: Needed?

            try {
                return (Class<? extends GeneratorClassLoader>) classLoaderDefineClassMethod.invoke(loader,
                        source.className,
                        source.byteCode,
                        Integer.valueOf(0),
                        Integer.valueOf(source.byteCode.length),
                        protectionDomain);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }
    }

    private static Method getMethodHandlesAPIDefineClassMethod() {
        try {
            Class.forName("java.lang.invoke.MethodHandles$Lookup");
        } catch (ClassNotFoundException ex) {
            return null;
        }

        // Call getMethod using reflection to prevent a bytecode manipulating class loader
        // from altering the result so they can inject their nasty evil filth!
        Method getMethodMethod;
        try {
            getMethodMethod = Class.class.getMethod(String.join("", "get", "Method"), String.class, Class[].class);
        } catch (Throwable t) {
            // Odd!
            throw MountiplexUtil.uncheckedRethrow(t);
        }

        // Let's go!
        try {
            return (Method) getMethodMethod.invoke(MethodHandles.Lookup.class,
                    String.join("", "define", "Class"),
                    new Class[] { byte[].class });
        } catch (InvocationTargetException inv_ex) {
            // Result error from inside getMethod()
            if (inv_ex.getCause() instanceof NoSuchMethodException) {
                return null;
            } else {
                // Odd!
                throw MountiplexUtil.uncheckedRethrow(inv_ex.getCause());
            }
        } catch (Throwable t) {
            // Odd!
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static Method getClassLoaderAPIDefineClassMethod() {
        // Call getDeclaredMethod using reflection to prevent a bytecode manipulating class
        // loader from altering the result so they can point it to their stuff.
        Method getDeclaredMethodMethod;
        try {
            getDeclaredMethodMethod = Class.class.getMethod(String.join("", "get", "Declared", "Method"), String.class, Class[].class);
        } catch (Throwable t) {
            // Odd!
            throw MountiplexUtil.uncheckedRethrow(t);
        }

        // Let's go!
        Method defineClassMethod;
        try {
            defineClassMethod = (Method) getDeclaredMethodMethod.invoke(ClassLoader.class,
                    String.join("", "define", "Class"),
                    new Class[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
        } catch (Throwable t) {
            // Odd!
            throw MountiplexUtil.uncheckedRethrow(t);
        }

        // Make accessible. This fails on JDK16 with illegal access!
        try {
            defineClassMethod.setAccessible(true);
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.severe("Failed to make ClassLoader.defineClass() accessible. Try adding one of these JVM flags:\n" +
                    "  - JDK9 to JDK14: --illegal-access=permit\n" +
                    "  - JDK15 and beyond: --add-opens java.base/java.lang=ALL-UNNAMED");
            throw new UnsupportedOperationException("Failed to make ClassLoader.defineClass() accessible", t);
        }

        return defineClassMethod;
    }

    /**
     * Builds the GeneratorClassLoader implementation source code, with the class
     * name being located below the provided base class.
     *
     * @param baseClass
     * @param prefix
     * @return implementation source
     */
    private static ImplementationSource buildSource(Class<?> baseClass, String prefix) {
        // First: get a unique name for the generator class loader implementation
        // Name may already be used if old loaded classes stick around
        String implName = prefix;
        {
            UniqueHash hash = new UniqueHash();
            while (true) {
                try {
                    Class.forName(baseClass.getName() + "$" + implName);
                } catch (ClassNotFoundException ex) {
                    break;
                }
                implName = prefix + hash.nextHex();
            }
        }

        // Generate the bytecode using ASM
        byte[] byteCode;
        {
            ClassWriter classWriter = new ClassWriter(0);
            MethodVisitor methodVisitor;

            String base_internalName = Type.getInternalName(baseClass);
            String gcl_internalName = Type.getInternalName(GeneratorClassLoader.class);

            classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, base_internalName + "$" + implName, null, gcl_internalName, null);

            {
                // Constructor
                methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/ClassLoader;)V", null, null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, gcl_internalName, "<init>", "(Ljava/lang/ClassLoader;)V", false);
                methodVisitor.visitInsn(RETURN);
                methodVisitor.visitMaxs(2, 2);
                methodVisitor.visitEnd();
            }

            {
                // The defineClass() method
                methodVisitor = classWriter.visitMethod(ACC_PROTECTED, "defineClassFromBytecode", "(Ljava/lang/String;[BLjava/security/ProtectionDomain;)Ljava/lang/Class;", "(Ljava/lang/String;[BLjava/security/ProtectionDomain;)Ljava/lang/Class<*>;", null);
                methodVisitor.visitCode();
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitVarInsn(ALOAD, 2);
                methodVisitor.visitInsn(ICONST_0);
                methodVisitor.visitVarInsn(ALOAD, 2);
                methodVisitor.visitInsn(ARRAYLENGTH);
                methodVisitor.visitVarInsn(ALOAD, 3);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, gcl_internalName, "defineClass", "(Ljava/lang/String;[BIILjava/security/ProtectionDomain;)Ljava/lang/Class;", false);
                methodVisitor.visitInsn(ARETURN);
                methodVisitor.visitMaxs(6, 4);
                methodVisitor.visitEnd();
            }
            classWriter.visitEnd();

            byteCode = classWriter.toByteArray();
        }

        return new ImplementationSource(baseClass.getName() + "$" + implName, byteCode);
    }

    private static class ImplementationSource {
        public final String className;
        public final byte[] byteCode;

        public ImplementationSource(String className, byte[] byteCode) {
            this.className = className;
            this.byteCode = byteCode;
        }
    }
}
