package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Matches fields to methods
 */
public class MethodMatchResolver {

    public static void match(Class<?> declaringClass, ClassResolver resolver, MethodDeclaration[] methods) {
        // Merge declared and public methods as one long array
        // Skip declared methods that are public - they are already in the list
        MethodDeclaration[] realMethods;
        try {
            realMethods = Stream.concat(
                    ReflectionUtil.getDeclaredMethods(declaringClass),
                    ReflectionUtil.getMethods(declaringClass)
                        .filter(m -> !Modifier.isStatic(m.getModifiers()))
            ).filter(ReflectionUtil.createDuplicateMethodFilter())
             .map(m -> new MethodDeclaration(resolver, m))
             .toArray(MethodDeclaration[]::new);
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to identify methods of class " + MPLType.getName(declaringClass), t);
            return;
        }

        // Connect the methods together
        for (int i = 0; i < methods.length; i++) {
            MethodDeclaration method = methods[i];
            if (method.body != null) {
                continue; // ignore, has body
            }

            // Ask Resolver for the real method name
            MethodDeclaration nameResolved = method.resolveName();

            boolean found = false;
            for (MethodDeclaration realMethod : realMethods) {
                if (realMethod.match(nameResolved)) {
                    // Log a warning when modifiers differ, but do not fail the matching
                    if (!realMethod.modifiers.match(method.modifiers)) {
                        // Log a warning when modifiers differ in a way that the method is more privated than expected
                        // For example, declaration says public, but the method is private/protected
                        //
                        // In the past we would log all the time, but it is a little spammy with no real value
                        if (realMethod.modifiers.getProtectionLevel() > method.modifiers.getProtectionLevel()) {
                            MountiplexUtil.LOGGER.log(Level.WARNING, "Method modifiers of " +
                                    resolver.getDeclaredClassName() + " " + method.toString() +
                                    " do not match (" + realMethod.modifiers + " expected)");
                        }
                    }

                    method.method = realMethod.method;
                    method.constructor = realMethod.constructor;
                    found = true;
                    break;
                }
            }
            if (!found && !method.modifiers.isOptional()) {
                FieldLCSResolver.logAlternatives("method", realMethods, nameResolved, false);
            }
        }
    }
}
