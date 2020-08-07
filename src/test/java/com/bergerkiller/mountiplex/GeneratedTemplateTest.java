package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

/**
 * Tests the @Template.Generated functionality for various permutations of supported logic
 */
public class GeneratedTemplateTest {

    @SuppressWarnings("unused")
    public static class HelperObject {
        public static int void_result = 0;

        public static void pub_test_0() {
            void_result = 100;
        }
        public static void pub_test_1(int arg0) {
            void_result = 100 + arg0;
        }
        public static void pub_test_2(int arg0, int arg1) {
            void_result = 100 + arg0 + arg1;
        }
        public static void pub_test_3(int arg0, int arg1, int arg2) {
            void_result = 100 + arg0 + arg1 + arg2;
        }
        public static void pub_test_4(int arg0, int arg1, int arg2, int arg3) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3;
        }
        public static void pub_test_5(int arg0, int arg1, int arg2, int arg3, int arg4) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4;
        }
        public static void pub_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4 + arg5;
        }
        public static void pub_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4 + arg5 + arg6;
        }
        public static int pub_test_ireturn() {
            return 500;
        }
        public static String pub_test_areturn() {
            return "500";
        }

        private static void priv_test_0() {
            void_result = 100;
        }
        private static void priv_test_1(int arg0) {
            void_result = 100 + arg0;
        }
        private static void priv_test_2(int arg0, int arg1) {
            void_result = 100 + arg0 + arg1;
        }
        private static void priv_test_3(int arg0, int arg1, int arg2) {
            void_result = 100 + arg0 + arg1 + arg2;
        }
        private static void priv_test_4(int arg0, int arg1, int arg2, int arg3) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3;
        }
        private static void priv_test_5(int arg0, int arg1, int arg2, int arg3, int arg4) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4;
        }
        private static void priv_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4 + arg5;
        }
        private static void priv_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6) {
            void_result = 100 + arg0 + arg1 + arg2 + arg3 + arg4 + arg5 + arg6;
        }
        private static int priv_test_ireturn() {
            return 600;
        }
        private static String priv_test_areturn() {
            return "600";
        }
    }

    @Template.InstanceType("com.bergerkiller.mountiplex.GeneratedTemplateTest.HelperObject")
    public static abstract class GeneratedClass extends Template.Class<Template.Handle> {
        @Template.Generated("public static void pub_test_0()")
        public abstract void pub_test_0();
        @Template.Generated("public static void pub_test_1(int arg0)")
        public abstract void pub_test_1(int arg0);
        @Template.Generated("public static void pub_test_2(int arg0, int arg1)")
        public abstract void pub_test_2(int arg0, int arg1);
        @Template.Generated("public static void pub_test_3(int arg0, int arg1, int arg2)")
        public abstract void pub_test_3(int arg0, int arg1, int arg2);
        @Template.Generated("public static void pub_test_4(int arg0, int arg1, int arg2, int arg3)")
        public abstract void pub_test_4(int arg0, int arg1, int arg2, int arg3);
        @Template.Generated("public static void pub_test_5(int arg0, int arg1, int arg2, int arg3, int arg4)")
        public abstract void pub_test_5(int arg0, int arg1, int arg2, int arg3, int arg4);
        @Template.Generated("public static void pub_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5)")
        public abstract void pub_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5);
        @Template.Generated("public static void pub_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6)")
        public abstract void pub_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6);
        @Template.Generated("public static int pub_test_ireturn()")
        public abstract int pub_test_ireturn();
        @Template.Generated("public static String pub_test_areturn()")
        public abstract String pub_test_areturn();

        @Template.Generated("private static void priv_test_0()")
        public abstract void priv_test_0();
        @Template.Generated("private static void priv_test_1(int arg0)")
        public abstract void priv_test_1(int arg0);
        @Template.Generated("private static void priv_test_2(int arg0, int arg1)")
        public abstract void priv_test_2(int arg0, int arg1);
        @Template.Generated("private static void priv_test_3(int arg0, int arg1, int arg2)")
        public abstract void priv_test_3(int arg0, int arg1, int arg2);
        @Template.Generated("private static void priv_test_4(int arg0, int arg1, int arg2, int arg3)")
        public abstract void priv_test_4(int arg0, int arg1, int arg2, int arg3);
        @Template.Generated("private static void priv_test_5(int arg0, int arg1, int arg2, int arg3, int arg4)")
        public abstract void priv_test_5(int arg0, int arg1, int arg2, int arg3, int arg4);
        @Template.Generated("private static void priv_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5)")
        public abstract void priv_test_6(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5);
        @Template.Generated("private static void priv_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6)")
        public abstract void priv_test_7(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg6);
        @Template.Generated("private static int priv_test_ireturn()")
        public abstract int priv_test_ireturn();
        @Template.Generated("private static String priv_test_areturn()")
        public abstract String priv_test_areturn();
    }

    // Proxies the call directly, because the method is accessible
    @Test
    public void testManyParametersWithoutInvoker() {
        GeneratedClass T = Template.Class.create(GeneratedClass.class);

        HelperObject.void_result = 0; T.pub_test_0();
        assertEquals(100, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_1(1);
        assertEquals(101, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_2(1, 2);
        assertEquals(103, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_3(1, 2, 4);
        assertEquals(107, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_4(1, 2, 4, 8);
        assertEquals(115, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_5(1, 2, 4, 8, 16);
        assertEquals(131, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_6(1, 2, 4, 8, 16, 32);
        assertEquals(163, HelperObject.void_result);

        HelperObject.void_result = 0; T.pub_test_7(1, 2, 4, 8, 16, 32, 64);
        assertEquals(227, HelperObject.void_result);
    }

    // Has to use invoker because the method is private, we expect all the boxing/unboxing/paramfilling to work
    @Test
    public void testManyParametersWithInvoker() {
        GeneratedClass T = Template.Class.create(GeneratedClass.class);

        HelperObject.void_result = 0; T.priv_test_0();
        assertEquals(100, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_1(1);
        assertEquals(101, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_2(1, 2);
        assertEquals(103, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_3(1, 2, 4);
        assertEquals(107, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_4(1, 2, 4, 8);
        assertEquals(115, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_5(1, 2, 4, 8, 16);
        assertEquals(131, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_6(1, 2, 4, 8, 16, 32);
        assertEquals(163, HelperObject.void_result);

        HelperObject.void_result = 0; T.priv_test_7(1, 2, 4, 8, 16, 32, 64);
        assertEquals(227, HelperObject.void_result);
    }

    // Tests the proper return codes used when the method is public
    @Test
    public void testReturnTypesWithoutInvoker() {
        GeneratedClass T = Template.Class.create(GeneratedClass.class);

        assertEquals(500, T.pub_test_ireturn());
        assertEquals("500", T.pub_test_areturn());
    }

    // Tests the proper return codes used when the method is private, and an Invoker is used
    @Test
    public void testReturnTypesWithInvoker() {
        GeneratedClass T = Template.Class.create(GeneratedClass.class);

        assertEquals(600, T.priv_test_ireturn());
        assertEquals("600", T.priv_test_areturn());
    }
}
