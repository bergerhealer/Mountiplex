package com.bergerkiller.mountiplex;

import org.junit.Test;

import com.bergerkiller.mountiplex.types.SpeedTestObject;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

/**
 * WIP test code
 */
public class JavassistTest {

    public static abstract class TestClass {
        
        public abstract void doTest(Object instance);
    }

    @Test
    public void testJavassist() {
        try {
            
            CtClass origClazz = ClassPool.getDefault().getCtClass(TestClass.class.getName());
            CtClass subClass = ClassPool.getDefault().makeClass(origClazz.getName() + "New", origClazz);
            
            CtMethod m = CtNewMethod.make(
                         "public void doTest(Object instance) { System.out.println(instance); }",
                         subClass );
            subClass.addMethod(m);

            TestClass tc = (TestClass) subClass.toClass().newInstance();
            tc.doTest(new SpeedTestObject());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
