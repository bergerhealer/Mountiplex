package com.bergerkiller.mountiplex.reflection.util.asm;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterListDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;

/**
 * Re-implementation of some methods of ASM's Type class to make use of a
 * runtime-generated helper when resolving classes. This is to make sure that
 * when this library is loaded using a bytecode-editing classloader, the class
 * names stay true to the JVM.
 */
public class MPLType {
    private static final MPLTypeHelper REMAPPING_DISABLED_HELPER = generateNoRemappingHelper();
    private static final MPLTypeHelper REMAPPING_ENABLED_HELPER = new DefaultMPLTypeHelper();
    private static MPLTypeHelper helper = REMAPPING_DISABLED_HELPER;

    /**
     * Sets whether results obtained through this class are remapped by the classloader
     * loading this class. If disabled, code is generated at runtime to avoid interference.
     *
     * @param enabled True if remapping is enabled (allowed), False if it is prevented
     */
    public static void setRemappingEnabled(boolean enabled) {
        helper = enabled ? REMAPPING_ENABLED_HELPER : REMAPPING_DISABLED_HELPER;
    }

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
     * Turns an array of classes into an array of internal names. If the input array
     * is null or empty, then null is returned.
     * 
     * @param types
     * @return names of all the types, or null if types is null or of length 0
     */
    public static String[] getInternalNames(Class<?>[] types) {
        if (types == null || types.length == 0) {
            return null;
        } else {
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                names[i] = getInternalName(types[i]);
            }
            return names;
        }
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
     * Returns the descriptor corresponding to the internal (not exposed)
     * method definition of a method declaration.
     * 
     * @param dec Declaration of the method
     * @return the descriptor of the given method.
     */
    public static String getInternalMethodDescriptor(MethodDeclaration dec) {
        Class<?> returnType = dec.returnType.type;
        Class<?>[] paramTypes = new Class[dec.parameters.parameters.length];
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = dec.parameters.parameters[i].type.type;
        }
        return getMethodDescriptor(returnType, paramTypes);
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
        return getType(clazz).getOpcode(opcode);
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
     * Includes instructions to unbox a boxed value on the stack to its primitive value.
     * <i>void</i> types are turned into <i>null</i>.
     * If the type specified isn't primitive, no instructions are included at all.
     *
     * @param mv method visitor
     * @param primType primitive type to unbox into (Integer -> int)
     */
    public static void visitUnboxVariable(MethodVisitor mv, java.lang.Class<?> primType) {
        if (primType == void.class) {
            mv.visitInsn(ACONST_NULL);
        } else if (primType.isPrimitive()) {
            mv.visitMethodInsn(INVOKEVIRTUAL, MPLType.getInternalName(BoxedType.getBoxedType(primType)),
                    primType.getSimpleName() + "Value", "()" + MPLType.getDescriptor(primType), false);
        }
    }

    /**
     * Visits a variable ILOAD instruction for a given Class type
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param type Type of variable to load
     * @return Next free register to use (register + size)
     */
    public static int visitVarILoad(MethodVisitor mv, int registerInitial, Class<?> type) {
        Type asm_type = Type.getType(type);
        mv.visitVarInsn(asm_type.getOpcode(ILOAD), registerInitial);
        return registerInitial + asm_type.getSize();
    }

    /**
     * Visits a variable ILOAD instruction for every Class type specified, in sequence
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param types The types of variables to load
     * @return Next free register to use (register + size after last type)
     */
    public static int visitVarILoad(MethodVisitor mv, int registerInitial, Class<?>... types) {
        int register = registerInitial;
        for (Class<?> type : types) {
            register = visitVarILoad(mv, register, type);
        }
        return register;
    }

    /**
     * Visits a variable ILOAD instruction for every Class type specified, in sequence,
     * for the types in a parameter list declaration.
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param parameters The types of variables to load
     * @return Next free register to use (register + size after last type)
     */
    public static int visitVarILoad(MethodVisitor mv, int registerInitial, ParameterListDeclaration parameters) {
        int register = registerInitial;
        for (ParameterDeclaration param : parameters.parameters) {
            register = visitVarILoad(mv, register, param.type.type);
        }
        return register;
    }

    /**
     * Visits a variable ILOAD instruction for a given Class type, then adds extra
     * instructions for primitive types to box them to an Object type.
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param type Type of variable to load
     * @return Next free register to use (register + size)
     */
    public static int visitVarILoadAndBox(MethodVisitor mv, int registerInitial, Class<?> type) {
        int register = visitVarILoad(mv, registerInitial, type);
        visitBoxVariable(mv, type);
        return register;
    }

    /**
     * Visits a variable ISTORE instruction for a given Class type
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param type Type of variable to load
     * @return Next free register to use (register + size)
     */
    public static int visitVarIStore(MethodVisitor mv, int registerInitial, Class<?> type) {
        Type asm_type = Type.getType(type);
        mv.visitVarInsn(asm_type.getOpcode(ISTORE), registerInitial);
        return registerInitial + asm_type.getSize();
    }

