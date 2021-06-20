package com.bergerkiller.mountiplex.reflection.util.asm;

import static org.objectweb.asm.Opcodes.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;

import javassist.NotFoundException;

/**
 * Generates bytecode by inspecting the details of a Class using Reflection.
 * Makes CtClass objects available for classes that lack an at-runtime .class file
 * to read bytecode from, for example, for classes generated at runtime.<br>
 * <br>
 * If the class is accessible by it's .class file, then that method is preferred.
 */
public class ClassBytecodeLoader {
    /**
     * Default ClassPath that uses this ClassBytecodeLoader to retrieve class data
     */
    public static final javassist.ClassPath CLASSPATH = new CBLObjectClassPath();

    /**
     * Loads the .class bytecode data for a Class.
     * If it can be found through the ClassLoader, then that stream is returned.
     * Otherwise, a mock version of the bytecode is generated that lacks implementation,
     * but covers all the signature methods, fields and constructors of the Class.
     * 
     * @param clazz
     * @return InputStream with the .class bytecode
     */
    public static InputStream getResourceAsStream(Class<?> clazz) {
        String filename = '/' + MPLType.getInternalName(clazz) + ".class";
        InputStream stream = ClassBytecodeLoader.class.getResourceAsStream(filename);
        if (stream == null) {
            stream = new ByteArrayInputStream(generateMockByteCode(clazz));
        }
        return stream;
    }

    /**
     * Finds the URL where the .class bytecode data can be found for a given Class.
     * If it can be found through the ClassLoader, then that data is returned.
     * Otherwise, a mock version of the bytecode is generated that lacks implementation,
     * but covers all the signature methods, fields and constructors of the Class.<br>
     * <br>
     * The actual bytecode is only loaded/generated once the URL connection is opened.
     * 
     * @param clazz
     * @return URL where the .class bytecode can be found
     */
    public static URL getResource(final Class<?> clazz) {
        String filename = '/' + MPLType.getInternalName(clazz) + ".class";
        URL url = ClassBytecodeLoader.class.getResource(filename);
        return (url != null) ? url : generatedURL(clazz, filename);
    }

    /**
     * Generates a mock version of the bytecode that lacks implementation,
     * but covers all the signature methods, fields and constructors of the Class.<br>
     * <br>
     * The actual bytecode is only loaded/generated once the URL connection is opened.
     * 
     * @param clazz
     * @return URL where the .class bytecode can be found
     */
    public static URL getResourceGenerated(final Class<?> clazz) {
        String filename = '/' + MPLType.getInternalName(clazz) + ".class";
        return generatedURL(clazz, filename);
    }

