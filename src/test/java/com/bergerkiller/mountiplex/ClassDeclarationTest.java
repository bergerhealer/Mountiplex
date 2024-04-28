package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.types.TestObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the correct functioning of Class Declarations, in particular, the remapping
 * functionality and how it affects requirements, method/field declarations and
 * parsed method bodies.
 */
public class ClassDeclarationTest {

    @Test
    public void testRemappedMethodDeclaration() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject private int addInt:h(int n);\n" +
                "    private int addInt(int n);\n" +
                "}");

        MethodDeclaration method = dec.methods[0].discover();
        assertNotNull(method);
        assertNotNull(method.method);
        assertEquals("addInt:h", method.name.toString());
    }

    @Test
    public void testRemappedMethodRequirement() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject private int addInt:h(int n);\n" +
                "    #require TestObject private int addInt(int n);\n" +
                "    public int callAddInt(int n) {\n" +
                "        return instance#addInt(n);\n" +
                "    }\n" +
                "}");

        MethodDeclaration method = dec.methods[0].discover();
        assertNotNull(method);
        assertEquals("callAddInt", method.name.toString());

        // Call the method, through the requirement, to verify the requirement remapping is working properly
        // c = 12
        // addInt(int n) -> c += n
        TestObject testObject = new TestObject(); // c = 12
        FastMethod<Integer> callSetInt = new FastMethod<>(method);
        assertEquals(20, (int) callSetInt.invoke(testObject, 8));
        assertEquals(25, (int) callSetInt.invoke(testObject, 5));
    }

    @Test
    public void testRemappedMethodInMethodBody() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject public int addInt:k(int n);\n" +
                "    public int callAddInt(int n) {\n" +
                "        return instance.addInt(n);\n" +
                "    }\n" +
                "}");

        MethodDeclaration method = dec.methods[0].discover();
        assertNotNull(method);
        assertEquals("callAddInt", method.name.toString());

        // Call the method, through the requirement, to verify the requirement remapping is working properly
        // c = 12
        // addInt(int n) -> c += n
        TestObject testObject = new TestObject(); // c = 12
        FastMethod<Integer> callSetInt = new FastMethod<>(method);
        assertEquals(20, (int) callSetInt.invoke(testObject, 8));
        assertEquals(25, (int) callSetInt.invoke(testObject, 5));
    }

    @Test
    public void testRemappedFieldDeclaration() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject private int intField:c;\n" +
                "    private int intField;\n" +
                "}");

        FieldDeclaration field = dec.fields[0].discover();
        assertNotNull(field);
        assertNotNull(field.field);
        assertEquals("intField:c", field.name.toString());
    }

    @Test
    public void testRemappedFieldRequirement() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject private int intField:c;\n" +
                "    #require TestObject private int intField;\n" +
                "    public int callAddInt(int n) {\n" +
                "        int k = instance#intField;\n" +
                "        k += n;\n" +
                "        instance#intField = k;\n" +
                "        return k;\n" +
                "    }\n" +
                "}");

        MethodDeclaration method = dec.methods[0].discover();
        assertNotNull(method);
        assertEquals("callAddInt", method.name.toString());

        // Call the method, through the requirement, to verify the requirement remapping is working properly
        // c = 12
        // addInt(int n) -> c += n
        TestObject testObject = new TestObject(); // c = 12
        FastMethod<Integer> callSetInt = new FastMethod<>(method);
        assertEquals(20, (int) callSetInt.invoke(testObject, 8));
        assertEquals(25, (int) callSetInt.invoke(testObject, 5));
    }

    @Test
    public void testRemappedFieldInMethodBody() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setPackage("com.bergerkiller.mountiplex.types");
        ClassDeclaration dec = new ClassDeclaration(resolver, "" +
                "class TestObject {\n" +
                "    #remap TestObject public int intField:d;\n" +
                "    public int callAddInt(int n) {\n" +
                "        instance.intField += n;\n" +
                "        return instance.intField;\n" +
                "    }\n" +
                "}");

        MethodDeclaration method = dec.methods[0].discover();
        assertNotNull(method);
        assertEquals("callAddInt", method.name.toString());

        // Call the method, through the requirement, to verify the requirement remapping is working properly
        // d = 5
        // addInt(int n) -> d += n
        TestObject testObject = new TestObject(); // d = 5
        FastMethod<Integer> callSetInt = new FastMethod<>(method);
        assertEquals(20, (int) callSetInt.invoke(testObject, 15));
        assertEquals(25, (int) callSetInt.invoke(testObject, 5));
    }
}
