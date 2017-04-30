package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;
import com.bergerkiller.mountiplex.types.IntegerMapOfString;

public class TypeMapTest {

    @Test
    public void testTypeDeclaration() {
        assertEquals("List", TypeDeclaration.fromClass(java.util.List.class).toString());
        assertEquals("java.text.NumberFormat", TypeDeclaration.fromClass(java.text.NumberFormat.class).toString());
        assertEquals("Map.Entry", TypeDeclaration.fromClass(java.util.Map.Entry.class).toString());
        assertEquals("java.util.Map.Entry", TypeDeclaration.fromClass(java.util.Map.Entry.class).toString(true));
        testTypeParsing("List<Integer>");
        testTypeParsing("Map<String, Integer>");
        testTypeParsing("Map<List<String>, Map<Integer, Short>>");
        testTypeParsing("Map.Entry<String, Integer>");

        assertTypesEqual("HashMap<String, Integer>", "HashMap<String, Integer>",
                "AbstractMap<String, Integer>",
                "Object",
                "Map<String, Integer>",
                "Cloneable",
                "java.io.Serializable");

        assertTypesEqual("ArrayList<String>", "ArrayList<String>",
                "AbstractList<String>",
                "AbstractCollection<String>",
                "Object",
                "Collection<String>",
                "List<String>",
                "RandomAccess",
                "Cloneable",
                "java.io.Serializable");

        assertTypesEqual(IntegerMapOfString.class, "com.bergerkiller.mountiplex.types.IntegerMapOfString",
                "com.bergerkiller.mountiplex.types.IntegerMapOfE<String>",
                "HashMap<Integer, String>",
                "AbstractMap<Integer, String>",
                "Object",
                "Map<Integer, String>",
                "Cloneable",
                "java.io.Serializable");
    }

    @Test
    public void testInputTypeMap() {
        TypeDeclaration tObject = TypeDeclaration.fromClass(Object.class);
        TypeDeclaration tNumber = TypeDeclaration.fromClass(Number.class);
        TypeDeclaration tInt = TypeDeclaration.fromClass(int.class);
        TypeDeclaration tInteger = TypeDeclaration.fromClass(Integer.class);
        TypeDeclaration tDouble = TypeDeclaration.fromClass(Double.class);

        InputTypeMap<Integer> map = new InputTypeMap<Integer>();
        testMap(map, tObject, null);
        testMap(map, tNumber, null);
        testMap(map, tInt, null);
        testMap(map, tInteger, null);
        testMap(map, tDouble, null);

        map.put(tNumber, 12);
        testMap(map, tObject, null);
        testMap(map, tNumber, 12);
        testMap(map, tInt, null);
        testMap(map, tInteger, 12);
        testMap(map, tDouble, 12);

        map.put(tObject, 15);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, null);
        testMap(map, tInteger, 12);
        testMap(map, tDouble, 12);

        map.put(tInt, 64);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, 64);
        testMap(map, tInteger, 12);
        testMap(map, tDouble, 12);

        map.put(tInteger, 32);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, 64);
        testMap(map, tInteger, 32);
        testMap(map, tDouble, 12);
    }

    private static void assertTypesEqual(Class<?> typeClass, String selfName, String... superTypeNames) {
        TypeDeclaration type = TypeDeclaration.fromClass(typeClass);
        assertTrue(type.isValid());
        assertTrue(type.isResolved());
        assertTypesEqual(type, selfName, superTypeNames);
    }

    private static void assertTypesEqual(String declaration, String selfName, String... superTypeNames) {
        TypeDeclaration type = new TypeDeclaration(ClassResolver.DEFAULT, declaration);
        assertTrue(type.isValid());
        assertTrue(type.isResolved());
        assertTypesEqual(type, selfName, superTypeNames);
    }

    private static void assertTypesEqual(TypeDeclaration type, String selfName, String... superTypeNames) {
        assertEquals(selfName, type.toString());
        int i = 0;
        for (TypeDeclaration superType : type.getSuperTypes()) {
            if (i >= superTypeNames.length) {
                logSuperTypes(type);
                fail("More super types than expected! Extra type = " + superType.toString());
            }
            if (!superTypeNames[i].equals(superType.toString())) {
                logSuperTypes(type);
                assertEquals(superTypeNames[i], superType.toString());
            }
            i++;
        }
        if (i != superTypeNames.length) {
            logSuperTypes(type);
            fail("Less super types than expected! Missing type = " + superTypeNames[i]);
        }
    }

    private static void logSuperTypes(TypeDeclaration type) {
        System.out.println("Super types of " + type.toString());
        for (TypeDeclaration superType : type.getSuperTypes()) {
            System.out.println("  - " + superType.toString());
        }
    }

    private static void testTypeParsing(String declaration) {
        TypeDeclaration type = new TypeDeclaration(ClassResolver.DEFAULT, declaration);
        assertTrue(type.isValid());
        assertTrue(type.isResolved());
        assertEquals(declaration, type.toString());
    }

    private static void testMap(InputTypeMap<Integer> map, TypeDeclaration type, Object value) {
        assertEquals((value != null), map.containsKey(type));
        assertEquals(value, map.get(type));
    }

}
