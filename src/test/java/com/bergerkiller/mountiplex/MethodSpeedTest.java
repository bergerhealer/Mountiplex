package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedInvoker;
import com.bergerkiller.mountiplex.types.SpeedTestObject;
import com.bergerkiller.mountiplex.types.SpeedTestObjectHandle;

public class MethodSpeedTest {

    static {
        Resolver.registerClassDeclarationResolver(new ClassDeclarationResolver() {
            @Override
            public ClassDeclaration resolveClassDeclaration(Class<?> classType) {
                if (classType.equals(SpeedTestObject.class)) {
                    String template = "package com.bergerkiller.mountiplex.types;\n" +
                                      "\n" +
                                      "public class SpeedTestObject {\n" +
                                      "    private int i;\n" +
                                      "    private double d;\n" +
                                      "    private String s;\n" +
                                      "    public final void setS(String value);\n" +
                                      "    public final String getS();\n" +
                                      "}\n";

                    return SourceDeclaration.parse(template).classes[0];
                }
                return null;
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

    public static class CustomGenSet extends GeneratedInvoker {

        public CustomGenSet(Method method) {
            super(method);
        }

        @Override
        public Object invokeVA(Object instance, Object... args) {
            if (args.length != 1) {
                throw failArgs(args.length);
            } else {
                return invoke(instance, args[0]);
            }
        }

        @Override
        public Object invoke(Object instance, Object arg0) {
            ((SpeedTestObject) instance).setS((String) arg0);
            return null;
        }
    }

    public static class CustomGenGet extends GeneratedInvoker {

        public CustomGenGet(Method method) {
            super(method);
        }

        @Override
        public Object invokeVA(Object instance, Object... args) {
            if (args.length != 0) {
                throw failArgs(args.length);
            } else {
                return invoke(instance);
            }
        }

        @Override
        public Object invoke(Object instance) {
            return ((SpeedTestObject) instance).getS();
        }

        @Override
        public Object invoke(Object instance, Object arg0, Object arg1) {
            return ((SpeedTestObject) instance).test((String) arg0, (Integer) arg1);
        }
    }

    public static interface SomeInterface {
        public void save();
    }
    
    @Test
    public void testMethodSpeed() {
        final SpeedTestObject object = new SpeedTestObject();
        final CustomGenSet setter = new CustomGenSet(SpeedTestObjectHandle.T.setS.toJavaMethod());
        final CustomGenGet getter = new CustomGenGet(SpeedTestObjectHandle.T.getS.toJavaMethod());

        //TestUtil.printASM(CustomGenGet.class);
        
        //if (true) return;
        
        object.s = "test1";
        assertEquals("test1", SpeedTestObjectHandle.T.getS.invokeVA(object));

        SpeedTestObjectHandle.T.setS.invokeVA(object, "test2");
        assertEquals("test2", object.s);
        
        measure("Direct method call", new Runnable() {
            @Override
            public void run() {
                object.setS(object.getS());
            }
        });
        measure("Reflection method call", new Runnable() {
            @Override
            public void run() {
                SpeedTestObjectHandle.T.setS.invoke(object, SpeedTestObjectHandle.T.getS.invoke(object));
            }
        });
        measure("Precompiled generated method call", new Runnable() {
            @Override
            public void run() {
                setter.invoke(object, getter.invoke(object));
            }
        });
    }
}
