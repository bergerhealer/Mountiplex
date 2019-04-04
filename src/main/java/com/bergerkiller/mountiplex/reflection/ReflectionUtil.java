package com.bergerkiller.mountiplex.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import com.bergerkiller.mountiplex.reflection.util.ASMUtil;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.DisableFinalModifierHelper;

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

    public static List<SafeField<?>> fillFields(List<SafeField<?>> fields, Class<?> clazz) {
        if (clazz == null) {
            return fields;
        }
        Field[] declared = clazz.getDeclaredFields();
        ArrayList<SafeField<?>> newFields = new ArrayList<SafeField<?>>(declared.length);
        for (Field field : declared) {
            if (!Modifier.isStatic(field.getModifiers())) {
                newFields.add(new SafeField<Object>(field));
            }
        }
        fields.addAll(0, newFields);
        return fillFields(fields, clazz.getSuperclass());
    }

    public static List<FastField<?>> fillFastFields(List<FastField<?>> fields, Class<?> clazz) {
        if (clazz == null) {
            return fields;
        }
        Field[] declared = clazz.getDeclaredFields();
        ArrayList<FastField<?>> newFields = new ArrayList<FastField<?>>(declared.length);
        for (Field field : declared) {
            if (!Modifier.isStatic(field.getModifiers())) {
                FastField<Object> ff = new FastField<Object>();
                ff.init(field);
                newFields.add(ff);
            }
        }
        fields.addAll(0, newFields);
        return fillFastFields(fields, clazz.getSuperclass());
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
        str += " " + method.getName();
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
            return type.getMethod(method.getName(), method.getParameterTypes()) != null;
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

    /**
     * Removes the final field modifier, making a final field writable
     * 
     * @param field
     * @throws IllegalAccessException
     */
    public static void removeFinalModifier(java.lang.reflect.Field field) throws IllegalAccessException {
        DisableFinalModifierHelper.removeFinalModifier(field);
    }
}