    /**
     * Visits a variable ISTORE instruction for every Class type specified, in sequence
     * 
     * @param mv Method visitor
     * @param registerInitial Initial register value to load into
     * @param types The types of variables to load
     * @return Next free register to use (register + size after last type)
     */
    public static int visitVarIStore(MethodVisitor mv, int registerInitial, Class<?>... types) {
        int register = registerInitial;
        for (Class<?> type : types) {
            register = visitVarIStore(mv, register, type);
        }
        return register;
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
    //public static Signature createSignature(Method method) {
    //    return new Signature(helper.getMethodName(method), getReturnType(method), getArgumentTypes(method));
   // }

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
        return Resolver.resolveCompiledMethodName(method.getDeclaringClass(),
                helper.getMethodName(method),
                method.getParameterTypes());
    }

    /**
     * Gets the name of a field
     * 
     * @param field
     * @return field name
     */
    public static String getName(Field field) {
        return Resolver.resolveCompiledFieldName(field.getDeclaringClass(),
                helper.getFieldName(field));
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

    /**
     * See: {@link Class#forName(String, boolean, ClassLoader)}
     * 
     * @param name
     * @param initialize
     * @param classLoader
     * @return Class
     * @throws ClassNotFoundException if not found
     */
    public static Class<?> getClassByName(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
        try {
            try {
                return helper.getClassByName(name, initialize, classLoader);
            } catch (IllegalStateException is_ex) {
                if ("zip file closed".equals(is_ex.getMessage()) && classLoader instanceof URLClassLoader) {
                    throw new LoaderClosedException();
                }
                throw new ClassNotFoundException("Failed to load class " + name, is_ex);
            }
        } catch (ClassNotFoundException ex) {
            // Try to load a previously generated class, which might otherwise be difficult to load
            Class<?> generated = GeneratorClassLoader.findGeneratedClass(name);
            if (generated != null) {
                return generated;
            }

            // Failed! Time to throw.
            throw ex;
        }
    }

    /**
     * Gets the class by name. Does <b>not</b> initialize! Uses the class loader of this library.
     * 
     * @param name
     * @return Class
     * @throws ClassNotFoundException if not found
     */
    public static Class<?> getClassByName(String name) throws ClassNotFoundException {
        try {
            try {
                return helper.getClassByName(name, false, MPLType.class.getClassLoader());
            } catch (IllegalStateException is_ex) {
                if ("zip file closed".equals(is_ex.getMessage())) {
                    throw new LoaderClosedException();
                }
                throw new ClassNotFoundException("Failed to load class " + name, is_ex);
            }
        } catch (ClassNotFoundException ex) {
            // Try to load a previously generated class, which might otherwise be difficult to load
            Class<?> generated = GeneratorClassLoader.findGeneratedClass(name);
            if (generated != null) {
                return generated;
            }

            // Failed! Time to throw.
            throw ex;
        }
    }

    /*
    public static Class<?> getDeclaringClass(Field field) {
        return helper.getFieldDeclaringClass(field);
    }

    public static Class<?> getDeclaringClass(Method method) {
        return helper.getMethodDeclaringClass(method);
    }
    */

    @SuppressWarnings("deprecation")
    private static MPLTypeHelper generateNoRemappingHelper() {
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

        // getClassByName
        mv = cw.visitMethod(ACC_PUBLIC, "getClassByName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class<*>;", new String[] { "java/lang/ClassNotFoundException" });
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 4);
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

        GeneratorClassLoader loader = GeneratorClassLoader.get(MPLType.class.getClassLoader());
        Class<?> helperImplType = loader.createClassFromBytecode(MPLType.class.getName() + "$HelperImpl",
                cw.toByteArray(), null, false);

        try {
            return (MPLTypeHelper) helperImplType.newInstance();
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Default implementation that allows for remapping to occur
     */
    private static final class DefaultMPLTypeHelper implements MPLTypeHelper {
        @Override
        public String getClassName(Class<?> clazz) {
            return clazz.getName();
        }

        @Override
        public String getMethodName(Method method) {
            return method.getName();
        }

        @Override
        public String getFieldName(Field field) {
            return field.getName();
        }

        @Override
        public Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
            return clazz.getDeclaredMethod(name, parameterTypes);
        }

        @Override
        public Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException, SecurityException {
            return clazz.getDeclaredField(name);
        }

        @Override
        public Class<?> getClassByName(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
            return Class.forName(name, initialize, classLoader);
        }
    }

    /**
     * Exception thrown when an attempt is made to load a Class from
     * a ClassLoader that has been closed. The caller should clean up
     * and avoid using the ClassLoader a second time.
     */
    public static final class LoaderClosedException extends ClassNotFoundException {
        private static final long serialVersionUID = -2465209759941212720L;

        public LoaderClosedException() {
            super("This ClassLoader is closed");
        }
    }
}
