package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.InvalidArgumentCountException;
import com.bergerkiller.mountiplex.types.SpeedTestObject;
import com.bergerkiller.mountiplex.types.SpeedTestObjectHandle;

public class MethodSpeedTest {

    static {
        Resolver.registerClassDeclarationResolver(new ClassDeclarationResolver() {
            @Override
            public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
                if (classType.equals(SpeedTestObject.class)) {
                    String template = "package com.bergerkiller.mountiplex.types;\n" +
                                      "\n" +
                                      "public class SpeedTestObject {\n" +
                                      "    private int i;\n" +
                                      "    private double d;\n" +
                                      "    private String s;\n" +
                                      "    public final int getIMethod();\n" +
                                      "    public final void setIMethod(int value);\n" +
                                      "    public final void setSMethod(String value);\n" +
                                      "    public final String getSMethod();\n" +
                                      "    public void setLocation(double x, double y, double z, float yaw, float pitch);\n" +
                                      "    public int lotsOfArgs(int a, int b, int c, int d, int e, int f, int g);\n" +
                                      "}\n";

                    return SourceDeclaration.parse(template).classes[0];
                }
                return null;
            }

            @Override
            public void resolveClassVariables(String classPath, Class<?> classType, Map<String, String> variables) {
            }
        });
    }

    private void measure(String testName, Runnable runnable) {
        // First run the loop shortly without measuring
        // This makes sure we exclude initialization time from the measurement
        for (int i = 0; i < 100; i++) {
            runnable.run();
        }

        // Run the test in a tight loop while measuring
        long startTime = System.currentTimeMillis();
        for (long ctr = 0; ctr < 10000000L; ctr++) {
            runnable.run();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Execution time of " + testName + ": " + (endTime - startTime) + "ms");
    }

    public static class CustomGenSet extends GeneratedInvoker<Object> {

        @Override
        public Object invokeVA(Object instance, Object... args) {
            if (args.length != 1) {
                throw new InvalidArgumentCountException("method", args.length, 1);
            } else {
                return invoke(instance, args[0]);
            }
        }

        @Override
        public Object invoke(Object instance, Object arg0) {
            ((SpeedTestObject) instance).setSMethod((String) arg0);
            return null;
        }
    }

    public static class CustomGenGet extends GeneratedInvoker<Object> {

        @Override
        public Object invokeVA(Object instance, Object... args) {
            if (args.length != 0) {
                throw new InvalidArgumentCountException("method", args.length, 0);
            } else {
                return invoke(instance);
            }
        }

        @Override
        public Object invoke(Object instance) {
            return ((SpeedTestObject) instance).getSMethod();
        }
    }

    @Test
    public void testMethodSpeed() {
        final SpeedTestObject object = new SpeedTestObject();
        final SpeedTestObjectHandle objectHandle = SpeedTestObjectHandle.createHandle(object);
        final CustomGenSet setter = new CustomGenSet();
        final CustomGenGet getter = new CustomGenGet();

        // Quickly test all generated things
        assertEquals(28, objectHandle.publicLotsOfArgs(1, 2, 3, 4, 5, 6, 7));
        assertEquals(35, objectHandle.privateLotsOfArgs(2, 3, 4, 5, 6, 7, 8));
        objectHandle.setLocation(2.0, 5.0, 7.0, 2.0f, 1.0f);

        //TestUtil.printASM(CustomGenGet.class);

        //if (true) return;

        object.s = "test1";
        assertEquals("test1", SpeedTestObjectHandle.T.getSMethod.invokeVA(object));

        SpeedTestObjectHandle.T.setSMethod.invokeVA(object, "test2");
        assertEquals("test2", object.s);
        
        measure("Direct method call", new Runnable() {
            @Override
            public void run() {
                object.setSMethod(object.getSMethod());
            }
        });
        measure("Template method call", new Runnable() {
            @Override
            public void run() {
                SpeedTestObjectHandle.T.setSMethod.invoke(object, SpeedTestObjectHandle.T.getSMethod.invoke(object));
            }
        });
        measure("Precompiled generated method call", new Runnable() {
            @Override
            public void run() {
                setter.invoke(object, getter.invoke(object));
            }
        });
        measure("Generated handle method call", new Runnable() {
            @Override
            public void run() {
                objectHandle.setSMethod(objectHandle.getSMethod());
            }
        });
    }
}
