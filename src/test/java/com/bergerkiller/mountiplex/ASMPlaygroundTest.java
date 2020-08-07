package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;
import com.bergerkiller.mountiplex.types.TestObject;

/**
 * This class contains no tests but offers room to play around with ASM utilities
 */
public class ASMPlaygroundTest {
    
    public static interface TestInterface {
        void theMethod(TestObject instance, int value1, int value2);
    }

    public static abstract class HandleBase {
        public abstract String someMethod(int k, int w, int c, int bb, int bbb, int bbbb);
    }

    public static class Handle {
        public static final Invoker<Integer> invoker = null;

        public int haha(int arg0, String arg1) {
            return ((Integer) invoker.invokeVA(null, arg0, arg1)).intValue();
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
