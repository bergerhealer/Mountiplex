package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;

/**
 * Matches fields to methods
 */
public class MethodMatchResolver {

    public static void match(Class<?> declaringClass, ClassResolver resolver, MethodDeclaration[] methods) {
        // Merge declared and public methods as one long list
        // Skip declared methods that are public - they are already in the list
        ArrayList<java.lang.reflect.Method> realRefMethods = new ArrayList<java.lang.reflect.Method>();

        try {
            realRefMethods.addAll(Arrays.asList(declaringClass.getMethods()));
            for (java.lang.reflect.Method decMethod : declaringClass.getDeclaredMethods()) {
                if (Modifier.isPublic(decMethod.getModifiers())) {
                    continue;
                }
                realRefMethods.add(decMethod);
            }
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to identify methods of class " + declaringClass.getName(), t);
            return;
        }

        MethodDeclaration[] realMethods = new MethodDeclaration[realRefMethods.size()];
        for (int i = 0; i < realMethods.length; i++) {
            try {
                realMethods[i] = new MethodDeclaration(resolver, realRefMethods.get(i));
            } catch (Throwable t) {
                if (resolver.getLogErrors()) {
                    MountiplexUtil.LOGGER.log(Level.WARNING, "Failed to read method " + realRefMethods.get(i), t);
                }
            }
        }

        // Connect the methods together
        for (int i = 0; i < methods.length; i++) {
            MethodDeclaration method = methods[i];

            // Ask Resolver for the real method name
            MethodDeclaration nameResolved = method.resolveName();

            boolean found = false;
            for (int j = 0; j < realMethods.length; j++) {
                if (realMethods[j].match(nameResolved)) {

                    // Log a warning when modifiers differ, but do not fail the matching
                    if (!realMethods[j].modifiers.match(method.modifiers)) {
                        MountiplexUtil.LOGGER.log(Level.WARNING, "Method modifiers of " + method.toString() +
                                " do not match (" + realMethods[j].modifiers + " expected)");
                    }

                    method.method = realMethods[j].method;
                    found = true;
                    break;
                }
            }
            if (!found && !method.modifiers.isOptional() && method.body == null) {
                FieldLCSResolver.logAlternatives("method", realMethods, nameResolved);
            }
        }
    }
}
