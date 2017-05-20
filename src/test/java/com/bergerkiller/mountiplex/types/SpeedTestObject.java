package com.bergerkiller.mountiplex.types;

public class SpeedTestObject {
    public int i;
    public double d;
    public String s;

    public final void setI(int value) {
        i = value;
    }

    public final int getI() {
        return i;
    }

    public final void setS(String value) {
        s = value;
    }

    public final String getS() {
        return s;
    }
    
    public final String test(String arg0, Integer arg1) {
        return "test";
    }
}
