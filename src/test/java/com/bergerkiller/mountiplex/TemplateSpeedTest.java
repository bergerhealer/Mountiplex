package com.bergerkiller.mountiplex;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.types.SpeedTestObject;
import com.bergerkiller.mountiplex.types.SpeedTestObjectHandle;

public class TemplateSpeedTest {

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
                                      "}\n";

                    return SourceDeclaration.parse(template).classes[0];
                }
                return null;
            }
        });
    }

    private void measure(String testName, Runnable runnable) {
        long startTime = System.currentTimeMillis();
        for (long ctr = 0; ctr < 2000000L; ctr++) {
            runnable.run();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Execution time of " + testName + ": " + (endTime - startTime) + "ms");
    }

    public static class IntSetter {
        public void set(Object instance, int value) {
            ((SpeedTestObject) instance).i = value;
        }

        public int get(Object instance) {
            return ((SpeedTestObject) instance).i;
        }
    }
    
    @Test
    public void testPrimitive() {
        final SpeedTestObject object = new SpeedTestObject();
        final SpeedTestObjectHandle handle = SpeedTestObjectHandle.createHandle(object);
        final IntSetter setter = new IntSetter();

        measure("direct field access", new Runnable() {
            @Override
            public void run() {
                object.i++;
            }
        });
        measure("property field access", new Runnable() {
            @Override
            public void run() {
                object.setI(object.getI() + 1);
            }
        });
        measure("handle field access", new Runnable() {
            @Override
            public void run() {
                handle.setI(handle.getI() + 1);
            }
        });
        measure("class field access", new Runnable() {
            @Override
            public void run() {
                SpeedTestObjectHandle.T.i.setInteger(object, SpeedTestObjectHandle.T.i.getInteger(object) + 1);
            }
        });
        measure("runtime property field access", new Runnable() {
            @Override
            public void run() {
                setter.set(object, setter.get(object) + 1);
            }
        });
    }
}
