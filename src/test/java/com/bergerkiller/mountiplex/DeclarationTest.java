package com.bergerkiller.mountiplex;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;

public class DeclarationTest {

    @Test
    public void testArrayField() {
        FieldDeclaration dec = new FieldDeclaration(ClassResolver.DEFAULT, "private double[] move_SomeArray:aI");
        System.out.println(dec.type.isResolved());
    }
}
