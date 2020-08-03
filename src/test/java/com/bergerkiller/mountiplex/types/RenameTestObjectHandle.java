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
    public abstract int generatedGetPrivateFieldUsingRequirements();
    public abstract int generatedGetFinalFieldUsingRequirements();

    public abstract void generatedSetPublicFieldUsingRequirements(int value);
    public abstract void generatedSetPrivateFieldUsingRequirements(int value);
    public abstract void generatedSetFinalFieldUsingRequirements(int value);

    public abstract int generatedCallMethodUsingRequirements();

    public static class RenameTestObjectClass extends Template.Class<RenameTestObjectHandle> {
        public final Template.Field.Integer someTestPublicField = new Template.Field.Integer();
        public final Template.Field.Integer someTestPrivateField = new Template.Field.Integer();
        public final Template.Field.Integer someTestFinalField = new Template.Field.Integer();
        public final Template.Method<Integer> someTestPublicMethod = new Template.Method<Integer>();
        public final Template.Method<Integer> someTestPrivateMethod = new Template.Method<Integer>();

        public final Template.Method<Integer> generatedGetPublicFieldUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedGetPrivateFieldUsingRequirements = new Template.Method<Integer>();
        public final Template.Method<Integer> generatedGetFinalFieldUsingRequirements = new Template.Method<Integer>();

        public final Template.Method<Void> generatedSetPublicFieldUsingRequirements = new Template.Method<Void>();
        public final Template.Method<Void> generatedSetPrivateFieldUsingRequirements = new Template.Method<Void>();
        public final Template.Method<Void> generatedSetFinalFieldUsingRequirements = new Template.Method<Void>();

        public final Template.Method<Integer> generatedCallMethodUsingRequirements = new Template.Method<Integer>();
    }
}
