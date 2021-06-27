package com.bergerkiller.mountiplex.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.MethodSignature;
import com.bergerkiller.mountiplex.reflection.util.asm.ASMUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

public class ReflectionUtil {

    /// removes generics from a field/method declaration
    /// example: Map<String, String> stuff -> Map stuff
    public static String filterGenerics(String input) {
        int genEnd = input.indexOf('>');
        if (genEnd == -1) {
            return input;
        }
        int genStart = input.lastIndexOf('<', genEnd);
        if (genStart == -1) {
            return input;
        }
        return filterGenerics(input.substring(0, genStart) + input.substring(genEnd + 1));
    }

    /**
     * Gets the String expression of a type. Shortens builtin type names
     * and properly resolves array types.
     * 
     * @param type
     * @return type name
     */
    public static String getTypeName(Class<?> type) {
        String name;
        int numArrays = 0;
        while (type.isArray()) {
            type = type.getComponentType();
            numArrays++;
        }
        if (type.isPrimitive() || BoxedType.getUnboxedType(type) != null) {
            name = type.getSimpleName();
        } else {
            name = MPLType.getName(type);
        }
        for (int i = 0; i < numArrays; i++) {
            name += "[]";
        }
        return name;
    }

    /**
     * Gets the String expression of a type. Shortens builtin type names
     * and properly resolves array types. If the input type is private,
     * Object is returned instead to guarantee the name used can be
     * compiled. If resolving the type name could result in a different
     * type than intended, a prefix is included that prevents that.
     *
     * @param type Type to get the name of
     * @return accessible type name
     */
    public static String getAccessibleTypeName(Class<?> type) {
        if (Resolver.isPublic(type)) {
            String name = getTypeName(type);

            // If a resolver would 'double-resolve' the type name, prefix with $mpl
            // This prevents accidents like that
            if (!Resolver.resolveClassPath(name).equals(name)) {
                name = MPLMemberResolver.IGNORE_PREFIX + name;
            }

            return name;
        } else {
            return "Object";
        }
    }

    /**
     * Similar to {@link #getAccessibleTypeName(Class)}, but surrounds
     * the result in parenthesis to make a type cast expression.
     * If the result is Object, then an empty String is returned
     * to indicate no casting is required.
     *
     * @param type Type to get the cast expression of
     * @return accessible cast expression
     */
    public static String getAccessibleTypeCast(Class<?> type) {
        if (type != Object.class && Resolver.isPublic(type)) {
            String name = getTypeName(type);

            // If a resolver would 'double-resolve' the type name, prefix with $mpl
            // This prevents accidents like that
            if (Resolver.resolveClassPath(name).equals(name)) {
                return '(' + name + ')';
            } else {
                name = '(' + MPLMemberResolver.IGNORE_PREFIX + name + ')';
            }

            return name;
        } else {
            return "";
        }
    }

    /**
     * Gets a cast String expression, for example (String)
     * 
     * @param type
     * @return cast expression
     */
    public static String getCastString(Class<?> type) {
        StringBuilder body = new StringBuilder();
        body.append('(');
        body.append(getTypeName(type));
        body.append(')');
        return body.toString();
    }

    /// parses method/field modifier lists
    public static int parseModifiers(String[] parts, int count) {
        // Read modifiers
        int modifiers = 0;
        for (int i = 0; i < count; i++) {
            if (parts[i].equals("public")) {
                modifiers |= Modifier.PUBLIC;
            } else if (parts[i].equals("private")) {
                modifiers |= Modifier.PRIVATE;
            } else if (parts[i].equals("protected")) {
                modifiers |= Modifier.PROTECTED;
            } else if (parts[i].equals("final")) {
                modifiers |= Modifier.FINAL;
            } else if (parts[i].equals("static")) {
               modifiers |= Modifier.STATIC;
            } else if (parts[i].equals("volatile")) {
               modifiers |= Modifier.VOLATILE; 
            } else if (parts[i].equals("abstract")) {
                modifiers |= Modifier.ABSTRACT;
            }
        }
        return modifiers;
    }

    public static boolean compareModifiers(int m1, int m2) {
        return (Modifier.isPrivate(m1) == Modifier.isPrivate(m2) &&
                Modifier.isPublic(m1) == Modifier.isPublic(m2) &&
                Modifier.isProtected(m1) == Modifier.isProtected(m2) &&
                Modifier.isStatic(m1) == Modifier.isStatic(m2) &&
                Modifier.isFinal(m1) == Modifier.isFinal(m2));
    }

