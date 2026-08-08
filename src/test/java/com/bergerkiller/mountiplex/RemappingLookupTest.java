package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Remapping;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.TestObjectExtended;
import org.junit.Test;

import static org.junit.Assert.*;

public class RemappingLookupTest {

    /**
     * Performs a remapping that is stored in the super class, but resolves from the extended class (TestObjectExtended to TestObject).
     * This tests the "TypeInputMap" behavior of the lookup store.
     */
    @Test
    public void testLookupMethodSearchInSuperClass() {
        final ClassResolver remapResolver = ClassResolver.DEFAULT.clone();
        remapResolver.setDeclaredClass(TestObject.class);
        MethodDeclaration remapMethod = new MethodDeclaration(remapResolver, "public int addInt:k(int i);").discover();
        assertEquals("addInt:k", remapMethod.name.toString());
        assertNotNull(remapMethod.method);

        Remapping.Lookup lookup = Remapping.createLookup();
        Remapping remapping = new Remapping.MethodRemapping(remapMethod);

        final ClassResolver searchResolver = ClassResolver.DEFAULT.clone();
        searchResolver.setDeclaredClass(TestObjectExtended.class);
        lookup.addRemapping(remapping);
        assertEquals(remapping, lookup.find(new MethodDeclaration(searchResolver, "public int addInt(int i);")));
    }

    /**
     * Performs a remapping, but the method lives in a super-class of the declared class (TestObjectExtended to TestObject)
      */
    @Test
    public void testLookupMethodDiscoverSuperClass() {
        final ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestObjectExtended.class);

        MethodDeclaration remapMethod = new MethodDeclaration(resolver, "public int addInt:k(int i);").discover();
        assertEquals("addInt:k", remapMethod.name.toString());
        assertNotNull(remapMethod.method);

        Remapping.Lookup lookup = Remapping.createLookup();
        Remapping remapping = new Remapping.MethodRemapping(remapMethod);

        lookup.addRemapping(remapping);
        assertEquals(remapping, lookup.find(new MethodDeclaration(resolver, "public int addInt(int i);")));
    }

    @Test
    public void testLookupMethod() {
        final ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestObject.class);

        MethodDeclaration remapMethod =   new MethodDeclaration(resolver, "public int addInt:k(int i);").discover();
        assertEquals("addInt:k", remapMethod.name.toString());
        assertNotNull(remapMethod.method);

        Remapping.Lookup lookup = Remapping.createLookup();
        Remapping remapping = new Remapping.MethodRemapping(remapMethod);

        lookup.addRemapping(remapping);
        assertEquals(remapping, lookup.find(new MethodDeclaration(resolver, "public int addInt(int i);")));
    }
}
