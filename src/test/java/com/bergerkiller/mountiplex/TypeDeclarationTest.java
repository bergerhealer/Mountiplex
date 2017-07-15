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
}