    /**
     * Gets a stream of the class and all declaring classes it sits inside of.
     * If clazz is null, an empty stream is returned.
     * 
     * @param clazz
     * @return stream starting with clazz, following all declaring classes in sequence
     */
    public static Stream<Class<?>> getAllDeclaringClasses(Class<?> clazz) {
        return MountiplexUtil.<Class<?>>iterateNullTerminated(clazz, Class::getDeclaringClass);
    }

    /**
     * Gets a stream of all superclasses represented by a type.
     * If clazz is null, an empty stream is returned.
     * 
     * @param clazz
     * @return stream starting with clazz, following all superclasses in sequence
     */
    public static Stream<Class<?>> getAllClasses(Class<?> clazz) {
        return MountiplexUtil.<Class<?>>iterateNullTerminated(clazz, Class::getSuperclass);
    }

    /**
     * Gets a stream of all superclasses and interfaces represented by a type.
     * If clazz is null, an empty stream is returned.
     * Duplicate interfaces implemented by multiple classes are removed.
     * 
     * @param clazz
     * @return stream starting with clazz, following all superclasses, then all interfaces,
     *         in sequence.
     */
    public static Stream<Class<?>> getAllClassesAndInterfaces(Class<?> clazz) {
        return Stream.concat(getAllClasses(clazz),
                getAllClasses(clazz)
                        .flatMap(ReflectionUtil::discoverAllInterfaces)
                        .distinct());
    }

    // Recursively figures out all the interfaces that exist for a type
    // Interfaces of interfaces are also included
    private static Stream<Class<?>> discoverAllInterfaces(Class<?> type) {
        Class<?>[] interfaces = type.getInterfaces();
        return Stream.concat(Stream.of(interfaces), Stream.of(interfaces)
                .flatMap(ReflectionUtil::discoverAllInterfaces));
    }

    /**
     * Gets a stream of methods declared inside a Class
     * 
     * @param clazz
     * @return declared methods
     */
    public static Stream<Method> getDeclaredMethods(Class<?> clazz) {
        return Stream.of(clazz.getDeclaredMethods());
    }

    /**
     * Gets all methods declared in a Class, its superclasses and
     * all its interfaces all the way down. If the input class is null,
     * then an empty stream is returned. Duplicate method
     * signatures are not removed.
     * 
     * @param clazz
     * @return stream of methods declared in the clazz, its superclasses and interfaces
     */
    public static Stream<Method> getAllMethods(Class<?> clazz) {
        return getAllClassesAndInterfaces(clazz)
                .flatMap(ReflectionUtil::getDeclaredMethods);
    }

    /**
     * Returns a predicate that will filter all duplicate method signatures.
     * Only member methods are filtered, since static methods don't override
     * each other and will always be unique.
     *
     * @return duplicate method filter
     */
    public static Predicate<Method> createDuplicateMethodFilter() {
        final java.util.HashSet<MethodSignature> filtered = new java.util.HashSet<MethodSignature>();
        return method -> Modifier.isStatic(method.getModifiers()) || filtered.add(new MethodSignature(method));
    }

    /**
     * Gets all fields declared in a Class and its superclasses.
     * If the input class is null, then an empty stream is returned.
     * 
     * @param clazz Type to look from
     * @return stream of fields declared in the clazz and its superclasses
     */
    public static Stream<Field> getAllFields(Class<?> clazz) {
        return MountiplexUtil.<Class<?>>iterateNullTerminated(clazz, Class::getSuperclass)
                .flatMap(t -> Stream.of(t.getDeclaredFields()));
    }

    /**
     * Applies a non-static modifier filter to the result of {@link #getAllFields(Class)}.
     * 
     * @param clazz
     * @return stream of non-static fields declared in the clazz and its superclasses
     */
    public static Stream<Field> getAllNonStaticFields(Class<?> clazz) {
        return getAllFields(clazz).filter(m -> !Modifier.isStatic(m.getModifiers()));
    }

    /**
     * Applies a static modifier filter to the result of {@link #getAllFields(Class)}.
     * 
     * @param clazz
     * @return stream of static fields declared in the clazz and its superclasses
     */
    public static Stream<Field> getAllStaticFields(Class<?> clazz) {
        return getAllFields(clazz).filter(m -> Modifier.isStatic(m.getModifiers()));
    }

