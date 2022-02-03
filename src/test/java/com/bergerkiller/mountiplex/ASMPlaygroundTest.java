package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;
import com.bergerkiller.mountiplex.reflection.util.fast.ClassFieldCopier;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

/**
 * This class contains no tests but offers room to play around with ASM utilities
 */
public class ASMPlaygroundTest {

    public static class Dummy {
        
        public Dummy(long k) {
            
        }
    }
    
    public static class Woo {
        public Dummy cool(long k) {
            return new Dummy(k);
        }
    }

    public static class MyCustomSerializer {

        public List<String> getValue(int k, byte d) {
            return Arrays.asList("a", "b");
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

    public static class Example {
        public int a = 10;
        public final String b;
        @SuppressWarnings("unused")
        private int c = 11;
        public String d = "d";
        public long e = 12;
        public final int f;

        public Example(String b, int f) {
            this.b = b;
            this.f = f;
        }
    }

    public static class TestCopier extends ClassFieldCopier<Example> {
        public static ReflectionCopier[] copiers;

        public static Field field_b;
        public static Field field_f;

        @Override
        protected void tryCopy(Example from, Example to) throws Throwable {
            super.copyAllWithReflection(copiers, from, to);

            to.a = from.a;
            to.e = from.e;
            field_b.set(to, from.b);
            field_f.setInt(to, from.f);
        }
    }

    @Ignore
    @Test
    public void testShowASM() {
        TestUtil.printASM(TestCopier.class);
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
