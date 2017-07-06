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

    @Test
    public void testMethodBody() {
        MethodDeclaration dec = new MethodDeclaration(ClassResolver.DEFAULT, 
                "    public int doStuff(int x, int y, int z) {\n" +
                "        int a = x + y + z;\n" +
                "        int b = 0;\n" +
                "        for (int i = 0; i < a; i++) {\n" +
                "            if (i > 5) {\n" +
                "                b += 5;\n" +
                "            }\n" +
                "            b += 6;\n" +
                "        }\n" +
                "        if (b > 5) { b *= 6; }\n" +
                "        return b;\n" +
                "    }\n" +
                "This should not be omitted");

        assertEquals(
                "{\n" +
                "    int a = x + y + z;\n" +
                "    int b = 0;\n" +
                "    for (int i = 0; i < a; i++) {\n" +
                "        if (i > 5) {\n" +
                "            b += 5;\n" +
                "        }\n" +
                "        b += 6;\n" +
                "    }\n" +
                "    if (b > 5) { b *= 6; }\n" +
                "    return b;\n" +
                "}\n",
                dec.body);

        assertEquals(dec.getPostfix(), "\nThis should not be omitted");
    }
}
