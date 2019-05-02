package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.types.SpeedTestObject;
import com.bergerkiller.mountiplex.types.TestObject;

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

        assertEquals(dec.getPostfix().toString(), "\nThis should not be omitted");
    }

    @Test
    public void testMethodBodyExecution() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(SpeedTestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public int getCounter(int ad) { return instance.i + ad; }");

        FastMethod<Integer> method = new FastMethod<Integer>();
        method.init(dec);

        SpeedTestObject obj = new SpeedTestObject();
        obj.i = 5;
        assertEquals(Integer.valueOf(27), method.invoke(obj, 22));
    }

    @Test
    public void testMethodBodyExecutionManyArgs() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(SpeedTestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public static String complex(String a, String b, String c, int d, int e, int f) {\n" +
                "  StringBuilder builder = new StringBuilder();\n" +
                "  builder.append(a).append(b).append(c).append(d+e+f);\n" +
                "  return builder.toString();\n" +
                "}");

        FastMethod<String> method = new FastMethod<String>();
        method.init(dec);

        assertEquals("aaabbbccc15", method.invokeVA(null, "aaa", "bbb", "ccc", 3, 5, 7));
    }

    @Test
    public void testMethodWithFieldRequirements() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public int add(int n) {\n" +
                "  #require com.bergerkiller.mountiplex.types.TestObject private int special:c;\n" +
                "  instance#special = instance#special + n;\n" +
                "  return instance#special;\n" +
                "}");

        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());
        assertEquals(
                "{\n" +
                "  this.special.setInteger(instance, this.special.getInteger(instance) + n);\n" +
                "  return this.special.getInteger(instance);\n" +
                "}\n",
                dec.body);
        assertEquals(1, dec.bodyRequirements.length);
        assertEquals("private int special:c;", dec.bodyRequirements[0].toString());

        // Method declaration is OK from this point. Try to invoke it.
        FastMethod<Integer> method = new FastMethod<Integer>();
        method.init(dec);
        TestObject testObject = new TestObject();
        assertEquals(12, method.invoke(testObject, 0).intValue());
        assertEquals(13, method.invoke(testObject, 1).intValue());
        assertEquals(18, method.invoke(testObject, 5).intValue());
    }

    @Test
    public void testMethodWithConvertedFieldRequirements() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public String getNumber(String value) {\n" +
                "  #require com.bergerkiller.mountiplex.types.TestObject private (String) int special:c;\n" +
                "  #require com.bergerkiller.mountiplex.types.TestObject private (int) String something:b;\n" +
                "  instance#special = value;\n" +
                "  instance#something = 57;\n" +
                "  StringBuilder builder = new StringBuilder();\n" +
                "  builder.append(instance#special);\n" +
                "  builder.append('_');\n" +
                "  builder.append(instance#something);\n" +
                "  return builder.toString();\n" +
                "}");

        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());

        assertEquals(
                "{\n" +
                "  this.special.set(instance, value);\n" +
                "  this.something.setInteger(instance, 57);\n" +
                "  StringBuilder builder = new StringBuilder();\n" +
                "  builder.append((java.lang.String)this.special.get(instance));\n" +
                "  builder.append('_');\n" +
                "  builder.append(this.something.getInteger(instance));\n" +
                "  return builder.toString();\n" +
                "}\n",
                dec.body);

        assertEquals(2, dec.bodyRequirements.length);
        assertEquals("private (String) int special:c;", dec.bodyRequirements[0].toString());
        assertEquals("private (int) String something:b;", dec.bodyRequirements[1].toString());

        // Method declaration is OK from this point. Try to invoke it.
        FastMethod<String> method = new FastMethod<String>();
        method.init(dec);
        TestObject testObject = new TestObject();
        assertEquals("unused", testObject.unusedField);
        assertEquals("12_57", method.invoke(testObject, "12"));
        assertEquals("59_57", method.invoke(testObject, "59"));
    }

    @Test
    public void testMethodWithMethodRequirements() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public int add(int n) {\n" +
                "  #require com.bergerkiller.mountiplex.types.TestObject private int addToSpecial:h(int n);\n" +
                "  return instance#addToSpecial(n) + 5;\n" +
                "}");

        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());

        assertEquals(
                "{\n" +
                "  return this.addToSpecial(instance, n) + 5;\n" +
                "}\n",
                dec.body);
        assertEquals(1, dec.bodyRequirements.length);
        assertEquals("private int addToSpecial:h(int n);", dec.bodyRequirements[0].toString());

        // Method declaration is OK from this point. Try to invoke it.
        FastMethod<Integer> method = new FastMethod<Integer>();
        method.init(dec);
        TestObject testObject = new TestObject();
        assertEquals(17, method.invoke(testObject, 0).intValue());
        assertEquals(19, method.invoke(testObject, 2).intValue());
    }

    @Test
    public void testMethodWithLotsaArgsMethodRequirements() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(SpeedTestObject.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, 
                "public int add(int n) {\n" +
                "  #require com.bergerkiller.mountiplex.types.SpeedTestObject public int test:lotsOfArgs(int a, int b, int c, int d, int e, int f, int g);\n" +
                "  return instance#test(1, 2, 3, 4, 5, 6, n);\n" +
                "}");

        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());

        assertEquals(
                "{\n" +
                "  return this.test(instance, 1, 2, 3, 4, 5, 6, n);\n" +
                "}\n",
                dec.body);
        assertEquals(1, dec.bodyRequirements.length);
        assertEquals("public int test:lotsOfArgs(int a, int b, int c, int d, int e, int f, int g);", dec.bodyRequirements[0].toString());

        // Method declaration is OK from this point. Try to invoke it.
        FastMethod<Integer> method = new FastMethod<Integer>();
        method.init(dec);
        SpeedTestObject testObject = new SpeedTestObject();
        assertEquals(21, method.invoke(testObject, 0).intValue());
        assertEquals(23, method.invoke(testObject, 2).intValue());
    }
}
