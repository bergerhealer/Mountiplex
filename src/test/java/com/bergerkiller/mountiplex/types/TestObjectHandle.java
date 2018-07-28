package com.bergerkiller.mountiplex.types;

import java.util.List;

import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

public class TestObjectHandle extends Template.Handle {
    public static final TestObjectClass T = new TestObjectClass();
    protected static final StaticInitHelper _init_helper = new StaticInitHelper(TestObjectHandle.class, "com.bergerkiller.mountiplex.types.TestObject");
    public static final String CONSTANT = T.staticField.getSafe();

    public static class TestObjectClass extends Template.Class<TestObjectHandle> {
        public final Template.Field.Converted<List<String>> testRawField = new Template.Field.Converted<List<String>>();
        public final Template.StaticField<String> staticField = new Template.StaticField<String>();
        public final Template.StaticField<String> staticFinalField = new Template.StaticField<String>();
        public final Template.Field<String> localField = new Template.Field<String>();
        public final Template.Field<String> localFinalField = new Template.Field<String>();
        public final Template.Field.Converted<String> intConvField = new Template.Field.Converted<String>();
        public final Template.Method<Integer> testFunc = new Template.Method<Integer>();
        public final Template.Method.Converted<String> testConvFunc1 = new Template.Method.Converted<String>();
        public final Template.Method.Converted<Integer> testConvFunc2 = new Template.Method.Converted<Integer>();
        public final Template.Method<Integer> defaultInterfaceMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> inheritedClassMethod = new Template.Method<Integer>();
        public final Template.StaticMethod.Converted<Long> testing2 = new Template.StaticMethod.Converted<Long>();
        @Template.Optional
        public final Template.Field<String> unusedField = new Template.Field<String>();
    }
}
