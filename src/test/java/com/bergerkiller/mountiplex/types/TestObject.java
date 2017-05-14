package com.bergerkiller.mountiplex.types;

@SuppressWarnings("unused")
public class TestObject {
    private static String a = "static_test";
    private String b = "local_test";
    private int c = 12;

    private int d(int k, int l) {
        return k + l;
    }

    private int e(int k, int l) {
        return k + l + 1;
    }

    private int f(int k, int l) {
        return k + l + 2;
    }

    private static int g(int a, int b) {
        return (a * b);
    }
}
