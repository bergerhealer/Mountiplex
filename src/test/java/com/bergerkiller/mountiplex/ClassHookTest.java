package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.types.IsolatedDogHook;
import org.junit.Test;
import static org.junit.Assert.*;

import com.bergerkiller.mountiplex.reflection.ClassHook;
import com.bergerkiller.mountiplex.reflection.ClassInterceptor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

public class ClassHookTest {

    @Test
    public void testIsolatedHooking() throws Throwable {
        Dog dog = new Dog();
        assertEquals("Dog::woof()", dog.woof());

        // Create the IsolatedDogHook using a special custom class that only the IsolatedClassLoader
        // has access to. The library's own class loader should not be used solely to access it.
        IsolatedClassLoader loader = new IsolatedClassLoader(ClassHookTest.class.getClassLoader());
        Class<? extends ClassHook<?>> isolatedHookClass = (Class<? extends ClassHook<?>>) loader.loadClass(IsolatedDogHook.class.getName() + "$Isolated");

        ClassHook<?> hook = isolatedHookClass.getConstructor().newInstance();
        Dog hooked_dog = hook.hook(dog);

        assertEquals("IsolatedDogHook::theIsolatedWoofMethod()", hooked_dog.woof());
    }

    @Test
    public void testClassHookMain() {
        Dog dog = new Dog();

        assertEquals("Dog::woof()", dog.woof());

        DogHook hook = new DogHook();

        Dog hooked_dog = hook.hook(dog);
        assertEquals(Dog.class, ClassInterceptor.findBaseType(hooked_dog.getClass()));

        assertEquals("DogHook::theWoofMethod()", hooked_dog.woof());

        assertEquals("DogHook::theWoofMethod()", hook.theWoofMethod());
        assertEquals("Dog::woof()", hook.base.theWoofMethod());

        testTimings("    Original", new Dog());
        testTimings("Hooked proxy", hooked_dog);
    }

    @Test
    public void testClassHookBaseCalls() {
        Dog dog = new Dog();
        assertEquals("Dog::woof()", dog.woof());

        DogSuperHook hook = new DogSuperHook();
        Dog hooked_dog = hook.hook(dog);

        assertEquals("DogSuperHook::theWoofMethod() -> Dog::woof()", hooked_dog.woof());

        assertEquals("DogSuperHook::theWoofMethod() -> Dog::woof()", hook.theWoofMethod());
        assertEquals("Dog::woof()", hook.base.theWoofMethod());

        testTimings("    Original", new Dog());
        testTimings(" Hooked base", hooked_dog);
    }

    /*
     * Tests the correct working of mock() interception
     */
    @Test
    public void testMocking() {
        Dog dog = new Dog();
        DogHook hook = new DogHook();
        hook.mock(dog);

        assertEquals("Dog::woof()", dog.woof());
        assertEquals("Dog::woof()", hook.base.theWoofMethod());
        assertEquals("DogHook::theWoofMethod()", hook.theWoofMethod());
    }

    /**
     * A class loader that simulates this library being on its own class path.
     * It does not have access itself to the class being hooked.
     */
    public static class IsolatedClassLoader extends ClassLoader {

        public IsolatedClassLoader(ClassLoader loader) {
            super(loader);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.endsWith("$Isolated")) {
                String originalName = name.substring(0, name.length() - 9);

                try {
                    // Load original class bytecode
                    InputStream classStream = getParent().getResourceAsStream(originalName.replace('.', '/') + ".class");
                    if (classStream == null) throw new ClassNotFoundException(originalName);

                    byte[] modifiedBytes = modifyClassName(originalName, name, classStream);
                    return defineClass(name, modifiedBytes, 0, modifiedBytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException("Failed to transform class: " + originalName, e);
                }
            }

            return super.findClass(name);
        }

        private byte[] modifyClassName(String originalName, String newName, InputStream classStream) throws IOException {
            // Read class bytecode
            ClassReader reader = new ClassReader(classStream);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            // Change the class name
            classNode.name = newName.replace('.', '/');

            // Update the superclass reference (if needed)
            if (classNode.superName.equals(originalName.replace('.', '/'))) {
                classNode.superName = newName.replace('.', '/');
            }

            // Write the modified class back
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);
            return writer.toByteArray();
        }
    }

    public static class Dog {

        public String woof() {
            return "Dog::woof()";
        }

    }

    public class DogHook extends ClassHook<DogHook> {
        @HookMethod("public String woof()")
        public String theWoofMethod() {
            return "DogHook::theWoofMethod()";
        }
    }

    public class DogSuperHook extends ClassHook<DogSuperHook> {
        @HookMethod("public String woof()")
        public String theWoofMethod() {
            return "DogSuperHook::theWoofMethod() -> " + base.theWoofMethod();
        }
    }

    public class DogLoopbackHook extends ClassHook<DogLoopbackHook> {
        @HookMethod("public String woof()")
        public String theWoofMethod() {
            return base.theWoofMethod();
        }
    }

    private static void testTimings(String info, Dog dog) {
        // Call a couple times up-front to make sure everything is cached and ready for fair timings
        for (int i = 0; i < 10; i++) {
            dog.woof();
        }

        // Call woof() a whole lot of times and measure execution time
        long start = System.nanoTime();
        for (long i = 0; i < 1000000; i++) {
            dog.woof();
        }
        long diff = (System.nanoTime() - start) / 1000000;
        System.out.println("[" + info + "] Elapsed nano time: " + diff + " ms");
    }
}
