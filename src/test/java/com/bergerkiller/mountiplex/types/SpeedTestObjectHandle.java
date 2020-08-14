package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

@Template.InstanceType("com.bergerkiller.mountiplex.types.SpeedTestObject")
public abstract class SpeedTestObjectHandle extends Template.Handle {
    public static final SpeedTestObjectClass T = Template.Class.create(SpeedTestObjectClass.class, com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE);

    public static SpeedTestObjectHandle createHandle(Object handleInstance) {
        return T.createHandle(handleInstance);
    }

    public abstract int getI();

    public abstract void setI(int value);

    public abstract double getD();

    public abstract void setD(double value);

    public abstract String getS();

    public abstract void setS(String value);

    public abstract int getIMethod();

    public abstract void setIMethod(int value);

    public abstract String getSMethod();

    public abstract void setSMethod(String value);
    
    public abstract void setLocation(double x, double y, double z, float yaw, float pitch);

    public abstract int publicLotsOfArgs(int a, int b, int c, int d, int e, int f, int g);
    public abstract int privateLotsOfArgs(int a, int b, int c, int d, int e, int f, int g);

    public static class SpeedTestObjectClass extends Template.Class<SpeedTestObjectHandle> {
        public final Template.Field.Integer i = new Template.Field.Integer();
        public final Template.Field.Double d = new Template.Field.Double();
        public final Template.Field<String> s = new Template.Field<String>();
        public final Template.Method<String> getSMethod = new Template.Method<String>();
        public final Template.Method<Void> setSMethod = new Template.Method<Void>();
        public final Template.Method<Integer> getIMethod = new Template.Method<Integer>();
        public final Template.Method<Void> setIMethod = new Template.Method<Void>();
        public final Template.Method<Void> setLocation = new Template.Method<Void>();
        public final Template.Method<Integer> publicLotsOfArgs = new Template.Method<Integer>();
        public final Template.Method<Integer> privateLotsOfArgs = new Template.Method<Integer>();
    }
}
