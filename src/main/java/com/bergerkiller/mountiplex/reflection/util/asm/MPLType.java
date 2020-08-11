package com.bergerkiller.mountiplex.reflection.util.asm;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.bergerkiller.mountiplex.MountiplexUtil;

import net.sf.cglib.core.Signature;

/**
 * Re-implementation of some methods of ASM's Type class to make use of the
 * {@link ReflectionInfoHelper} when resolving classes. This is to make sure that
 * when this library is loaded using a bytecode-editing classloader, the class
 * names stay true to the JVM.
 */
public class MPLType {

    /**
     * Returns the internal name of the given class. The internal name of a class is its fully
     * qualified name, as returned by Class.getName(), where '.' are replaced by '/'.
     *
     * @param clazz an object or array class.
     * @return the internal name of the given class.
     */
    public static String getInternalName(final Class<?> clazz) {
        return helper.getClassName(clazz).replace('.', '/');
    }

    /**
     * Returns the descriptor corresponding to the given class.
     *
     * @param clazz an object class, a primitive class or an array class.
     * @return the descriptor corresponding to the given class.
     */
    public static String getDescriptor(final Class<?> clazz) {
        StringBuilder stringBuilder = new StringBuilder();
        appendDescriptor(clazz, stringBuilder);
        return stringBuilder.toString();
    }