    public static String stringifyType(Class<?> type) {
        return (type == null ? "[null]" : type.getSimpleName());
    }

    /**
     * Produces a human-readable version of the method signature for logging purposes
     * 
     * @param method to stringify
     * @return stringified method signature
     */
    public static String stringifyMethodSignature(Method method) {
        String str = Modifier.toString(method.getModifiers());
        str += " " + stringifyType(method.getReturnType());
        str += " " + MPLType.getName(method);
        str += "(";
        boolean first = true;
        for (Class<?> param : method.getParameterTypes()) {
            if (first) {
                first = false;
            } else {
                str += ", ";
            }
            str += stringifyType(param);
        }
        str += ")";
        return str;
    }

    private static boolean hasMethod(Class<?> type, Method method) {
        try {
            String name = MPLType.getName(method);
            return MPLType.getDeclaredMethod(type, name, method.getParameterTypes()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Attempts to find the base class or interface in which a particular method was originally declared.
     * 
     * @param method to find
     * @return Class where it is defined
     */
    public static Class<?> findMethodClass(Method method) {
        Class<?> type = method.getDeclaringClass();
        for (Class<?> iif : type.getInterfaces()) {
            if (hasMethod(iif, method)) {
                return iif;
            }
        }
        Class<?> lowestSubClass = type;
        while ((type = type.getSuperclass()) != null) {
            if (hasMethod(type, method)) {
                lowestSubClass = type;
            }
        }
        return lowestSubClass;
    }

    /**
     * Recursively looks up the Class hierarchy to find the first annotation of a given type.
     * If found, the property of the annotation as specified is returned. If not found, the
     * default value is returned instead.
     * 
     * @param type The Class type from which to recursively look for the annotation
     * @param annotationClass Annotation class type to find
     * @param method Method of the annotation class type to call
     * @param defaultValue Default value to return if the annotation is not found
     * @return value
     */
    public static <A extends Annotation, V> V recurseFindAnnotationValue(java.lang.Class<?> type, java.lang.Class<A> annotationClass, Function<A, V> method, V defaultValue) {
        return getAllDeclaringClasses(type)
            .map(t -> t.getAnnotation(annotationClass))
            .filter(Objects::nonNull)
            .map(method)
            .findFirst().orElse(defaultValue);
    }

    /**
     * Simplifies the exception thrown when invoking a Method and throws it. This prevents very long InvocationTargetException
     * errors that barely show what actually went wrong, and instead shows only the actual exception that occurred.
     * Parameters and instance are also type-checked in case there is an error there.
     *
     * @param method that was invoked
     * @param instance the method was invoked on
     * @param args arguments passed in
     * @param ex exception that was thrown
     * @return a suitable exception to throw
     */
    public static RuntimeException fixMethodInvokeException(Method method, Object instance, Object[] args, Throwable ex) {
        RuntimeException result = null;
        if (ex instanceof InvocationTargetException) {
            // In this case we know for certain something got invoked
            // There is no use checking for argument mismatches here
            ex = getCleanCause(ex);
        } else if (ex instanceof IllegalAccessException) {
            // Problems accessing a method. Arguments do not matter here.
            // Illegal access exception thrown by invoke() - wrap in a clean runtime exception
            filterInvokeTraceElements(ex, 0);
            result = new RuntimeException("Failed to invoke method " + stringifyMethodSignature(method), ex);
            result.setStackTrace(new StackTraceElement[0]);
            return result;
        } else {
            // Validate the instance object that was used to invoke the method
            if (Modifier.isStatic(method.getModifiers())) {
                if (instance != null) {
                    result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                            " is static and can not be invoked on instance of type " + stringifyType(instance.getClass()));
                }
            } else {
                if (instance == null) {
                    result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                            " is not static and requires an instance passed in (instance is null)");
                } else {
                    Class<?> m_type = findMethodClass(method);
                    if (!m_type.isInstance(instance)) {
                        result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                                " is declared in class " + stringifyType(m_type) +
                                " and can not be invoked on object of type " + stringifyType(instance.getClass()));
                    }
                }
            }

            // Validate the parameters used to invoke the method
            if (result == null) {
                Class<?>[] m_params = method.getParameterTypes();
                if (args.length != m_params.length) {
                    result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                            " Illegal number of arguments provided. " + 
                            "Expected " + m_params.length + ", but got " + args.length);
                } else {
                    for (int i = 0; i < m_params.length; i++) {
                        Object arg = args[i];
                        if (m_params[i].isPrimitive() && arg == null) {
                            result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                                    " Passed in null for primitive type parameter #" + (i + 1));
                            break;
                        } else if (arg != null && !BoxedType.tryBoxType(m_params[i]).isInstance(arg)) {
                            result = new IllegalArgumentException("Method " + stringifyMethodSignature(method) +
                                    " Passed in wrong type for parameter #" + (i + 1) + " (" + stringifyType(m_params[i]) + " expected" +
                                    ", but was " + stringifyType(arg.getClass()) + ")");
                            break;
                        }
                    }
                }
            }

