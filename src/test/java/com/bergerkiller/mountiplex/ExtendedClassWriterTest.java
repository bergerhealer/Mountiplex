package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.types.TestObject;

public class ExtendedClassWriterTest {

    @Test
    public void testGenerateExtendedClass() {
        // Generate a class with a static field
        String name = "com.bergerkiller.mountiplex.ExtendedClassWriterTest$TestObject";
        ExtendedClassWriter<TestObject> writer = ExtendedClassWriter.builder(TestObject.class).setExactName(name).build();
        writer.visitStaticField("test", String.class, "This is the test");
        Class<?> type = writer.generate();

        // Now try to generate a method that accesses the static field of the class we just generated
        ClassResolver resolver = new ClassResolver();
        resolver.addImport(TestObject.class.getName());
        resolver.setDeclaredClass(type, name);
        MethodDeclaration mDec = new MethodDeclaration(resolver,
                "public static String get() {\n" +
                "    return " + name + ".test;\n" +
                "}");
        FastMethod<String> method = new FastMethod<String>();
        method.init(mDec);
        assertEquals("This is the test", method.invoke(null));
    }
}