    /**
     * Returns the descriptor corresponding to the given constructor.
     *
     * @param constructor a {@link Constructor} object.
     * @return the descriptor of the given constructor.
     */
    public static String getConstructorDescriptor(final Constructor<?> constructor) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('(');
        Class<?>[] parameters = constructor.getParameterTypes();
        for (Class<?> parameter : parameters) {
            appendDescriptor(parameter, stringBuilder);
        }
        return stringBuilder.append(")V").toString();
    }

    /**
     * Returns the descriptor corresponding to the given method.
     *
     * @param method a {@link Method} object.
     * @return the descriptor of the given method.
     */
    public static String getMethodDescriptor(final Method method) {
        return getMethodDescriptor(method.getReturnType(), method.getParameterTypes());
    }

    /**
     * Returns the descriptor corresponding to the given method.
     * 
     * @param returnType The return type of the method
     * @param parameterTypes The parameters of the method
     * @return the descriptor of the given method.
     */
    public static String getMethodDescriptor(Class<?> returnType, Class<?>[] parameterTypes) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('(');
        for (Class<?> parameter : parameterTypes) {
            appendDescriptor(parameter, stringBuilder);
        }
        stringBuilder.append(')');
        appendDescriptor(returnType, stringBuilder);
        return stringBuilder.toString();
    }

    /**
     * Appends the descriptor of the given class to the given string builder.
     *
     * @param clazz the class whose descriptor must be computed.
     * @param stringBuilder the string builder to which the descriptor must be appended.
     */
    private static void appendDescriptor(final Class<?> clazz, final StringBuilder stringBuilder) {
        Class<?> currentClass = clazz;
        while (currentClass.isArray()) {
            stringBuilder.append('[');
            currentClass = currentClass.getComponentType();
        }
        if (currentClass.isPrimitive()) {
            char descriptor;
            if (currentClass == Integer.TYPE) {
                descriptor = 'I';
            } else if (currentClass == Void.TYPE) {
                descriptor = 'V';
            } else if (currentClass == Boolean.TYPE) {
                descriptor = 'Z';
            } else if (currentClass == Byte.TYPE) {
                descriptor = 'B';
            } else if (currentClass == Character.TYPE) {
                descriptor = 'C';
            } else if (currentClass == Short.TYPE) {
                descriptor = 'S';
            } else if (currentClass == Double.TYPE) {
                descriptor = 'D';
            } else if (currentClass == Float.TYPE) {
                descriptor = 'F';
            } else if (currentClass == Long.TYPE) {
                descriptor = 'J';
            } else {
                throw new AssertionError();
            }
            stringBuilder.append(descriptor);
        } else {
            stringBuilder.append('L');
            String name = helper.getClassName(currentClass);
            int nameLength = name.length();
            for (int i = 0; i < nameLength; ++i) {
                char car = name.charAt(i);
                stringBuilder.append(car == '.' ? '/' : car);
            }
            stringBuilder.append(';');
        }
    }

    /**
     * Returns a JVM instruction opcode adapted to this Class.
     * 
     * @param clazz The type of class to adapt the opcode for
     * @param opcode The opcode to adapt
     * @return adapted opcode
     */
    public static int getOpcode(Class<?> clazz, int opcode) {
        return Type.getType(clazz).getOpcode(opcode);
    }

    /**
     * Visits a variable ILOAD instruction for a given Class type
     * 
     * @param mv Method visitor
     * @param clazz Type of variable to load
     * @param register The register to load into
     * @return Next free register to use (register + size)
     */
    public static int visitVarILoad(MethodVisitor mv, Class<?> clazz, int register) {
        Type type = Type.getType(clazz);
        mv.visitVarInsn(type.getOpcode(ILOAD), register);
        return register + type.getSize();
    }

    /**
     * Returns the {@link Type} corresponding to the return type of the given method.
     *
     * @param method a method.
     * @return the {@link Type} corresponding to the return type of the given method.
     */
    public static Type getReturnType(final Method method) {
      return getType(method.getReturnType());
    }

    /**
     * Returns the {@link Type} values corresponding to the argument types of the given method.
     *
     * @param method a method.
     * @return the {@link Type} values corresponding to the argument types of the given method.
     */
    public static Type[] getArgumentTypes(final Method method) {
      Class<?>[] classes = method.getParameterTypes();
      Type[] types = new Type[classes.length];
      for (int i = classes.length - 1; i >= 0; --i) {
        types[i] = getType(classes[i]);
      }
      return types;
    }

    /**
     * Gets an array of types using an array of classes
     * 
     * @param classes
     * @return types
     */
    public static Type[] getTypes(Class<?>... classes) {
        Type[] types = new Type[classes.length];
        for (int i = 0; i < classes.length; i++) {
            types[i] = getType(classes[i]);
        }
        return types;
    }

    /**
     * Returns the {@link Type} corresponding to the given class.
     * A guarantee is made that a custom bytecode-editing classloader
     * loading this library won't interfere.
     *
     * @param clazz a class.
     * @return the {@link Type} corresponding to the given class.
     */
    public static Type getType(final Class<?> clazz) {
      if (clazz.isPrimitive()) {
        if (clazz == Integer.TYPE) {
          return Type.INT_TYPE;
        } else if (clazz == Void.TYPE) {
          return Type.VOID_TYPE;
        } else if (clazz == Boolean.TYPE) {
          return Type.BOOLEAN_TYPE;
        } else if (clazz == Byte.TYPE) {
          return Type.BYTE_TYPE;
        } else if (clazz == Character.TYPE) {
          return Type.CHAR_TYPE;
        } else if (clazz == Short.TYPE) {
          return Type.SHORT_TYPE;
        } else if (clazz == Double.TYPE) {
          return Type.DOUBLE_TYPE;
        } else if (clazz == Float.TYPE) {
          return Type.FLOAT_TYPE;
        } else if (clazz == Long.TYPE) {
          return Type.LONG_TYPE;
        } else {
          throw new AssertionError();
        }
      } else {
        return Type.getType(getDescriptor(clazz));
      }
    }

    /**
     * Creates a CGLib Signature of a method
     * 
     * @param method
     * @return signature
     */
    public static Signature createSignature(Method method) {
        return new Signature(helper.getMethodName(method), getReturnType(method), getArgumentTypes(method));
    }

    /**
     * Gets the name of a Class
     * 
     * @param clazz
     * @return class name
     */
    public static String getName(Class<?> clazz) {
        return helper.getClassName(clazz);
    }

    /**
     * Gets the name of a method
     * 
     * @param method
     * @return method name
     */
    public static String getName(Method method) {
        return helper.getMethodName(method);
    }

    /**
     * Gets the name of a field
     * 
     * @param field
     * @return field name
     */
    public static String getName(Field field) {
        return helper.getFieldName(field);
    }

    /**
     * Looks up a method declared in a Class
     * 
     * @param clazz Class to find the method in
     * @param name Name of the method
     * @param parameterTypes Parameters of the method
     * @return the method
     * @throws NoSuchMethodException
     * @throws SecurityException
     */
    public static Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
        return helper.getDeclaredMethod(clazz, name, parameterTypes);
    }

    /**
     * Looks up a field declared in a Class
     * 
     * @param clazz Class to find the field in
     * @param name Name of the field
     * @return the field
     * @throws NoSuchFieldException
     * @throws SecurityException
     */
    public static Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException, SecurityException {
        return helper.getDeclaredField(clazz, name);
    }

    /*
    public static Class<?> getDeclaringClass(Field field) {
        return helper.getFieldDeclaringClass(field);
    }

    public static Class<?> getDeclaringClass(Method method) {
        return helper.getMethodDeclaringClass(method);
    }
    */

    private static final MPLTypeHelper helper;

    // Solely used to generate the helper implementation, is then discarded
    // Only the MPLTypeHelper generated class holds a reference to it, then
    private static final class MPLTypeHelperClassLoader extends ClassLoader {
        public MPLTypeHelperClassLoader(ClassLoader base) {
            super(base);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    static {
        String interfaceName = MPLTypeHelper.class.getName().replace('.', '/');
        String internalName = MPLType.class.getName().replace('.', '/') + "$HelperImpl";
        String signature = "Ljava/lang/Object;L" + interfaceName + ";";
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_8, ACC_PUBLIC | ACC_STATIC, internalName, signature, "java/lang/Object", new String[] {interfaceName});

        MethodVisitor mv;

        // Empty constructor
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // getClassName
        mv = cw.visitMethod(ACC_PUBLIC, "getClassName", "(Ljava/lang/Class;)Ljava/lang/String;", "(Ljava/lang/Class<*>;)Ljava/lang/String;", null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        // getMethodName
        mv = cw.visitMethod(ACC_PUBLIC, "getMethodName", "(Ljava/lang/reflect/Method;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        // getFieldName
        mv = cw.visitMethod(ACC_PUBLIC, "getFieldName", "(Ljava/lang/reflect/Field;)Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getName", "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        // getDeclaredMethod
        mv = cw.visitMethod(ACC_PUBLIC | ACC_VARARGS, "getDeclaredMethod", "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", "(Ljava/lang/Class<*>;Ljava/lang/String;[Ljava/lang/Class<*>;)Ljava/lang/reflect/Method;", new String[] { "java/lang/NoSuchMethodException", "java/lang/SecurityException" });
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 4);
        mv.visitEnd();

        // getDeclaredField
        mv = cw.visitMethod(ACC_PUBLIC, "getDeclaredField", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;", "(Ljava/lang/Class<*>;Ljava/lang/String;)Ljava/lang/reflect/Field;", new String[] { "java/lang/NoSuchFieldException", "java/lang/SecurityException" });
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 3);
        mv.visitEnd();

        /*
        // getFieldDeclaringClass
        mv = cw.visitMethod(ACC_PUBLIC, "getFieldDeclaringClass", "(Ljava/lang/reflect/Field;)Ljava/lang/Class;", "(Ljava/lang/reflect/Field;)Ljava/lang/Class<*>;", null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getDeclaringClass", "()Ljava/lang/Class;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        // getMethodDeclaringClass
        mv = cw.visitMethod(ACC_PUBLIC, "getMethodDeclaringClass", "(Ljava/lang/reflect/Method;)Ljava/lang/Class;", "(Ljava/lang/reflect/Method;)Ljava/lang/Class<*>;", null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "getDeclaringClass", "()Ljava/lang/Class;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        */

        cw.visitEnd();

        MPLTypeHelperClassLoader loader = new MPLTypeHelperClassLoader(MPLType.class.getClassLoader());
        Class<?> helperImplType = loader.defineClass(MPLType.class.getName() + "$HelperImpl", cw.toByteArray());

        try {
            helper = (MPLTypeHelper) helperImplType.newInstance();
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
