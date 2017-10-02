package com.bergerkiller.mountiplex.types;

/**
 * Shows the implementation of the SpeedTestObjectHandle as is generated at runtime
 */
public class SpeedTestObjectHandleImpl extends SpeedTestObjectHandle {
    private final SpeedTestObject instance;
    
    public SpeedTestObjectHandleImpl(SpeedTestObject instance) {
        this.instance = instance;
    }

    @Override
    public Object getRaw() {
        return this.instance;
    }

    public double getD() {
        return instance.d;
        //return T.d.getDouble(instance);
    }

    public void setD(double value) {
        instance.d = value;
        //T.d.setDouble(instance, value);
    }

    public int getI() {
        return instance.i;
        //return T.i.getInteger(instance);
    }

    public void setI(int value) {
        instance.i = value;
        //T.i.setInteger(instance, value);
    }
    
    public String getS() {
        return instance.s;
        //return T.s.get(instance);
    }

    public void setS(String value) {
        instance.s = value;
        //T.s.set(instance, value);
    }

    public int getIMethod() {
        return T.getIMethod.invoke(instance);
    }

    public void setIMethod(int value) {
        T.setIMethod.invoke(instance, value);
    }

    public final String getSMethod() {
        return T.getSMethod.invoke(instance);
    }

    public final void setSMethod(String value) {
        T.setSMethod.invoke(instance, value);
    }

    public final void setLocation(double x, double y, double z, float yaw, float pitch) {
        T.setLocation.invoke(instance, x, y, z, yaw, pitch);
    }

    public int lotsOfArgs(int a, int b, int c, int d, int e, int f, int g) {
        return T.lotsOfArgs.invokeVA(instance, a, b, c, d, e, f, g);
    }
}
