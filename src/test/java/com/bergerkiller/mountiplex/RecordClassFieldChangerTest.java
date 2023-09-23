package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.reflection.util.fast.RecordClassFieldChanger;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.TestRecordClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the {@link RecordClassFieldChanger} making sure that it can actually create a copy of a record(-like)
 * class with some fields changed out.
 */
public class RecordClassFieldChangerTest {

    @Test
    public void testRequireRecordChanger() {
        ClassResolver resolver = ClassResolver.DEFAULT.clone();
        resolver.setDeclaredClass(TestRecordClass.class);
        MethodDeclaration dec = new MethodDeclaration(resolver, "" +
                        "public TestRecordClass change(int x) {\n" +
                        "    #require TestRecordClass TestRecordClass change_x:<record_changer>(int field2_x);\n" +
                        "    return instance#change_x(x);\n" +
                        "}");

        assertTrue(dec.isValid());
        assertTrue(dec.isResolved());

        // Method declaration is OK from this point. Try to invoke it.
        FastMethod<TestRecordClass> method = new FastMethod<TestRecordClass>();
        method.init(dec);

        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        assertEquals(new TestRecordClass("hello", 1L, 50, 3, 4),
                method.invoke(test, 50));
    }

    @Test
    public void testMultipleMiddleReversed() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field3_y", "field2_x"));

        assertEquals(new TestRecordClass("hello", 1L, 60, 50, 4),
                changer.invoke(test, 50, 60));
        assertEquals(new TestRecordClass("hello", 1L, 60, 50, 4),
                changer.invokeVA(test, 50, 60));
    }

    @Test
    public void testMultipleMiddle() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field2_x", "field3_y"));

        assertEquals(new TestRecordClass("hello", 1L, 50, 60, 4),
                changer.invoke(test, 50, 60));
        assertEquals(new TestRecordClass("hello", 1L, 50, 60, 4),
                changer.invokeVA(test, 50, 60));
    }

    @Test
    public void testRecordField0() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field0_text"));

        assertEquals(new TestRecordClass("changed", 1L, 2, 3, 4),
                changer.invoke(test, "changed"));
        assertEquals(new TestRecordClass("changed", 1L, 2, 3, 4),
                changer.invokeVA(test, "changed"));
    }

    @Test
    public void testRecordField1() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field1_long"));

        assertEquals(new TestRecordClass("hello", 50L, 2, 3, 4),
                changer.invoke(test, 50L));
        assertEquals(new TestRecordClass("hello", 50L, 2, 3, 4),
                changer.invokeVA(test, 50L));
    }

    @Test
    public void testRecordField2() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field2_x"));

        assertEquals(new TestRecordClass("hello", 1L, 50, 3, 4),
                changer.invoke(test, 50));
        assertEquals(new TestRecordClass("hello", 1L, 50, 3, 4),
                changer.invokeVA(test, 50));
    }

    @Test
    public void testRecordField3() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field3_y"));

        assertEquals(new TestRecordClass("hello", 1L, 2, 50, 4),
                changer.invoke(test, 50));
        assertEquals(new TestRecordClass("hello", 1L, 2, 50, 4),
                changer.invokeVA(test, 50));
    }

    @Test
    public void testRecordField4() {
        TestRecordClass test = new TestRecordClass("hello", 1L, 2, 3, 4);

        RecordClassFieldChanger<TestRecordClass> changer = RecordClassFieldChanger.create(TestRecordClass.class, "change",
                Arrays.asList("field4_z"));

        assertEquals(new TestRecordClass("hello", 1L, 2, 3, 50),
                changer.invoke(test, 50));
        assertEquals(new TestRecordClass("hello", 1L, 2, 3, 50),
                changer.invokeVA(test, 50));
    }
}
