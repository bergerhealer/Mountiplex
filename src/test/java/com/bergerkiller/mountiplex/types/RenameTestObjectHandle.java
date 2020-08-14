package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

@Template.InstanceType("com.bergerkiller.mountiplex.types.RenameTestObject")
public abstract class RenameTestObjectHandle extends Template.Handle {
    public static final RenameTestObjectClass T = Template.Class.create(RenameTestObjectClass.class, com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE);

    public static RenameTestObjectHandle createHandle(Object handleInstance) {
        return T.createHandle(handleInstance);
    }

    public abstract int getSomeTestPublicField();
    public abstract void setSomeTestPublicField(int value);

    public abstract int getSomeTestPrivateField();
    public abstract void setSomeTestPrivateField(int value);

    public abstract int getSomeTestFinalField();
    public abstract void setSomeTestFinalField(int value);

    public abstract int someTestPublicMethod();
    public abstract int someTestPrivateMethod();

    public abstract int generatedGetPublicFieldUsingRequirements();
    public abstract int generatedGetPublicFieldUsingMemberResolver();
    public abstract int generatedGetPrivateFieldUsingRequirements();
    public abstract int generatedGetFinalFieldUsingRequirements();

    public abstract void generatedSetPublicFieldUsingRequirements(int value);
    public abstract void generatedSetPublicFieldUsingMemberResolver(int value);
    public abstract void generatedSetPrivateFieldUsingRequirements(int value);
    public abstract void generatedSetFinalFieldUsingRequirements(int value);

    public abstract int generatedCallMethodUsingRequirements();
    public abstract int generatedCallMethodUsingMemberResolver();

    public abstract int overrideGetPublicFieldUsingRequirements();
    public abstract void overrideSetPublicFieldUsingRequirements(int value);
    public abstract int overrideCallPublicMethodUsingRequirements();

    public static class RenameTestObjectClass extends Template.Class<RenameTestObjectHandle> {
        public final Template.Field.Integer someTestPublicField = new Template.Field.Integer();
        public final Template.Field.Integer someTestPrivateField = new Template.Field.Integer();
        public final Template.Field.Integer someTestFinalField = new Template.Field.Integer();

        public final Template.StaticField.Integer someTestStaticPublicField = new Template.StaticField.Integer();
        public final Template.StaticField.Integer someTestStaticPrivateField = new Template.StaticField.Integer();
        public final Template.StaticField.Integer someTestStaticFinalField = new Template.StaticField.Integer();

        public final Template.Method<Integer> someTestPublicMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> someTestPrivateMethod = new Template.Method<Integer>();

        public final Template.StaticMethod<Integer> someTestStaticPublicMethod = new Template.StaticMethod<Integer>();
        public final Template.StaticMethod<Integer> someTestStaticPrivateMethod = new Template.StaticMethod<Integer>();

        public final Template.Method<Integer> generatedGetPublicFieldUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedGetPublicFieldUsingMemberResolver = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedGetPrivateFieldUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedGetFinalFieldUsingRequirements = new Template.Method<Integer>();

        public final Template.Method<Void> generatedSetPublicFieldUsingRequirements = new Template.Method<Void>();
        public final Template.Method<Void> generatedSetPublicFieldUsingMemberResolver = new Template.Method<Void>();
        public final Template.Method<Void> generatedSetPrivateFieldUsingRequirements = new Template.Method<Void>();
        public final Template.Method<Void> generatedSetFinalFieldUsingRequirements = new Template.Method<Void>();

        public final Template.StaticMethod<Integer> generatedGetStaticPublicFieldUsingRequirements = new Template.StaticMethod<Integer>();
        public final Template.StaticMethod<Integer> generatedGetStaticPublicFieldUsingMemberResolver = new Template.StaticMethod<Integer>();
        public final Template.StaticMethod<Integer> generatedGetStaticPrivateFieldUsingRequirements = new Template.StaticMethod<Integer>();
        public final Template.StaticMethod<Integer> generatedGetStaticFinalFieldUsingRequirements = new Template.StaticMethod<Integer>();

        public final Template.StaticMethod<Void> generatedSetStaticPublicFieldUsingRequirements = new Template.StaticMethod<Void>();
        public final Template.StaticMethod<Void> generatedSetStaticPublicFieldUsingMemberResolver = new Template.StaticMethod<Void>();
        public final Template.StaticMethod<Void> generatedSetStaticPrivateFieldUsingRequirements = new Template.StaticMethod<Void>();
        public final Template.StaticMethod<Void> generatedSetStaticFinalFieldUsingRequirements = new Template.StaticMethod<Void>();

        public final Template.Method<Integer> generatedCallMethodUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedCallMethodUsingMemberResolver = new Template.Method<Integer>();
        public final Template.StaticMethod<Integer> generatedCallStaticMethodUsingRequirements = new Template.StaticMethod<Integer>();

        public final Template.Method<Integer> overrideGetPublicFieldUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Void> overrideSetPublicFieldUsingRequirements = new Template.Method<Void>();
        public final Template.Method<Integer> overrideCallPublicMethodUsingRequirements = new Template.Method<Integer>();
    }
}
