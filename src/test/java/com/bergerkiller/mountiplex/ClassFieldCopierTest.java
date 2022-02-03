package com.bergerkiller.mountiplex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.util.fast.ClassFieldCopier;

/**
 * Tests various conditions that the ClassFieldCopier must handle
 */
public class ClassFieldCopierTest {

    public static class PublicTestObjectFinalFields {
        public final boolean f_bool;
        public final byte f_byte;
        public final char f_char;
        public final short f_short;
        public final int f_int;
        public final long f_long;
        public final float f_float;
        public final double f_double;

        public PublicTestObjectFinalFields(boolean f_bool, byte f_byte, char f_char, short f_short, int f_int, long f_long, float f_float, double f_double) {
            this.f_bool = f_bool;
            this.f_byte = f_byte;
            this.f_char = f_char;
            this.f_short = f_short;
            this.f_int = f_int;
            this.f_long = f_long;
            this.f_float = f_float;
            this.f_double = f_double;
        }
    }

    @Test
    public void testPublicFinalFields() {
        PublicTestObjectFinalFields from = new PublicTestObjectFinalFields(false, (byte) 11, 'A', (short) 12, 13, 14, 15.0f, 16.0);
        PublicTestObjectFinalFields to   = new PublicTestObjectFinalFields(true, (byte) 10, 'B', (short) 22, 23, 24, 25.0f, 26.0);

        ClassFieldCopier.of(PublicTestObjectFinalFields.class).copy(from, to);

        assertEquals(false, from.f_bool);
        assertEquals((byte) 11, from.f_byte);
        assertEquals('A', from.f_char);
        assertEquals((short) 12, from.f_short);
        assertEquals(13, from.f_int);
        assertEquals(14, from.f_long);
        assertEquals(15.0f, from.f_float, 0.0);
        assertEquals(16.0, from.f_double, 0.0);

        assertEquals(false, to.f_bool);
        assertEquals((byte) 11, to.f_byte);
        assertEquals('A', to.f_char);
        assertEquals((short) 12, to.f_short);
        assertEquals(13, to.f_int);
        assertEquals(14, to.f_long);
        assertEquals(15.0f, to.f_float, 0.0);
        assertEquals(16.0, to.f_double, 0.0);
    }

    public static class PublicTestObjectPrivateFields {
        private boolean f_bool;
        private byte f_byte;
        private char f_char;
        private short f_short;
        private int f_int;
        private long f_long;
        private float f_float;
        private double f_double;

        public PublicTestObjectPrivateFields(boolean f_bool, byte f_byte, char f_char, short f_short, int f_int, long f_long, float f_float, double f_double) {
            this.f_bool = f_bool;
            this.f_byte = f_byte;
            this.f_char = f_char;
            this.f_short = f_short;
            this.f_int = f_int;
            this.f_long = f_long;
            this.f_float = f_float;
            this.f_double = f_double;
        }
    }

    @Test
    public void testPrivateFields() {
        PublicTestObjectPrivateFields from = new PublicTestObjectPrivateFields(false, (byte) 11, 'A', (short) 12, 13, 14, 15.0f, 16.0);
        PublicTestObjectPrivateFields to   = new PublicTestObjectPrivateFields(true, (byte) 10, 'B', (short) 22, 23, 24, 25.0f, 26.0);

        ClassFieldCopier.of(PublicTestObjectPrivateFields.class).copy(from, to);

        assertEquals(false, from.f_bool);
        assertEquals((byte) 11, from.f_byte);
        assertEquals('A', from.f_char);
        assertEquals((short) 12, from.f_short);
        assertEquals(13, from.f_int);
        assertEquals(14, from.f_long);
        assertEquals(15.0f, from.f_float, 0.0);
        assertEquals(16.0, from.f_double, 0.0);

        assertEquals(false, to.f_bool);
        assertEquals((byte) 11, to.f_byte);
        assertEquals('A', to.f_char);
        assertEquals((short) 12, to.f_short);
        assertEquals(13, to.f_int);
        assertEquals(14, to.f_long);
        assertEquals(15.0f, to.f_float, 0.0);
        assertEquals(16.0, to.f_double, 0.0);
    }

    public static class PublicTestMixedObject {
        public static String st_unused_str = "hello";
        public final int a;
        public int b;
        protected int c;

        public PublicTestMixedObject(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @Test
    public void testPublicMixed() {
        PublicTestMixedObject from = new PublicTestMixedObject(10, 11, 12);
        PublicTestMixedObject to   = new PublicTestMixedObject(20, 21, 22);

        ClassFieldCopier.of(PublicTestMixedObject.class).copy(from, to);

        assertEquals(10, from.a);
        assertEquals(11, from.b);
        assertEquals(12, from.c);

        assertEquals(10, to.a);
        assertEquals(11, to.b);
        assertEquals(12, to.c);

        assertEquals("hello", PublicTestMixedObject.st_unused_str);
    }

    private static class PrivateTestMixedObject {
        public static String st_unused_str = "hello";
        public final int a;
        public int b;
        protected int c;

        public PrivateTestMixedObject(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    @Test
    public void testPrivateMixed() {
        PrivateTestMixedObject from = new PrivateTestMixedObject(10, 11, 12);
        PrivateTestMixedObject to   = new PrivateTestMixedObject(20, 21, 22);

        ClassFieldCopier.of(PrivateTestMixedObject.class).copy(from, to);

        assertEquals(10, from.a);
        assertEquals(11, from.b);
        assertEquals(12, from.c);

        assertEquals(10, to.a);
        assertEquals(11, to.b);
        assertEquals(12, to.c);

        assertEquals("hello", PrivateTestMixedObject.st_unused_str);
    }

    public static class PublicExtendedObject extends PublicTestMixedObject {
        public String d;

        public PublicExtendedObject(int a, int b, int c, String d) {
            super(a, b, c);
            this.d = d;
        }
    }

    @Test
    public void testPublicExtended() {
        PublicExtendedObject from = new PublicExtendedObject(10, 11, 12, "13");
        PublicExtendedObject to   = new PublicExtendedObject(20, 21, 22, "23");

        ClassFieldCopier.of(PublicExtendedObject.class).copy(from, to);

        assertEquals(10, from.a);
        assertEquals(11, from.b);
        assertEquals(12, from.c);
        assertEquals("13", from.d);

        assertEquals(10, to.a);
        assertEquals(11, to.b);
        assertEquals(12, to.c);
        assertEquals("13", to.d);
    }
}
