package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.TestObjectHandle;

// tests the correct working of Template elements
public class TemplateTest {

    static {
        Resolver.registerClassDeclarationResolver(new ClassDeclarationResolver() {
            @Override
            public ClassDeclaration resolveClassDeclaration(Class<?> classType) {
                if (classType.equals(TestObject.class)) {
                    String template = "package com.bergerkiller.mountiplex.types;\n" +
                                      "\n" +
                                      "public class TestObject {\n" +
                                      "    private static String staticField:a;\n" +
                                      "    private static final String staticFinalField:a_f;\n" +
                                      "    private String localField:b;\n" +
                                      "    private final String localFinalField:b_f;\n" +
                                      "    private (String) int intConvField:c;\n" +
                                      "    public final (List<String>) List<Integer> testRawField;\n" +
                                      "    \n" +
                                      "    private int testFunc:d(int k, int l);\n" +
                                      "    private (String) int testConvFunc1:e(int k, int l);\n" +
                                      "    private int testConvFunc2:f((String) int k, (String) int l);\n" +
                                      "    private static (long) int testing2:g(int a, (String) int b);\n" +
                                      "}\n";

                    return SourceDeclaration.parse(template).classes[0];
                }
                return null;
            }
        });
    }

    @Test
    public void testTemplate() {
        TestObject object = new TestObject();
        assertEquals("static_test", TestObjectHandle.CONSTANT);
        assertEquals("static_test", TestObjectHandle.T.staticField.get());
        assertEquals("static_final_test", TestObjectHandle.T.staticFinalField.get());
        assertEquals("local_test", TestObjectHandle.T.localField.get(object));
        assertEquals("local_final_test", TestObjectHandle.T.localFinalField.get(object));
        TestObjectHandle.T.staticField.set("static_changed");
        TestObjectHandle.T.staticFinalField.set("static_final_changed");
        TestObjectHandle.T.localField.set(object, "local_changed");
        TestObjectHandle.T.localFinalField.set(object, "local_final_changed");
        assertEquals("static_changed", TestObjectHandle.T.staticField.get());
        assertEquals("static_final_changed", TestObjectHandle.T.staticFinalField.get());
        assertEquals("local_changed", TestObjectHandle.T.localField.get(object));
        assertEquals("local_final_changed", TestObjectHandle.T.localFinalField.get(object));
        assertEquals("12", TestObjectHandle.T.intConvField.get(object));
        assertEquals(57, TestObjectHandle.T.testFunc.invokeVA(object, 12, 45).intValue());
        assertEquals("77", TestObjectHandle.T.testConvFunc1.invokeVA(object, 43, 33));
        assertEquals(68, TestObjectHandle.T.testConvFunc2.invokeVA(object, "22", "44").intValue());
        assertEquals(Long.valueOf(288), TestObjectHandle.T.testing2.invokeVA(12, "24"));
        
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRawList() {
        // Verify that it stores Integer values for String values put in
        TestObject obj = new TestObject();
        List<String> list = TestObjectHandle.T.testRawField.get(obj);
        list.add("12");
        list.add("-55");
        list.add("0");
        List<Integer> rawList = (List<Integer>) TestObjectHandle.T.testRawField.raw.get(obj);
        assertEquals(Integer.valueOf(12), rawList.get(0));
        assertEquals(Integer.valueOf(-55), rawList.get(1));
        assertEquals(Integer.valueOf(0), rawList.get(2));
    }

    @Test
    public void testNestedClasses() {
        SourceDeclaration source = SourceDeclaration.parse(
                "package com.bergerkiller.mountiplex.types;\n" +
                "\n" +
                "public class MainClass1 {\n" +
                "    private String mainField1;\n"+
                "\n" +
                "    class MainClass1.NestedClass1 {\n" +
                "        public String nestedField1;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "public class MainClass2 {\n" +
                "    private String mainField2;\n"+
                "\n" +
                "    class MainClass2.NestedClass2 {\n" +
                "        public String nestedField2;\n" +
                "    }\n" +
                "}\n"
                );

        //System.out.println(source.toString());

        assertEquals(2, source.classes.length);
        assertEquals(1, source.classes[0].subclasses.length);
        assertEquals(1, source.classes[1].subclasses.length);
        assertEquals("MainClass1", source.classes[0].type.typeName);
        assertEquals("MainClass2", source.classes[1].type.typeName);
        assertEquals("MainClass1.NestedClass1", source.classes[0].subclasses[0].type.typeName);
        assertEquals("MainClass2.NestedClass2", source.classes[1].subclasses[0].type.typeName);
    }

    @Test
    public void testVariables() {
        ClassResolver resolver = new ClassResolver();
        resolver.setVariable("version", "1.12-pre5");
        assertTrue(resolver.evaluateExpression("version == 1.12-pre5"));
        assertTrue(resolver.evaluateExpression("version >= 1.12-pre5"));
        assertTrue(resolver.evaluateExpression("version <= 1.12-pre5"));
        assertFalse(resolver.evaluateExpression("version != 1.12-pre5"));
        assertFalse(resolver.evaluateExpression("version < 1.12-pre5"));
        assertFalse(resolver.evaluateExpression("version > 1.12-pre5"));
        assertTrue(resolver.evaluateExpression("version >= 1.11-pre5"));
        assertTrue(resolver.evaluateExpression("version > 1.11-pre5"));
        assertTrue(resolver.evaluateExpression("version <= 1.13-pre5"));
        assertTrue(resolver.evaluateExpression("version < 1.13-pre5"));
        assertTrue(resolver.evaluateExpression("version >= 1.12"));
        assertFalse(resolver.evaluateExpression("version >= 1.12-pre6"));
    }
}
