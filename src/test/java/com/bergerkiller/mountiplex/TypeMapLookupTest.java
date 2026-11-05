package com.bergerkiller.mountiplex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;
import com.bergerkiller.mountiplex.reflection.util.InputTypeSet;
import com.bergerkiller.mountiplex.reflection.util.OutputTypeMap;

/**
 * Tests the sub-class aware lookups of the type maps, as documented in docs/type-maps.md
 */
public class TypeMapLookupTest {

    @Test
    public void testInputTypeMapIncludesSuperTypes() {
        InputTypeMap<String> map = new InputTypeMap<String>();
        map.add(Number.class, "number");
        map.add(Object.class, "object");
        map.add(Long.class, "long");
        map.add(Collection.class, "coll");

        assertEquals(Arrays.asList("long", "number", "object"), new ArrayList<String>(map.getAll(Long.class)));
        assertEquals(Arrays.asList("number", "object"), new ArrayList<String>(map.getAll(Integer.class)));
        assertEquals(Arrays.asList("object"), new ArrayList<String>(map.getAll(String.class)));
        assertEquals(Arrays.asList("coll", "object"), new ArrayList<String>(map.getAll(ArrayList.class)));
        assertEquals(Arrays.asList("coll", "object"),
                new ArrayList<String>(map.getAll(TypeDeclaration.parse("java.util.List<String>"))));
        assertEquals("long", map.get(Long.class));
    }

    @Test
    public void testInputTypeMapModification() {
        InputTypeMap<String> map = new InputTypeMap<String>();
        map.add(Number.class, "number");
        map.add(Long.class, "long");

        map.put(Number.class, "n2");
        assertEquals(Arrays.asList("long", "n2"), new ArrayList<String>(map.getAll(Long.class)));
        map.remove(Number.class, "n2");
        assertEquals(Arrays.asList("long"), new ArrayList<String>(map.getAll(Long.class)));

        assertFalse(map.amend(TypeDeclaration.fromClass(Long.class), "x"));
        assertTrue(map.amend(TypeDeclaration.fromClass(Short.class), "short"));
        assertEquals(Arrays.asList("short"), new ArrayList<String>(map.getAll(Short.class)));
        assertNull(map.get(String.class));
        assertFalse(map.containsKey(TypeDeclaration.fromClass(String.class)));
    }

    @Test
    public void testOutputTypeMapIncludesSubTypes() {
        OutputTypeMap<String> map = new OutputTypeMap<String>();
        map.add(Long.class, "long");
        map.add(Float.class, "float");
        map.add(String.class, "str");

        assertEquals(Arrays.asList("long", "float"), new ArrayList<String>(map.getAll(Number.class)));
        assertEquals(Arrays.asList("long"), new ArrayList<String>(map.getAll(Long.class)));
        assertEquals(3, map.getAll(Object.class).size());
    }

    @Test
    public void testInputTypeSetContainsSubTypesOfAddedTypes() {
        InputTypeSet set = new InputTypeSet();
        set.add(TypeDeclaration.fromClass(ArrayList.class));
        assertTrue(set.contains(TypeDeclaration.fromClass(ArrayList.class)));
        assertFalse(set.contains(TypeDeclaration.fromClass(LinkedList.class)));
        assertFalse(set.contains(TypeDeclaration.fromClass(List.class)));

        set.add(TypeDeclaration.fromClass(Collection.class));
        assertTrue(set.contains(TypeDeclaration.fromClass(LinkedList.class)));
        assertTrue(set.contains(TypeDeclaration.fromClass(List.class)));
    }
}
