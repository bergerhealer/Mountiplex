package com.bergerkiller.mountiplex.reflection.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Uses as a static instance variable in a class to help with static initialization of a Class.
 * Calls all the static methods annotated with the {@link InitMethod} annotation, optionally
 * passing along the parameters specified if they match the method signature. It is strongly
 * discouraged to use null parameter arguments, as their type can not be resolved to match
 * method signatures correctly.
 */
public class StaticInitHelper {

    public StaticInitHelper(Class<?> type, Object... parameters) {
        Class<?> currentType = type;
        ArrayList<Object> argsBuff = new ArrayList<Object>();
        while (currentType != null && InitClass.class.isAssignableFrom(currentType)) {
            for (java.lang.reflect.Method method : currentType.getDeclaredMethods()) {
                if (method.getAnnotation(InitMethod.class) == null) {
                    continue;
                }

                // Collect all the parameters we can send to the method with this signature
                argsBuff.clear();
                argsBuff.add(type);
                argsBuff.addAll(Arrays.asList(parameters));
                Iterator<Object> argsIter = argsBuff.iterator();
                boolean canCallMethod = true;
                for (Class<?> paramType : method.getParameterTypes()) {
                    while (true) {
                        if (!argsIter.hasNext()) {
                            canCallMethod = false;
                            break;
                        }
                        Object arg = argsIter.next();
                        if (arg == null || paramType.isAssignableFrom(arg.getClass())) {
                            break; // allowed
                        } else {
                            argsIter.remove();
                        }
                    }
                    if (!canCallMethod) {
                        break;
                    }
                }

                if (canCallMethod) {
                    try {
                        method.setAccessible(true);
                        method.invoke(null, argsBuff.toArray());
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to call initializer function for type " + type.getName(), t);
                    }
                }
            }
            currentType = currentType.getSuperclass();
        }
    }

    /**
     * Dummy method, whose sole purpose is to instantiate whatever
     * field is storing this StaticInitHelper, and all other StaticInitHelper
     * instances stored in superclass types.
     */
    public final void init() {
    }

    /**
     * Declares that a method is a static initializer method,
     * to be called when the class is initialized.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface InitMethod {
    }

    /**
     * Declares a class to contain a StaticInitHelper, a hint for class initialization functions
     */
    public interface InitClass {
    }

    public static void initType(Class<?> type) {
        Class<?> currentType = type;
        while (currentType != null && InitClass.class.isAssignableFrom(currentType)) {
            // Find the first static StaticInitHelper field we find and call a function on it
            // We only have to do this for the first one we find, because it will initializer
            // the super classes the same way
            for (java.lang.reflect.Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(StaticInitHelper.class)) {
                    try {
                        field.setAccessible(true);
                        StaticInitHelper helper = (StaticInitHelper) field.get(null);
                        if (helper != null) {
                            helper.init();
                        }
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to initialize " + currentType.getName(), t);
                    }
                    return;
                }
            }

            currentType = currentType.getSuperclass();
        }
    }
}
