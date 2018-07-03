package com.bergerkiller.mountiplex.types;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "rawtypes"})
public class TestObject {
    private static String a = "static_test";
    private static final String a_f = "static_final_test";
    private String b = "local_test";
    private final String b_f = "local_final_test";
    private int c = 12;
    public final List testRawField = new ArrayList();

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

    public static int h(int a, int b) {
        return (a * b);
    }
}