            // This probably will never happen, but in case we miss parameter checks
            if (result == null && ex instanceof IllegalArgumentException) {
                result = (IllegalArgumentException) ex;
            }

            // For exception created in here, take over stack trace and filter the invoke() elements
            if (result != null) {
                result.setStackTrace(ex.getStackTrace());
                filterInvokeTraceElements(result, 0);
                addMethodTraceElement(result, method);
                return result;
            }
        }

        // When errors happen in the static initializer, show that cleanly
        if (ex instanceof ExceptionInInitializerError) {
            ExceptionInInitializerError e = (ExceptionInInitializerError) ex;
            filterCause(e);
            filterInvokeTraceElements(e, 0);
        }

        // Re-throw unchecked errors
        if (ex instanceof Error) {
            throw (Error) ex;
        }

        // Wrap the exception as a proper RuntimeException so it doesn't show a pointless 'caused by'
        if (ex instanceof RuntimeException) {
            result = ((RuntimeException) ex);
        } else {
            result = new RuntimeException("Failed to invoke method " + stringifyMethodSignature(method), ex);
            result.setStackTrace(new StackTraceElement[0]);
        }
        return result;
    }

    private static Throwable getCleanCause(Throwable ex) {
        Throwable cause = ex.getCause();
        int index = cause.getStackTrace().length - ex.getStackTrace().length;
        filterInvokeTraceElements(cause, index);
        return cause;
    }

    private static void filterCause(Throwable t) {
        if (t.getCause() != null) {
            filterCause(t.getCause());

            List<StackTraceElement> newElem = new ArrayList<StackTraceElement>(Arrays.asList(t.getCause().getStackTrace()));
            StackTraceElement[] parentElem = t.getStackTrace();
            for (int i = parentElem.length - 1; i >= 0; --i) {
                if (newElem.get(newElem.size() - 1).equals(parentElem[i])) {
                    newElem.remove(newElem.size() - 1);
                } else {
                    break;
                }
            }
            t.getCause().setStackTrace(newElem.toArray(new StackTraceElement[newElem.size()]));
        }
    }

    /**
     * Removes invoke() call information from a stack trace
     * 
     * @param t throwable containing the stack trace
     * @param offset amount of elements to skip checking
     */
    private static void filterInvokeTraceElements(Throwable t, int offset) {
        List<StackTraceElement> stack_trace = new ArrayList<StackTraceElement>(Arrays.asList(t.getStackTrace()));
        if (offset >= 0 && offset < stack_trace.size()) {
            ListIterator<StackTraceElement> iter = stack_trace.listIterator(offset);
            while (iter.hasNext()) {
                //java.lang.reflect.Method.invoke
                StackTraceElement e = iter.next();
                String c = e.getClassName();
                if (c.equals("java.lang.reflect.Method") && e.getMethodName().equals("invoke")) {
                    break;
                }
                if (c.startsWith("java.lang.reflect.") || c.startsWith("sun.reflect.")) {
                    iter.remove();
                } else {
                    break;
                }
            }
            t.setStackTrace(stack_trace.toArray(new StackTraceElement[stack_trace.size()]));
        }
    }

    /**
     * Adds the method as a stack trace element at the top of the stack'
     * 
     * @param t throwable to change the stack trace of
     * @param m method to add at the top
     */
    private static void addMethodTraceElement(Throwable t, Method m) {
        StackTraceElement[] oldTrace = t.getStackTrace();
        StackTraceElement[] newTrace = new StackTraceElement[oldTrace.length + 1];
        System.arraycopy(oldTrace, 0, newTrace, 1, oldTrace.length);
        newTrace[0] = ASMUtil.findMethodDetails(m);
        t.setStackTrace(newTrace);
    }
}
