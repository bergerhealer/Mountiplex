package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;

public class MethodDeclarationTest {

    @Test
    public void testCastingParams() {
        MethodDeclaration dec = new MethodDeclaration(ClassResolver.DEFAULT, "private int test:a((String) int k, (String) int l);");
        assertEquals(dec.toString(), "private int test:a((String) int k, (String) int l);");
        assertEquals(dec.name.value(), "a");
        assertEquals(dec.name.real(), "test");
        assertEquals(dec.returnType.type, int.class);
        assertEquals(dec.parameters.parameters[0].type.type, int.class);
        assertEquals(dec.parameters.parameters[0].type.cast.type, String.class);
        assertEquals(dec.parameters.parameters[1].type.type, int.class);
        assertEquals(dec.parameters.parameters[1].type.cast.type, String.class);
    }

}
