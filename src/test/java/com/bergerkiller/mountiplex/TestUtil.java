package com.bergerkiller.mountiplex;

import java.io.PrintWriter;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.ASMifierClassVisitor;

public class TestUtil {

    public static void printASM(Class<?> type) {
        ClassReader localClassReader;
        try {
            localClassReader = new ClassReader(type.getName());
            localClassReader.accept(new ASMifierClassVisitor(new PrintWriter(System.out)), new Attribute[0], 2);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void measure(String testName, Runnable runnable) {
        // First run the loop shortly without measuring
        // This makes sure we exclude initialization time from the measurement
        for (int i = 0; i < 100; i++) {
            runnable.run();
        }

        // Run the test in a tight loop while measuring
        long startTime = System.currentTimeMillis();
        for (long ctr = 0; ctr < 2000000L; ctr++) {
            runnable.run();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Execution time of " + testName + ": " + (endTime - startTime) + "ms");
    }
}
