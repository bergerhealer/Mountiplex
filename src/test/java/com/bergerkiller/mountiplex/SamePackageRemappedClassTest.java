package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassPathResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.types.samepackage.DeclaredPackageType;
import com.bergerkiller.mountiplex.types.samepackage.SamePackageType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Registers a class path resolver that remaps a class to a different name, but that remapped class
 * also actually exists. Verifies that the remapper chooses the remapped class over the same-package
 * class.<br>
 * <br>
 * This was a bug previously, where an remap existed from net.minecraft.nbt.Tag -> net.minecraft.server.NBTBase,
 * but because net.minecraft.server.Tag also existed as a class, this broke.
 */
public class SamePackageRemappedClassTest {
    static {
        Resolver.registerClassResolver(new ClassPathResolver() {
            @Override
            public String resolveClassPath(String className) {
                if (className.equals("com.bergerkiller.mountiplex.types.otherpackage.RemappedType")) {
                    return "com.bergerkiller.mountiplex.types.samepackage.SamePackageType";
                } else {
                    return className;
                }
            }
        });
    }

    @Test
    public void testSamePackageRemappedClassAtPackage() {
        // Compile generated code that references RemappedType
        // It should turn it into SomePackageType, and not use the existing RemappedType class.
        // This is verified by trying to call a constructor with arg, which fails for RemappedType
        // because it lacks that one.
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(DeclaredPackageType.class);
        resolver.setPackage("com.bergerkiller.mountiplex.types.otherpackage");

        MethodDeclaration mdec = new MethodDeclaration(resolver, "" +
                "public static RemappedType createWithArg(String arg) {\n" +
                "    return new RemappedType(arg);\n" +
                "}");
        FastMethod<SamePackageType> method = new FastMethod<>(mdec);
        method.forceInitialization();

        assertEquals("test", method.invoke(null, "test").arg);
    }

    @Test
    public void testSamePackageRemappedClassAtImport() {
        // Register the class remapping
        Resolver.registerClassResolver(new ClassPathResolver() {
            @Override
            public String resolveClassPath(String className) {
                if (className.equals("com.bergerkiller.mountiplex.types.otherpackage.RemappedType")) {
                    return "com.bergerkiller.mountiplex.types.samepackage.SamePackageType";
                } else {
                    return className;
                }
            }
        });

        // Now try to compile generated code that references RemappedType
        // It should turn it into SomePackageType, and not use the existing RemappedType class.
        // This is verified by trying to call a constructor with arg, which fails for RemappedType
        // because it lacks that one.
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(DeclaredPackageType.class);
        resolver.addImport("com.bergerkiller.mountiplex.types.otherpackage.RemappedType");

        MethodDeclaration mdec = new MethodDeclaration(resolver, "" +
                "public static RemappedType createWithArg(String arg) {\n" +
                "    return new RemappedType(arg);\n" +
                "}");
        FastMethod<SamePackageType> method = new FastMethod<>(mdec);
        method.forceInitialization();

        assertEquals("test", method.invoke(null, "test").arg);
    }
}
