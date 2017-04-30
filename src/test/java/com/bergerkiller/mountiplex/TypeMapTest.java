package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;

public class TypeMapTest {

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

    private static void testMap(InputTypeMap<Integer> map, TypeDeclaration type, Object value) {
        assertEquals((value != null), map.containsKey(type));
        assertEquals(value, map.get(type));
    }
}
