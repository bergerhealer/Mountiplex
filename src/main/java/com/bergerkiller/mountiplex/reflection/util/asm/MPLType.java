package com.bergerkiller.mountiplex.reflection.util.asm;

import static org.objectweb.asm.Opcodes.ILOAD;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.bergerkiller.mountiplex.reflection.resolver.Resolver;

/**
 * Re-implementation of some methods of Javassist's Type class to take into account the
 * Resolver getClassName(clazz) method.
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
        return Resolver.resolveClassName(clazz).replace('.', '/');
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
            String name = Resolver.resolveClassName(currentClass);
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
}
