package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.NameDeclaration;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameDeclarationTest {

    @Test
    public void testSetValue() {
        assertEquals(name("first:second"),
                name("first") .setValue(name("second")));

        assertEquals(name("first:second"),
                name("first:second") .setValue(name("second")));

        assertEquals(name("first:second"),
                name("first") .setValue(name("first:second")));

        assertEquals(name("a:b:c:d"),
                name("a:b") .setValue(name("b:c:d")));
    }

    @Test
    public void testSetValueOptimizedCaseOne() {
        // Check that the same instance is returned when setValue does not change it
        NameDeclaration a = name("a");
        NameDeclaration ab = name("a:b");
        assertSame("setValue should optimize when identical", ab, a.setValue(ab));
    }

    @Test
    public void testSetValueOptimizedCaseTwo() {
        // Check that the same instance is returned when setValue does not change it
        NameDeclaration ab = name("a:b");
        NameDeclaration b = name("b");
        assertSame("setValue should optimize when identical", ab, ab.setValue(b));
    }

    private static NameDeclaration name(String str) {
        StringBuffer sb = new StringBuffer(str);
        return new NameDeclaration(ClassResolver.DEFAULT, sb);
    }
}
