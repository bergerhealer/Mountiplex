package com.bergerkiller.mountiplex.types;

import java.util.List;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

@Template.InstanceType("com.bergerkiller.mountiplex.types.TestObject")
public abstract class TestObjectHandle extends Template.Handle {
    public static final TestObjectClass T = Template.Class.create(TestObjectClass.class, com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE);
    public static final String CONSTANT = T.staticField.getSafe();

    public static TestObjectHandle createHandle(Object instance) {
        return T.createHandle(instance);
    }

    public static String getStaticField() {
        return T.staticField.get();
    }

    public static void setStaticField(String value) {
        T.staticField.set(value);
    }

    public static String getStaticFinalField() {
        return T.staticFinalField.get();
    }

    public static void setStaticFinalField(String value) {
        T.staticFinalField.set(value);
    }

    public static long testing2(int a, String b) {
        return T.testing2.invoke(a, b);
    }

    public static int staticGenerated(int parameter) {
        return T.staticGenerated.invoke(Integer.valueOf(parameter)).intValue();
    }

    public abstract String getLocalField();
    public abstract void setLocalField(String value);
    public abstract String getLocalFinalField();
    public abstract void setLocalFinalField(String value);
    public abstract String getIntConvField();
    public abstract void setIntConvField(String value);
    public abstract short getIntToShortConvField();
    public abstract void setIntToShortConvField(short value);
    public abstract List<String> getTestRawField();
    public abstract void setTestRawField(List<String> value);
    public abstract UniqueType getOneWay();
    public abstract int testFunc(int k, int l);
    public abstract String testConvFunc1(int k, int l);
    public abstract int testConvFunc2(String k, String l);
    public abstract int defaultInterfaceMethod();
    public abstract int inheritedClassMethod();
    public abstract int testGeneratedWithArg(int parameter);
    public abstract long[][] getMultiArr();
    public abstract void setMultiArr(long[][] value);

    public static class TestObjectClass extends Template.Class<TestObjectHandle> {
        public final Template.Field.Converted<List<String>> testRawField = new Template.Field.Converted<List<String>>();
        public final Template.StaticField<String> staticField = new Template.StaticField<String>();
        public final Template.StaticField<String> staticFinalField = new Template.StaticField<String>();
        public final Template.Field<String> localField = new Template.Field<String>();
        public final Template.Field<String> localFinalField = new Template.Field<String>();
        public final Template.Field.Converted<String> intConvField = new Template.Field.Converted<String>();
        public final Template.Field.Converted<Short> intToShortConvField = new Template.Field.Converted<Short>();
        public final Template.Method<Integer> testFunc = new Template.Method<Integer>();
        public final Template.Method.Converted<String> testConvFunc1 = new Template.Method.Converted<String>();
        public final Template.Method.Converted<Integer> testConvFunc2 = new Template.Method.Converted<Integer>();
        public final Template.Method<Integer> defaultInterfaceMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> inheritedClassMethod = new Template.Method<Integer>();
        public final Template.StaticMethod.Converted<Long> testing2 = new Template.StaticMethod.Converted<Long>();
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
