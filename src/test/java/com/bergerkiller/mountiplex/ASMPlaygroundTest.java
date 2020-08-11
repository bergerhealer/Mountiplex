package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        public String getClassName(Class<?> clazz) {
            return clazz.getName();
        }

        public String getMethodName(Method method) {
            return method.getName();
        }

        public String getFieldName(Field field) {
            return field.getName();
        }
        
        public Method getDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
            return clazz.getDeclaredMethod(name, parameterTypes);
        }

        public Field getDeclaredField(Class<?> clazz, String name) throws NoSuchFieldException, SecurityException {
            return clazz.getDeclaredField(name);
        }

        public Class<?> getClassByName(String name, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
            return Class.forName(name, initialize, classLoader);
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
