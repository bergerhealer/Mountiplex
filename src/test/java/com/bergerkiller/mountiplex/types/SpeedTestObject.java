package com.bergerkiller.mountiplex.types;

public class SpeedTestObject {
    public int i;
    public double d;
    public String s;

    public SpeedTestObject() {
    }

    public SpeedTestObject(Object arg) {
    }

    public SpeedTestObject(Object arg0, Object arg1) {
    }

    public final void setIMethod(int value) {
        i = value;
    }

    public final int getIMethod() {
        return i;
    }

    public final void setSMethod(String value) {
        s = value;
    }

    public final String getSMethod() {
        return s;
    }
    
    public final String test(String arg0, Integer arg1) {
        return "test";
    }

    public void setLocation(double x, double y, double z, float yaw, float pitch) {
    }

    public int lotsOfArgs(int a, int b, int c, int d, int e, int f, int g) {
        return a + b + c + d + e + f + g;
    }
}
