package com.bergerkiller.mountiplex.reflection.util.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper interface, implemented at runtime using generated code
 */
public interface MPLTypeHelper {
    String getClassName(Class<?> clazz);
    String getMethodName(Method method);
    String getFieldName(Field field);
    Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException;
    Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException, SecurityException;
    Class<?> getClassByName(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException;
    //Class<?> getFieldDeclaringClass(Field field);
    //Class<?> getMethodDeclaringClass(Method method);
}
