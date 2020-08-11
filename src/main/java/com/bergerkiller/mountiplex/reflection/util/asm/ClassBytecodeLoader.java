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

import javassist.ClassPath;
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
    public static final ClassPath CLASSPATH = new ClassPath() {
        @Override
        public InputStream openClassfile(String classname) throws NotFoundException {
            // Ask ClassLoader first
            String filename = '/' + classname.replace('.', '/') + ".class";
            InputStream stream = ClassBytecodeLoader.class.getResourceAsStream(filename);
            if (stream != null) {
                return stream;
            }

            // Retrieve the Class by this path
            try {
                Class<?> clazz = MPLType.getClassByName(classname, false, ClassBytecodeLoader.class.getClassLoader());
                return new ByteArrayInputStream(generateMockByteCode(clazz));
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public URL find(String classname) {
            // Ask ClassLoader first
            String filename = '/' + classname.replace('.', '/') + ".class";
            URL url = ClassBytecodeLoader.class.getResource(filename);
            if (url != null) {
                return url;
            }

            // Retrieve the Class by this path
            try {
                Class<?> clazz = MPLType.getClassByName(classname, false, ClassBytecodeLoader.class.getClassLoader());
                return generatedURL(clazz, filename);
            } catch (Throwable t) {
                return null;
            }
        }
    };

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

    private static String[] toInternalNames(Class<?>[] types) {
        if (types == null || types.length == 0) {
            return null;
        } else {
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = MPLType.getInternalName(types[i]);
            }
            return names;
        }
    }

    private static byte[] generateMockByteCode(Class<?> clazz) {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        System.out.println("GENERATING " + MPLType.getName(clazz));

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
                toInternalNames(clazz.getInterfaces()));

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
                    toInternalNames(constructor.getExceptionTypes()));
            mv.visitEnd();
        }

        // Add all the methods
        for (Method method : clazz.getDeclaredMethods()) {
            mv = cw.visitMethod(method.getModifiers(),
                    MPLType.getName(method),
                    MPLType.getMethodDescriptor(method),
                    null, /* signature */
                    toInternalNames(method.getExceptionTypes()));
            mv.visitEnd();
        }

        // Done
        cw.visitEnd();
        return cw.toByteArray();
    }
}
