package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Template;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.TestObjectHandle;

/**
 * This class contains no tests but offers room to play around with ASM utilities
 */
public class ASMPlaygroundTest {
    
    public static interface TestInterface {
        void theMethod(TestObject instance, int value1, int value2);
    }

    public static class Handle {
        public static final Class T = new Class();
        public TestObject instance;

        public void theMethod(int value1, int value2) {
            TestObjectHandle.T.testFunc.invoker.invoke(instance, value1, value2);
        }

        public java.lang.Class<?> getSelfClassType() {
            return getClass().getSuperclass();
        }

        public static class Class {
            public Template.Method<Integer> theMethod;
        }
    }

    public static class TheCoolThing extends InitInvoker<Object> {
        @Override
        protected Invoker<Object> create() {
            return null;
        }
        
        public Class<?> getInterface() {
            return TestInterface.class;
        }
        
        public char aaa() {
            return 0;
        }
    }

    @Ignore
    @Test
    public void testShowASM() {
        TestUtil.printASM(Handle.class);
    }

    private Invoker<Object> invoker;

    @Test
    public void generate() {
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(getClass());
        MethodDeclaration testMethod = new MethodDeclaration(resolver,
                "public String runMethod(Object argument) {\n" +
                "  return argument.toString();\n" +
                "}");
        invoker = InitInvoker.forMethod(this, "invoker", testMethod);
        assertEquals("13", invoker.invoke(this, 13));
        assertFalse(invoker instanceof InitInvoker);
    }
}
