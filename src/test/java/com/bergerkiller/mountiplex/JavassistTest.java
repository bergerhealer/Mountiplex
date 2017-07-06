package com.bergerkiller.mountiplex;

import org.junit.Test;

import com.bergerkiller.mountiplex.types.SpeedTestObject;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

/**
 * WIP test code
 */
public class JavassistTest {

    public static abstract class TestClass {
        
        public abstract void doTest(Object instance);
    }

    private CtMethod overrideMethod(CtClass ctClass, CtMethod getConnectionMethodOfSuperclass)
            throws NotFoundException, CannotCompileException {
        final CtMethod m = CtNewMethod.delegator(getConnectionMethodOfSuperclass, ctClass);
        ctClass.addMethod(m);
        return m;
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
