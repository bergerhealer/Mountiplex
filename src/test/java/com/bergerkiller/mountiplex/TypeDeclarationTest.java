package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

public class TypeDeclarationTest {

    @Test
    public void testComponentType() {
        TypeDeclaration arrType = TypeDeclaration.parse("List<String>[]");
        assertTrue(arrType.isArray());
        TypeDeclaration compType = arrType.getComponentType();
        assertEquals("List<String>", compType.toString());
    }

    @Test
    public void testGenericInstanceOf() {
        TypeDeclaration tObject = TypeDeclaration.fromClass(Object.class);
        TypeDeclaration tList = TypeDeclaration.parse("List");
        TypeDeclaration tListAny = TypeDeclaration.parse("List<?>");
        TypeDeclaration tListInteger = TypeDeclaration.parse("List<Integer>");
        TypeDeclaration tListString = TypeDeclaration.parse("List<String>");
        TypeDeclaration tCollection = TypeDeclaration.parse("Collection");
        TypeDeclaration tCollectionAny = TypeDeclaration.parse("Collection<?>");
        TypeDeclaration tCollectionInteger = TypeDeclaration.parse("Collection<Integer>");
        TypeDeclaration tCollectionString = TypeDeclaration.parse("Collection<String>");

        // All types should be an Object
        assertTrue(tList.isInstanceOf(tObject));
        assertTrue(tListAny.isInstanceOf(tObject));
        assertTrue(tListInteger.isInstanceOf(tObject));
        assertTrue(tListString.isInstanceOf(tObject));
        assertTrue(tCollection.isInstanceOf(tObject));
        assertTrue(tCollectionAny.isInstanceOf(tObject));
        assertTrue(tCollectionInteger.isInstanceOf(tObject));
        assertTrue(tCollectionString.isInstanceOf(tObject));

        // All list types should be a List. Collections are not Lists.
        assertTrue(tListAny.isInstanceOf(tList));
        assertTrue(tListInteger.isInstanceOf(tList));
        assertTrue(tListString.isInstanceOf(tList));
        assertFalse(tCollection.isInstanceOf(tList));
        assertFalse(tCollectionAny.isInstanceOf(tList));
        assertFalse(tCollectionInteger.isInstanceOf(tList));
        assertFalse(tCollectionString.isInstanceOf(tList));

        // All non-raw List types should be List<?>. Collections are not Lists.
        assertFalse(tList.isInstanceOf(tListAny));
        assertTrue(tListInteger.isInstanceOf(tListAny));
        assertTrue(tListString.isInstanceOf(tListAny));
        assertFalse(tCollection.isInstanceOf(tListAny));
        assertFalse(tCollectionAny.isInstanceOf(tListAny));
        assertFalse(tCollectionInteger.isInstanceOf(tListAny));
        assertFalse(tCollectionString.isInstanceOf(tListAny));

        // List<?> should support Collection<?>
        assertTrue(tListAny.isInstanceOf(tCollectionAny));
        assertTrue(tListInteger.isInstanceOf(tCollectionAny));
        assertTrue(tListString.isInstanceOf(tCollectionAny));

        // Generic type checking
        assertTrue(tListInteger.isInstanceOf(tCollectionInteger));
        assertTrue(tListString.isInstanceOf(tCollectionString));
        assertFalse(tListInteger.isInstanceOf(tCollectionString));
        assertFalse(tListString.isInstanceOf(tCollectionInteger));
    }

    @Test
    public void testAnyGenericInstanceOf() {
        TypeDeclaration a, b;

        // Check that List<String> instanceof List == true
        a = TypeDeclaration.parse("java.util.List<String>");
        b = TypeDeclaration.parse("java.util.List");
        assertTrue(a.isInstanceOf(b));

        // Check that List instanceof List<String> == false
        a = TypeDeclaration.parse("java.util.List");
        b = TypeDeclaration.parse("java.util.List<String>");
        assertFalse(a.isInstanceOf(b));

        // Check that List<?> instanceof List == true
        a = TypeDeclaration.parse("java.util.List<?>");
        b = TypeDeclaration.parse("java.util.List");
        assertTrue(a.isInstanceOf(b));

        // Check that List instanceof List<?> == true
        a = TypeDeclaration.parse("java.util.List");
        b = TypeDeclaration.parse("java.util.List<?>");
        assertFalse(a.isInstanceOf(b));
    }
}
