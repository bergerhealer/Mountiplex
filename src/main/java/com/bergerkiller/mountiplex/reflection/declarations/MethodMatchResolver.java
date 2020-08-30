package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Matches fields to methods
 */
public class MethodMatchResolver {

    public static void match(Class<?> declaringClass, ClassResolver resolver, MethodDeclaration[] methods) {
        // Merge declared and public methods as one long array
        // Skip declared methods that are public - they are already in the list
        // Skip methods that are volatile, they are duplicates of non-volatile methods
        // declared in a base class
        MethodDeclaration[] realMethods;
        try {
            realMethods = Stream.concat(
                    Stream.of(declaringClass.getMethods()),
                    Stream.of(declaringClass.getDeclaredMethods())
                            .filter(m -> !Modifier.isPublic(m.getModifiers()))
            ).filter(m -> !Modifier.isVolatile(m.getModifiers()))
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
            if (!found && !method.modifiers.isOptional()) {
                FieldLCSResolver.logAlternatives("method", realMethods, nameResolved, false);
            }
        }
    }
}