    private static URL generatedURL(final Class<?> clazz, String filename) {
        try {
            return new URL("bytecode", "mountiplex", 0, filename, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) throws IOException {
                    return new URLConnection(u) {
                        @Override
                        public InputStream getInputStream() throws IOException {
                            return new ByteArrayInputStream(generateMockByteCode(clazz));
                        }

                        @Override
                        public void connect() throws IOException {
                        }
                    };
                }
            });
        } catch (MalformedURLException e) {
            // Should never happen
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] generateMockByteCode(Class<?> clazz) {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        // Select superclass. For interface this is null, use Object in that case.
        Class<?> superClass = clazz.getSuperclass();
        if (superClass == null) {
            superClass = Object.class;
        }

        // Generate class definition
        cw.visit(V1_8,
                clazz.getModifiers(),
                MPLType.getInternalName(clazz),
                null, /* signature */
                MPLType.getInternalName(superClass),
                MPLType.getInternalNames(clazz.getInterfaces()));

        // Add all the fields
        for (Field field : clazz.getDeclaredFields()) {
            fv = cw.visitField(field.getModifiers(),
                    MPLType.getName(field),
                    MPLType.getDescriptor(field.getType()),
                    null, /* signature */
                    null  /* value */);
            fv.visitEnd();
        }

        // Add all the constructors
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            mv = cw.visitMethod(constructor.getModifiers(),
                    "<init>",
                    MPLType.getConstructorDescriptor(constructor),
                    null, /* signature */
                    MPLType.getInternalNames(constructor.getExceptionTypes()));
            mv.visitEnd();
        }

        // Add all the methods
        for (Method method : clazz.getDeclaredMethods()) {
            mv = cw.visitMethod(method.getModifiers(),
                    MPLType.getName(method),
                    MPLType.getMethodDescriptor(method),
                    null, /* signature */
                    MPLType.getInternalNames(method.getExceptionTypes()));
            mv.visitEnd();
        }

        // Done
        cw.visitEnd();
        return cw.toByteArray();
    }

    // Used to resolve Java's own types
    private static final javassist.ClassPath SYSTEM = new javassist.ClassClassPath(Object.class);

    private static final class CBLObjectClassPath implements javassist.ClassPath {
        private final ClassLoader mountiplexClassLoader = ClassBytecodeLoader.class.getClassLoader();
        private final ClassLoader fallbackClassLoader = Thread.currentThread().getContextClassLoader();
        private final boolean fallbackIsMountiplex = (mountiplexClassLoader == fallbackClassLoader);

        @Override
        public InputStream openClassfile(String classname) throws NotFoundException {
            InputStream result;

            // Optimization: java types must always be loaded from the system loader
            // This also fixes java.lang.Object being loaded using the wrong ClassLoader
            if (classname.startsWith("java.") && (result = SYSTEM.openClassfile(classname)) != null) {
                return result;
            }

            // Ask ClassLoader first
            // Check loading from .class is allowed
            if (Resolver.canLoadClassPath(classname)) {
                String filename = classname.replace('.', '/') + ".class";

                // The world!
                if ((result = fallbackClassLoader.getResourceAsStream(filename)) != null) {
                    return result;
                }

                // Mountiplex's own types
                if (!fallbackIsMountiplex && (result = mountiplexClassLoader.getResourceAsStream(filename)) != null) {
                    return result;
                }

                // Generated types
                Class<?> generated = GeneratorClassLoader.findGeneratedClass(classname);
                if (generated != null) {
                    return new ByteArrayInputStream(generateMockByteCode(generated));
                }

                // Just in case the check in the beginning missed it: System path
                if ((result = SYSTEM.openClassfile(classname)) != null) {
                    return result;
                }
            }

            // Generate the Class Bytecode by this path
            try {
                Class<?> clazz = Resolver.getClassByExactName(classname);
                return new ByteArrayInputStream(generateMockByteCode(clazz));
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public URL find(String classname) {
            URL result;

            // Optimization: java types must always be loaded from the system loader
            // This also fixes java.lang.Object being loaded using the wrong ClassLoader
            if (classname.startsWith("java.") && (result = SYSTEM.find(classname)) != null) {
                return result;
            }

            // Ask ClassLoader first
            // Check loading from .class is allowed
            String filename = classname.replace('.', '/') + ".class";
            if (Resolver.canLoadClassPath(classname)) {
                // The world!
                if ((result = fallbackClassLoader.getResource(filename)) != null) {
                    return result;
                }

                // Mountiplex's own types
                if (!fallbackIsMountiplex && (result = mountiplexClassLoader.getResource(filename)) != null) {
                    return result;
                }

                // Generated types
                Class<?> generated = GeneratorClassLoader.findGeneratedClass(classname);
                if (generated != null) {
                    return getResourceGenerated(generated);
                }

                // Just in case the check in the beginning missed it: System path
                if ((result = SYSTEM.find(classname)) != null) {
                    return result;
                }
            }

            // Generate the Class Bytecode by this path
            // Is generated when the URL is visited.
            try {
                Class<?> clazz = Resolver.getClassByExactName(classname);
                return generatedURL(clazz, filename);
            } catch (Throwable t) {}

            return null;
        }
    }
}
