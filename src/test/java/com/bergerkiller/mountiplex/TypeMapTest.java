package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;
import com.bergerkiller.mountiplex.reflection.util.OutputTypeMap;
import com.bergerkiller.mountiplex.reflection.util.TypeMap;
import com.bergerkiller.mountiplex.types.CustomSetType;
import com.bergerkiller.mountiplex.types.IntegerMapOfString;
import com.bergerkiller.mountiplex.types.TestInterface;
import com.bergerkiller.mountiplex.types.TestInterfaceExt;
import com.bergerkiller.mountiplex.types.TestInterfaceImpl;

public class TypeMapTest {

    @Test
    public void testTypeDeclaration() {
        assertEquals("List", TypeDeclaration.fromClass(java.util.List.class).toString());
        assertEquals("java.text.NumberFormat", TypeDeclaration.fromClass(java.text.NumberFormat.class).toString());
        assertEquals("Map.Entry", TypeDeclaration.fromClass(java.util.Map.Entry.class).toString());
        assertEquals("java.util.Map$Entry", TypeDeclaration.fromClass(java.util.Map.Entry.class).toString(true));
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
                "Iterable<String>",
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
    public void testArrayTypes() {
        TypeDeclaration tInt = TypeDeclaration.fromClass(int.class);
        TypeDeclaration tIntArr = TypeDeclaration.fromClass(int[].class);
        assertFalse(tInt.equals(tIntArr));
    }

    @Test
    public void testGenericType() {
        TypeDeclaration testa = TypeDeclaration.parse("List<?>");
        TypeDeclaration testb = TypeDeclaration.parse("List<T>");
        TypeDeclaration testc = TypeDeclaration.parse("List<Integer>");
        assertTrue(testb.isInstanceOf(testa));
        assertTrue(testc.isInstanceOf(testa));
        assertTrue(testc.isInstanceOf(testb));
        assertFalse(testb.isInstanceOf(testc));
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

    @Test
    public void testOutputTypeMap() {
        TypeDeclaration tObject = TypeDeclaration.fromClass(Object.class);
        TypeDeclaration tNumber = TypeDeclaration.fromClass(Number.class);
        TypeDeclaration tInt = TypeDeclaration.fromClass(int.class);
        TypeDeclaration tInteger = TypeDeclaration.fromClass(Integer.class);
        TypeDeclaration tDouble = TypeDeclaration.fromClass(Double.class);

        OutputTypeMap<Integer> map = new OutputTypeMap<Integer>();
        testMap(map, tObject, null);
        testMap(map, tNumber, null);
        testMap(map, tInt, null);
        testMap(map, tInteger, null);
        testMap(map, tDouble, null);

        map.put(tNumber, 12);
        testMap(map, tObject, 12);
        testMap(map, tNumber, 12);
        testMap(map, tInt, null);
        testMap(map, tInteger, null);
        testMap(map, tDouble, null);

        map.put(tObject, 15);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, null);
        testMap(map, tInteger, null);
        testMap(map, tDouble, null);

        map.put(tInt, 64);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, 64);
        testMap(map, tInteger, null);
        testMap(map, tDouble, null);

        map.put(tInteger, 32);
        testMap(map, tObject, 15);
        testMap(map, tNumber, 12);
        testMap(map, tInt, 64);
        testMap(map, tInteger, 32);
        testMap(map, tDouble, null);

        map.clear();
        map.put(tInteger, 64);
        testMap(map, tObject, 64);
        testMap(map, tNumber, 64);
        testMap(map, tInt, null);
        testMap(map, tInteger, 64);
        testMap(map, tDouble, null);

        map.put(tDouble, 70);
        testMap(map, tObject, 70);
        testMap(map, tNumber, 70);
        testMap(map, tInt, null);
        testMap(map, tInteger, 64);
        testMap(map, tDouble, 70);

        System.out.println(map.getAll(tObject));
    }
    
    @Test
    public void castToInterfaceTest() {
        TypeDeclaration t1 = TypeDeclaration.fromClass(TestInterfaceImpl.class);
        TypeDeclaration t2 = TypeDeclaration.fromClass(TestInterface.class);
        assertTrue(t1.isInstanceOf(t2));
        assertTrue(t2.isAssignableFrom(t1));
        TypeDeclaration t3 = t1.castAsType(TestInterface.class);
        assertNotNull(t3);
        assertEquals(t2, t3);

        TypeDeclaration t4 = TypeDeclaration.fromClass(TestInterfaceExt.class);
        assertTrue(t4.isInstanceOf(t2));
        assertTrue(t2.isAssignableFrom(t4));
        TypeDeclaration t5 = t4.castAsType(TestInterface.class);
        assertNotNull(t5);
        assertEquals(t5, t2);
    }

    @Test
    public void baseTypeTest() {
        ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, "class Test extends ArrayList<String> {}");
        assertEquals(cDec.type.typePath, "Test");
        assertEquals(cDec.base.toString(), "ArrayList<String>");
    }

    @Test
    public void testAmend() {
        InputTypeMap<String> map = new InputTypeMap<String>();
        assertAmend(map, TypeDeclaration.createGeneric(Set.class, String.class), "hello");
        assertAmend(map, TypeDeclaration.createGeneric(CustomSetType.class, String.class), "world");
    }

    private static <T> void assertAmend(InputTypeMap<T> map, TypeDeclaration type, T value) {
        assertTrue(map.amend(type, value));
        assertEquals(value, map.get(type));
    }

    private static void assertTypesEqual(Class<?> typeClass, String selfName, String... superTypeNames) {
        TypeDeclaration type = TypeDeclaration.fromClass(typeClass);
        assertTrue(type.isValid());
        assertTrue(type.isResolved());
        assertTypesEqual(type, selfName, superTypeNames);
    }

    private static void assertTypesEqual(String declaration, String selfName, String... superTypeNames) {
        TypeDeclaration type = TypeDeclaration.parse(declaration);
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
        TypeDeclaration type = TypeDeclaration.parse(declaration);
        assertTrue(type.isValid());
        assertTrue(type.isResolved());
        assertEquals(declaration, type.toString());
    }

    private static void testMap(TypeMap<Integer> map, TypeDeclaration type, Object value) {
        assertEquals((value != null), map.containsKey(type));
        assertEquals(value, map.get(type));
    }

}
