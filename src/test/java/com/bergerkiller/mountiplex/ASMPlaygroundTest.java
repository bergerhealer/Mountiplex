package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.security.ProtectionDomain;

import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;
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
        public HandleBase(String as, double a, double b, double c, String cool) {
            
        }
    }

    public static class Handle extends HandleBase {

        public Handle(String as, double a, double b, double c, String cool) {
            super(as, a, b, c, cool);
            // TODO Auto-generated constructor stub
        }

    }

    /**
     * As implemented at runtime
     */
    public static class ImplementedGeneratorClassLoader extends GeneratorClassLoader {

        protected ImplementedGeneratorClassLoader(ClassLoader base) {
            super(base);
        }

        @Override
        protected Class<?> defineClassFromBytecode(String name, byte[] b, ProtectionDomain protectionDomain) {
            return super.defineClass(name, b, 0, b.length, protectionDomain);
        }
    }

    @Ignore
    @Test
    public void testShowASM() {
        TestUtil.printASM(ImplementedGeneratorClassLoader.class);
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
