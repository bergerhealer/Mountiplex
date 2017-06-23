package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;

public class FieldDeclarationTest {

    @Test
    public void testArrayField() {
        FieldDeclaration dec = new FieldDeclaration(ClassResolver.DEFAULT, "private double[] move_SomeArray:aI");
        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());
        assertEquals("double[]", dec.type.toString(true));
        assertEquals("double[]", dec.type.toString());
        assertEquals(double[].class, dec.type.type);
    }

    @Test
    public void testTranslatedField() {
        FieldDeclaration dec = new FieldDeclaration(ClassResolver.DEFAULT, "private (Set<String>) List<String> field");
        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());
        assertEquals(dec.type.type, List.class);
        assertNotNull(dec.type.cast);
        assertEquals(dec.type.cast.type, Set.class);
        assertEquals("private (Set<String>) List<String> field;", dec.toString());
        assertEquals("Set<String>", dec.type.cast.toString());
        assertEquals("private java.util.List<java.lang.String> field;", dec.toString(true));
        assertEquals("java.util.Set<java.lang.String>", dec.type.cast.toString(true));
    }

    @Test
    public void testEnumField() {
        FieldDeclaration dec = new FieldDeclaration(ClassResolver.DEFAULT, "enum optional String TEST");
        assertTrue(dec.isEnum);
        assertTrue(dec.modifiers.isOptional());
        assertTrue(dec.modifiers.isFinal());
        assertEquals("enum java.lang.String TEST", dec.toString(true));
    }

}
