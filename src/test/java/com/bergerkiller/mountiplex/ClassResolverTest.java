package com.bergerkiller.mountiplex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.types.otherpackage.ChildClassImportObject;

public class ClassResolverTest {

    @Test
    public void testImportChildClasses() {
        Resolver.getPackageNameCache().reset();

        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.addImport("com.bergerkiller.mountiplex.types.otherpackage.ChildClassImportObject");

        // These should both succeed
        assertEquals(ChildClassImportObject.OtherClassInChildClass.class, resolver.resolveClass(
                "ChildClassImportObject.OtherClassInChildClass"));
        assertEquals(ChildClassImportObject.OtherClassInChildClass.class, resolver.resolveClass(
                "ChildClassImportObject$OtherClassInChildClass"));

        // These should not, because they do not match the import
        assertNull(resolver.resolveClass("OtherClassInChildClass"));
        assertNull(resolver.resolveClass("OtherClassInOtherPackage"));
    }
}
