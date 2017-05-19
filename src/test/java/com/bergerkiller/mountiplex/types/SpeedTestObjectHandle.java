package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

public class SpeedTestObjectHandle extends Template.Handle {
    public static final SpeedTestObjectClass T = new SpeedTestObjectClass();
    protected static final StaticInitHelper _init_helper = new StaticInitHelper(SpeedTestObjectHandle.class, "com.bergerkiller.mountiplex.types.SpeedTestObject");

    public static SpeedTestObjectHandle createHandle(Object handleInstance) {
        if (handleInstance == null) return null;
        SpeedTestObjectHandle handle = new SpeedTestObjectHandle();
        handle.instance = handleInstance;
        return handle;
    }

    public int getI() {
        return T.i.getInteger(instance);
    }

    public void setI(int value) {
        T.i.setInteger(instance, value);
    }

    public double getD() {
        return T.d.getDouble(instance);
    }

    public void setD(int value) {
        T.d.setDouble(instance, value);
    }

    public String getS() {
        return T.s.get(instance);
    }

    public void setS(String value) {
        T.s.set(instance, value);
    }

    public static class SpeedTestObjectClass extends Template.Class<SpeedTestObjectHandle> {
        public final Template.Field.Integer i = new Template.Field.Integer();
        public final Template.Field.Double d = new Template.Field.Double();
        public final Template.Field<String> s = new Template.Field<String>();
    }
}
