package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;

public class TypeMapTest {

    @Test
    public void testInputTypeMap() {
        TypeDeclaration tInteger = TypeDeclaration.fromClass(Integer.class);
        TypeDeclaration tNumber = TypeDeclaration.fromClass(Number.class);
        TypeDeclaration tObject = TypeDeclaration.fromClass(Object.class);

        InputTypeMap<Integer> map = new InputTypeMap<Integer>();
        assertFalse(map.containsKey(tInteger));

        /*
        map.put(tObject, 12);
        assertEquals(12, (int) map.get(tInteger));
        assertEquals(12, (int) map.get(tNumber));
        assertEquals(12, (int) map.get(tObject));

        map.put(tInteger, 16);
        assertEquals(16, (int) map.get(tInteger));
        assertEquals(16, (int) map.get(tNumber));
        assertEquals(16, (int) map.get(tObject));

        map.put(tNumber, 15);
        assertEquals(12, (int) map.get(tInteger));
        assertEquals(15, (int) map.get(tNumber));
        assertEquals(12, (int) map.get(tObject));
        */
    }
    
    @Test
    public void test2() {
        TypeDeclaration tInt = TypeDeclaration.fromClass(int.class);
        TypeDeclaration tInteger = TypeDeclaration.fromClass(Integer.class);
        TypeDeclaration tNumber = TypeDeclaration.fromClass(Number.class);
        TypeDeclaration tObject = TypeDeclaration.fromClass(Object.class);
        TypeDeclaration tDouble = TypeDeclaration.fromClass(Double.class);
        
        InputTypeMap<Integer> map = new InputTypeMap<Integer>();
        map.put(tInt, 10);
        map.put(tInteger, 12);
        map.put(tNumber, 15);
        System.out.println(map.get(tDouble));
        
        ///map.put(tDouble, 33);
        //System.out.println(map.get(tDouble));
    }
}
