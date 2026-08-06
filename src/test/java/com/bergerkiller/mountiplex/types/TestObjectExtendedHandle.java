package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

import java.util.List;

@Template.InstanceType("com.bergerkiller.mountiplex.types.TestObjectExtended")
public abstract class TestObjectExtendedHandle extends Template.Handle {
    public static final TestObjectClass T = Template.Class.create(TestObjectClass.class, TestClassDeclarationResolver.INSTANCE);

    public static TestObjectExtendedHandle createHandle(Object instance) {
        return T.createHandle(instance);
    }

    public static int staticGenerated(int parameter) {
        return T.staticGenerated.invoke(Integer.valueOf(parameter)).intValue();
    }

    public abstract List<String> getTestRawField();
    public abstract void setTestRawField(List<String> value);
    public abstract UniqueType getOneWay();
    public abstract int defaultInterfaceMethod();
    public abstract int inheritedClassMethod();
    public abstract int testGeneratedWithArg(int parameter);
    public abstract long[][] getMultiArr();
    public abstract void setMultiArr(long[][] value);

    public static class TestObjectClass extends Template.Class<TestObjectExtendedHandle> {
        public final Template.Field.Converted<List<String>> testRawField = new Template.Field.Converted<List<String>>();
        public final Template.Method<Integer> defaultInterfaceMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> inheritedClassMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> testGeneratedWithArg = new Template.Method<Integer>();
        @Template.Optional
        public final Template.Method<Integer> testGenerated = new Template.Method<Integer>();
        public final Template.StaticMethod<Integer> staticGenerated = new Template.StaticMethod<Integer>();
        @Template.Optional
        public final Template.Field<String> unusedField = new Template.Field<String>();
        @Template.Readonly
        public final Template.Field.Converted<UniqueType> oneWay = new Template.Field.Converted<UniqueType>();
        public final Template.Field<long[][]> multiArr = new Template.Field<long[][]>();
    }
}
