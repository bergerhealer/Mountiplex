package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

public class TestObjectHandle extends Template.Handle {
    public static final TestObjectClass T = new TestObjectClass();
    public static final String CONSTANT = T.staticField.getSafe();

    public static class TestObjectClass extends Template.Class {
        public TestObjectClass() {
            this.init(TestObjectClass.class, "com.bergerkiller.mountiplex.types.TestObject");
        }

        public final Template.StaticField<String> staticField = new Template.StaticField<String>();
        public final Template.Field<String> localField = new Template.Field<String>();
        public final Template.Field.Converted<String> intConvField = new Template.Field.Converted<String>();
        public final Template.Method<Integer> testFunc = new Template.Method<Integer>();
        public final Template.Method.Converted<String> testConvFunc1 = new Template.Method.Converted<String>();
        public final Template.Method.Converted<Integer> testConvFunc2 = new Template.Method.Converted<Integer>();
        public final Template.StaticMethod.Converted<Long> testing2 = new Template.StaticMethod.Converted<Long>();
    }
}