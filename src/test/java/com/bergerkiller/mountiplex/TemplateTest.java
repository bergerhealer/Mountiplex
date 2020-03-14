package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.SafeConstructor;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ASMUtil;
import com.bergerkiller.mountiplex.types.BootstrapState;
import com.bergerkiller.mountiplex.types.PrivateTestObjectHandle;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.TestObjectHandle;

// tests the correct working of Template elements
public class TemplateTest {

    private void test_TestObject() {
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
        assertFalse(TestObjectHandle.T.unusedField.isAvailable());
        assertEquals(12, TestObjectHandle.T.defaultInterfaceMethod.invoke(object).intValue());
        assertEquals(13, TestObjectHandle.T.inheritedClassMethod.invoke(object).intValue());
        assertEquals(621, TestObjectHandle.T.testGenerated.invoke(object).intValue());
        assertEquals(244, TestObjectHandle.T.testGeneratedWithArg.invoke(object, 12).intValue());
        assertTrue(TestObjectHandle.T.testGenerated.isAvailable());
        assertTrue(TestObjectHandle.T.oneWay.isReadonly());
        assertEquals("OneWayConvertableType::UniqueType", TestObjectHandle.T.oneWay.get(object).name);
        assertEquals(Integer.valueOf(562), TestObjectHandle.T.staticGenerated.invoke(50));

        try {
            TestObjectHandle.T.oneWay.set(object, null);
            fail("Readonly field was written to");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Field oneWay is readonly", ex.getMessage());
        }
        assertNotNull(TestObjectHandle.T.oneWay.get(object));
    }

    // Same test as above, but uses handle instead
    private void test_TestObjectHandle() {
        // Preset correctly
        TestObjectHandle.setStaticField("static_test");
        TestObjectHandle.setStaticFinalField("static_final_test");

        TestObject object = new TestObject();
        TestObjectHandle handle = TestObjectHandle.createHandle(object);

        assertEquals("static_test", TestObjectHandle.getStaticField());
        assertEquals("static_final_test", TestObjectHandle.getStaticFinalField());
        assertEquals("local_test", handle.getLocalField());
        assertEquals("local_final_test", handle.getLocalFinalField());
        TestObjectHandle.setStaticField("static_changed");
        TestObjectHandle.setStaticFinalField("static_final_changed");
        handle.setLocalField("local_changed");
        handle.setLocalFinalField("local_final_changed");
        assertEquals("static_changed", TestObjectHandle.getStaticField());
        assertEquals("static_final_changed", TestObjectHandle.getStaticFinalField());
        assertEquals("local_changed", handle.getLocalField());
        assertEquals("local_final_changed", handle.getLocalFinalField());
        assertEquals("12", handle.getIntConvField());
        assertEquals(57, handle.testFunc(12, 45));
        assertEquals("77", handle.testConvFunc1(43, 33));
        assertEquals(68, handle.testConvFunc2("22", "44"));
        assertEquals(288L, TestObjectHandle.testing2(12, "24"));
        assertEquals(12, handle.defaultInterfaceMethod());
        assertEquals(13, handle.inheritedClassMethod());
        assertEquals(244, handle.testGeneratedWithArg(12));
        assertEquals("OneWayConvertableType::UniqueType", handle.getOneWay().name);
        assertEquals(562, TestObjectHandle.staticGenerated(50));

        try {
            TestObjectHandle.T.oneWay.set(object, null);
            fail("Readonly field was written to");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Field oneWay is readonly", ex.getMessage());
        }
        assertNotNull(handle.getOneWay());
    }

    private void test_PrivateTestObject() {
        Object privateTestObject = SafeConstructor.create(Resolver.loadClass("com.bergerkiller.mountiplex.types.PrivateTestObject", true)).newInstance();
        PrivateTestObjectHandle handle = PrivateTestObjectHandle.createHandle(privateTestObject);
        handle.setField("test");
        assertEquals("test", handle.getField());
        assertEquals("test", handle.method());
    }

    @Test
    public void testTemplate() {
        // Run tests, verify bootstrap is called once and only once
        test_TestObject();

        assertEquals(1, BootstrapState.CALLED_ROOT);
        assertEquals(1, BootstrapState.CALLED_TESTOBJECT);

        test_PrivateTestObject();

        assertEquals(1, BootstrapState.CALLED_ROOT);
        assertEquals(1, BootstrapState.CALLED_TESTOBJECT);
        assertEquals(1, BootstrapState.CALLED_PRIVATETESTOBJECT);

        test_TestObjectHandle();
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
        resolver.setVariable("cond_a", "12");
        resolver.setVariable("cond_b", "44");
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

        assertTrue(resolver.evaluateExpression("cond_a == 12"));
        assertTrue(resolver.evaluateExpression("cond_b == 44"));
        assertTrue(resolver.evaluateExpression("cond_a == 12 && cond_b == 44"));
        assertFalse(resolver.evaluateExpression("cond_a == 11 && cond_b == 44"));
        assertFalse(resolver.evaluateExpression("cond_a == 12 && cond_b == 43"));
        assertTrue(resolver.evaluateExpression("cond_a == 12 || cond_b == 44"));
        assertFalse(resolver.evaluateExpression("cond_a == 11 || cond_b == 43"));
        assertTrue(resolver.evaluateExpression("cond_a == 11 || cond_b == 44"));
        assertTrue(resolver.evaluateExpression("cond_a == 12 || cond_b == 43"));
    }

    @Test
    public void testUnresolvedField() {
        // Verify that when parsing a template that stores in incorrect type, a proper error is raised
        String template = "package com.bergerkiller.mountiplex.types;\n" +
                "\n" +
                "public class TestObject {\n" +
                "    private static int staticField:a;\n" +
                "}\n";

        ClassResolver resolver = new ClassResolver();
        resolver.setLogErrors(false);
        ClassDeclaration cdec = SourceDeclaration.parse(resolver, template).classes[0];
        assertEquals(1, cdec.fields.length);
        assertNull(cdec.fields[0].field);
    }

    @Test
    public void testFindConstantFromMethod() {
        java.lang.reflect.Method m;
        try {
            m = TestObject.class.getDeclaredMethod("returnsConstant");
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
        assertEquals("SomeConstant", ASMUtil.findStringConstantReturnedByMethod(m));
    }
}
