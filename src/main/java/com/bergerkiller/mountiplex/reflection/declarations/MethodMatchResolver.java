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
class MethodMatchResolver {
    private final Class<?> declaringClass;
    private final ClassResolver resolver;
    private final MethodDeclaration[] methods;
    private MethodDeclaration[] realMethods;

    static void resolve(Class<?> declaringClass, ClassResolver resolver, MethodDeclaration[] methods) {
        (new MethodMatchResolver(declaringClass, resolver, methods)).resolve();
    }

    private MethodMatchResolver(Class<?> declaringClass, ClassResolver resolver, MethodDeclaration[] methods) {
        this.declaringClass = declaringClass;
        this.resolver = resolver;
        this.methods = methods;
    }

    void resolve() {
        // Connect the methods together
        for (int i = 0; i < methods.length; i++) {
            MethodDeclaration method = methods[i];
            if (method.body != null) {
                continue; // ignore and keep as-is, has body
            }

            // Ask Resolver for the real method name
            MethodDeclaration nameResolved = method.resolveName();

            // If the Resolver also already has the method assigned to it (remapping rule), instantly
            // return it and skip the LCS nonsense.
            // Also assign to the underlying array
            // This updates the MethodDeclaration method, so it stores the discovered method instance
            if (nameResolved.isDiscovered()) {
                methods[i] = nameResolved;
                continue;
            }

            // Try to assign it to a real existing method by looking at the real methods array
            if (assignRealMethod(method, nameResolved)) {
                methods[i] = nameResolved;
                continue;
            }

            if (!method.modifiers.isOptional()) {
                FieldLCSResolver.logAlternatives("method", getRealMethods(), nameResolved, false);
            }
        }
    }

    private boolean assignRealMethod(MethodDeclaration method, MethodDeclaration nameResolved) {
        // LCS search using all known real method names
        MethodDeclaration[] realMethods = getRealMethods();
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

                // This makes the method 'discovered' (isDiscovered() = true)
                nameResolved.method = realMethod.method;
                nameResolved.constructor = realMethod.constructor;
                return true;
            }
        }

        return false;
    }

    private MethodDeclaration[] getRealMethods() {
        if (realMethods == null) {
            try {
                // Merge declared and public methods as one long array
                // Skip declared methods that are public - they are already in the list
                realMethods = Stream.concat(
                                ReflectionUtil.getDeclaredMethods(declaringClass),
                                ReflectionUtil.getMethods(declaringClass)
                                        .filter(m -> !Modifier.isStatic(m.getModifiers()))
                        ).filter(ReflectionUtil.createDuplicateMethodFilter())
                        .map(m -> new MethodDeclaration(resolver, m))
                        .toArray(MethodDeclaration[]::new);
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to identify methods of class " + MPLType.getName(declaringClass), t);
                realMethods = new MethodDeclaration[0];
            }
        }
        return realMethods;
    }
}
