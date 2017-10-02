package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

public class SpeedTestObjectHandle extends Template.Handle {
    public static final SpeedTestObjectClass T = new SpeedTestObjectClass();
    protected static final StaticInitHelper _init_helper = new StaticInitHelper(SpeedTestObjectHandle.class, "com.bergerkiller.mountiplex.types.SpeedTestObject");

    public static SpeedTestObjectHandle createHandle(Object handleInstance) {
        return T.createHandle(handleInstance);
    }

    public int getI() {
        return T.i.getInteger(getRaw());
    }

    public void setI(int value) {
        T.i.setInteger(getRaw(), value);
    }

    public double getD() {
        return T.d.getDouble(getRaw());
    }

    public void setD(int value) {
        T.d.setDouble(getRaw(), value);
    }

    public String getS() {
        return T.s.get(getRaw());
    }

    public void setS(String value) {
        T.s.set(getRaw(), value);
    }

    public static class SpeedTestObjectClass extends Template.Class<SpeedTestObjectHandle> {
        public final Template.Field.Integer i = new Template.Field.Integer();
        public final Template.Field.Double d = new Template.Field.Double();
        public final Template.Field<String> s = new Template.Field<String>();
        public final Template.Method<String> getS = new Template.Method<String>();
        public final Template.Method<Void> setS = new Template.Method<Void>();
    }
}
