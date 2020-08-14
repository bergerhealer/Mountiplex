package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.ClassTemplate;
import com.bergerkiller.mountiplex.reflection.FieldAccessor;
import com.bergerkiller.mountiplex.reflection.MethodAccessor;
import com.bergerkiller.mountiplex.reflection.SafeField;
import com.bergerkiller.mountiplex.reflection.SafeMethod;
import com.bergerkiller.mountiplex.types.RenameTestObject;
import com.bergerkiller.mountiplex.types.RenameTestObjectHandle;

/**
 * Tests the correct functioning of field and method name renaming
 */
public class RenameTest {
    static {
        com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.bootstrap();
    }

    /**
     * Tests accessing local fields and methods directly. The name of the field and methods
     * should be automatically renamed by the TestClassDeclarationResolver to be matching.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleLocalDeclarations() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.someTestPublicField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestPrivateField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestFinalField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestPublicMethod.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestPrivateMethod.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        RenameTestObjectHandle.T.someTestPublicField.setInteger(object, 20);
        assertEquals(20, RenameTestObjectHandle.T.someTestPublicField.getInteger(object));

        RenameTestObjectHandle.T.someTestPrivateField.setInteger(object, 21);
        assertEquals(21, RenameTestObjectHandle.T.someTestPrivateField.getInteger(object));

        RenameTestObjectHandle.T.someTestFinalField.setInteger(object, 22);
        assertEquals(22, RenameTestObjectHandle.T.someTestFinalField.getInteger(object));

        assertEquals(222, RenameTestObjectHandle.T.someTestPublicMethod.invoke(object).intValue());
        assertEquals(333, RenameTestObjectHandle.T.someTestPrivateMethod.invoke(object).intValue());

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);

        handle.setSomeTestPublicField(40);
        assertEquals(40, handle.getSomeTestPublicField());

        handle.setSomeTestPrivateField(41);
        assertEquals(41, handle.getSomeTestPrivateField());

        handle.setSomeTestFinalField(42);
        assertEquals(42, handle.getSomeTestFinalField());

        assertEquals(222, handle.someTestPublicMethod());
        assertEquals(333, handle.someTestPrivateMethod());
    }

    /**
     * Tests accessing static fields and methods directly. The name of the field and methods
     * should be automatically renamed by the TestClassDeclarationResolver to be matching.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleStaticDeclarations() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.someTestStaticPublicField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestStaticPrivateField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestStaticFinalField.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestStaticPublicMethod.isAvailable());
        assertTrue(RenameTestObjectHandle.T.someTestStaticPrivateMethod.isAvailable());

        // Reset
        RenameTestObjectHandle.T.someTestStaticPublicField.setInteger(0);
        RenameTestObjectHandle.T.someTestStaticPrivateField.setInteger(0);
        RenameTestObjectHandle.T.someTestStaticFinalField.setInteger(0);

        // Try using the T. Template objects
        RenameTestObjectHandle.T.someTestStaticPublicField.setInteger(20);
        assertEquals(20, RenameTestObjectHandle.T.someTestStaticPublicField.getInteger());

        RenameTestObjectHandle.T.someTestStaticPrivateField.setInteger(21);
        assertEquals(21, RenameTestObjectHandle.T.someTestStaticPrivateField.getInteger());

        RenameTestObjectHandle.T.someTestStaticFinalField.setInteger(22);
        assertEquals(22, RenameTestObjectHandle.T.someTestStaticFinalField.getInteger());

        assertEquals(444, RenameTestObjectHandle.T.someTestStaticPublicMethod.invoke().intValue());
        assertEquals(555, RenameTestObjectHandle.T.someTestStaticPrivateMethod.invoke().intValue());
    }

    /**
     * Tests the generated methods (methods with body) that uses a #require to require
     * a local field in the class. The name of the field should be renamed
     * by the TestClassDeclarationResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithLocalFieldRequirements() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedGetPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedGetPrivateFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedGetFinalFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetPrivateFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetFinalFieldUsingRequirements.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        RenameTestObjectHandle.T.generatedSetPublicFieldUsingRequirements.invoke(object, 50);
        RenameTestObjectHandle.T.generatedSetPrivateFieldUsingRequirements.invoke(object, 51);
        RenameTestObjectHandle.T.generatedSetFinalFieldUsingRequirements.invoke(object, 52);
        assertEquals(50, RenameTestObjectHandle.T.generatedGetPublicFieldUsingRequirements.invoke(object).intValue());
        assertEquals(50, object.testPublicField);
        assertEquals(51, RenameTestObjectHandle.T.generatedGetPrivateFieldUsingRequirements.invoke(object).intValue());
        assertEquals(52, RenameTestObjectHandle.T.generatedGetFinalFieldUsingRequirements.invoke(object).intValue());
        assertEquals(52, object.testFinalField);

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);
        handle.generatedSetPublicFieldUsingRequirements(60);
        handle.generatedSetPrivateFieldUsingRequirements(61);
        handle.generatedSetFinalFieldUsingRequirements(62);
        assertEquals(60, handle.generatedGetPublicFieldUsingRequirements());
        assertEquals(60, object.testPublicField);
        assertEquals(61, handle.generatedGetPrivateFieldUsingRequirements());
        assertEquals(62, handle.generatedGetFinalFieldUsingRequirements());
        assertEquals(62, object.testFinalField);
    }

    /**
     * Tests the generated methods (methods with body) that uses a #require to require
     * a static field in the class. The name of the field should be renamed
     * by the TestClassDeclarationResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithStaticFieldRequirements() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedGetStaticPrivateFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedGetStaticFinalFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetStaticPrivateFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetStaticFinalFieldUsingRequirements.isAvailable());

        // Reset
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingRequirements.invoke(0);
        RenameTestObjectHandle.T.generatedSetStaticPrivateFieldUsingRequirements.invoke(0);
        RenameTestObjectHandle.T.generatedSetStaticFinalFieldUsingRequirements.invoke(0);

        // Try using the T. Template objects
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingRequirements.invoke(70);
        RenameTestObjectHandle.T.generatedSetStaticPrivateFieldUsingRequirements.invoke(71);
        RenameTestObjectHandle.T.generatedSetStaticFinalFieldUsingRequirements.invoke(72);
        assertEquals(70, RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingRequirements.invoke().intValue());
        assertEquals(71, RenameTestObjectHandle.T.generatedGetStaticPrivateFieldUsingRequirements.invoke().intValue());
        assertEquals(72, RenameTestObjectHandle.T.generatedGetStaticFinalFieldUsingRequirements.invoke().intValue());
    }

    /**
     * Tests the generated methods (methods with body) that uses a #require to require
     * a local method in the class. The name of the method should be renamed
     * by the TestClassDeclarationResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithLocalMethodRequirements() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedCallMethodUsingRequirements.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        assertEquals(333, RenameTestObjectHandle.T.generatedCallMethodUsingRequirements.invoke(object).intValue());

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);
        assertEquals(333, handle.generatedCallMethodUsingRequirements());
    }

    /**
     * Tests the generated methods (methods with body) that uses a #require to require
     * a static method in the class. The name of the method should be renamed
     * by the TestClassDeclarationResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithStaticMethodRequirements() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedCallStaticMethodUsingRequirements.isAvailable());

        // Try using the T. Template objects
        assertEquals(555, RenameTestObjectHandle.T.generatedCallStaticMethodUsingRequirements.invoke().intValue());
    }

    /**
     * Tests the generated methods (methods with body) that use a local field directly in the code.
     * The name of the field should be renamed
     * by the Javassist MemberResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithLocalFieldMemberResolver() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedGetPublicFieldUsingMemberResolver.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetPublicFieldUsingMemberResolver.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        RenameTestObjectHandle.T.generatedSetPublicFieldUsingMemberResolver.invoke(object, 50);
        assertEquals(50, RenameTestObjectHandle.T.generatedGetPublicFieldUsingMemberResolver.invoke(object).intValue());

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);
        handle.generatedSetPublicFieldUsingMemberResolver(60);
        assertEquals(60, handle.generatedGetPublicFieldUsingMemberResolver());
    }

    /**
     * Tests the generated methods (methods with body) that use a static field directly in the code.
     * The name of the field should be renamed
     * by the Javassist MemberResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithStaticFieldMemberResolver() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingMemberResolver.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.isAvailable());

        // Reset
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.invoke(0);

        // Try using the T. Template objects
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.invoke(80);
        assertEquals(80, RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingMemberResolver.invoke().intValue());
    }

    /**
     * Tests the generated methods (methods with body) that use a local method directly in the code.
     * The name of the method should be renamed
     * by the Javassist MemberResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithLocalMethodMemberResolver() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedCallMethodUsingMemberResolver.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        assertEquals(222, RenameTestObjectHandle.T.generatedCallMethodUsingMemberResolver.invoke(object).intValue());

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);
        assertEquals(222, handle.generatedCallMethodUsingMemberResolver());
    }

    /**
     * Tests the generated methods (methods with body) that use a static method directly in the code.
     * The name of the method should be renamed
     * by the Javassist MemberResolver appropriately.<br>
     * <br>
     * Tests the Template objects in the 'T' static variable, and the generated code
     * of the Handle class itself.
     */
    @Test
    public void testHandleGeneratedWithStaticMethodMemberResolver() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingMemberResolver.isAvailable());
        assertTrue(RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.isAvailable());

        // Reset
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.invoke(0);

        // Try using the T. Template objects
        RenameTestObjectHandle.T.generatedSetStaticPublicFieldUsingMemberResolver.invoke(80);
        assertEquals(80, RenameTestObjectHandle.T.generatedGetStaticPublicFieldUsingMemberResolver.invoke().intValue());
    }
    
    /**
     * There is a reverse renaming operation that renames a field to a name that
     * also has a rename operation back. If anywhere in the code two renames are
     * performed on the code, then this operation will fail.
     */
    @Test
    public void testHandleGeneratedWithOverrides() {
        // Verify resolved
        assertTrue(RenameTestObjectHandle.T.overrideGetPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.overrideSetPublicFieldUsingRequirements.isAvailable());
        assertTrue(RenameTestObjectHandle.T.overrideCallPublicMethodUsingRequirements.isAvailable());

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Try using the T. Template objects
        RenameTestObjectHandle.T.overrideSetPublicFieldUsingRequirements.invoke(object, 50);
        assertEquals(50, RenameTestObjectHandle.T.overrideGetPublicFieldUsingRequirements.invoke(object).intValue());
        assertEquals(666, RenameTestObjectHandle.T.overrideCallPublicMethodUsingRequirements.invoke(object).intValue());

        // Try using the Handle class
        RenameTestObjectHandle handle = RenameTestObjectHandle.createHandle(object);
        handle.overrideSetPublicFieldUsingRequirements(60);
        assertEquals(60, handle.overrideGetPublicFieldUsingRequirements());
        assertNotEquals(60, object.testPublicField);
        assertEquals(60, object.originalTestPublicField);
        assertEquals(666, handle.overrideCallPublicMethodUsingRequirements());
    }

    /**
     * Tests proper functioning when initializing a SafeField with a field name that has been renamed
     */
    @Test
    public void testSafeField() {
        // Test static contains
        assertTrue(SafeField.contains(RenameTestObject.class, "originalTestPublicField", int.class));
        assertTrue(SafeField.contains(RenameTestObject.class, "originalTestPrivateField", int.class));
        assertTrue(SafeField.contains(RenameTestObject.class, "originalTestFinalField", int.class));

        // We operate on this object
        RenameTestObject object = new RenameTestObject();

        // Test static get/set
        SafeField.set(object, "originalTestPublicField", 50);
        assertEquals(50, SafeField.get(object, "originalTestPublicField", int.class).intValue());
        assertEquals(50, object.testPublicField);
        SafeField.set(object, "originalTestPrivateField", 51);
        assertEquals(51, SafeField.get(object, "originalTestPrivateField", int.class).intValue());
        SafeField.set(object, "originalTestFinalField", 52);
        assertEquals(52, object.testFinalField);
        assertEquals(52, SafeField.get(object, "originalTestFinalField", int.class).intValue());

        // Test instance get/set
        testField(SafeField.create(RenameTestObject.class, "originalTestPublicField", int.class));
        testField(SafeField.create(RenameTestObject.class, "originalTestPrivateField", int.class));
        testField(SafeField.create(RenameTestObject.class, "originalTestFinalField", int.class));
    }

    /**
     * Tests proper functioning when initializing a SafeMethod with a method name that has been renamed
     */
    @Test
    public void testSafeMethod() {
        assertTrue(SafeMethod.contains(RenameTestObject.class, "originalTestPublicMethod"));
        assertTrue(SafeMethod.contains(RenameTestObject.class, "originalTestPrivateMethod"));

        // Test instance invoke
        testMethod(new SafeMethod<Integer>(RenameTestObject.class, "originalTestPublicMethod"), 222);
        testMethod(new SafeMethod<Integer>(RenameTestObject.class, "originalTestPrivateMethod"), 333);
    }

    /**
     * Tests that the various methods that produce fields in the ClassTemplate
     * work properly with fields that have been renamed.
     */
    @Test
    public void testClassTemplateGetField() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testField(cDec.getField("originalTestPublicField"));
        testField(cDec.getField("originalTestPrivateField"));
        testField(cDec.getField("originalTestFinalField"));

        testField(cDec.getField("originalTestPublicField", int.class));
        testField(cDec.getField("originalTestPrivateField", int.class));
        testField(cDec.getField("originalTestFinalField", int.class));
    }

    /**
     * Tests ClassTemplate selectField() logic, which matches the field exactly,
     * but ignores order.
     */
    @Test
    public void testClassTemplateSelectField() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testField(cDec.selectField("public int originalTestPublicField"));
        testField(cDec.selectField("private int originalTestPrivateField"));
        testField(cDec.selectField("public final int originalTestFinalField"));
    }

    /**
     * Tests ClassTemplate nextField() logic, which matches the field exactly
     */
    @Test
    public void testClassTemplateNextField() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testField(cDec.nextField("public int originalTestPublicField"));
        testField(cDec.nextField("private int originalTestPrivateField"));
        testField(cDec.nextField("public final int originalTestFinalField"));
    }

    /**
     * Tests ClassTemplate nextFieldSignature() logic, which matches the field by type/modifiers.
     * Please check console log that no warnings are printed about a changed name.
     */
    @Test
    public void testClassTemplateNextFieldSignature() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testField(cDec.nextFieldSignature("public int originalTestPublicField"));
        testField(cDec.nextFieldSignature("private int originalTestPrivateField"));
        testField(cDec.nextFieldSignature("public final int originalTestFinalField"));
    }

    /**
     * Tests ClassTemplate getMethod(name) logic
     */
    @Test
    public void testClassTemplateGetMethod() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testMethod(cDec.getMethod("originalTestPublicMethod"), 222);
        testMethod(cDec.getMethod("originalTestPrivateMethod"), 333);
    }

    /**
     * Tests ClassTemplate selectMethod(signature) logic
     */
    @Test
    public void testClassTemplateSelectMethod() {
        ClassTemplate<RenameTestObject> cDec = ClassTemplate.create(RenameTestObject.class);

        testMethod(cDec.selectMethod("public int originalTestPublicMethod()"), 222);
        testMethod(cDec.selectMethod("private int originalTestPrivateMethod()"), 333);
    }

    private void testField(FieldAccessor<Integer> field) {
        assertNotNull(field);
        assertTrue(field.isValid());

        RenameTestObject object = new RenameTestObject();
        assertTrue(field.set(object, 70));
        assertEquals(70, field.get(object).intValue());
    }

    private void testMethod(MethodAccessor<Integer> method, int expectedValue) {
        assertNotNull(method);
        assertTrue(method.isValid());

        RenameTestObject object = new RenameTestObject();
        assertEquals(expectedValue, method.invoke(object).intValue());
    }
}
