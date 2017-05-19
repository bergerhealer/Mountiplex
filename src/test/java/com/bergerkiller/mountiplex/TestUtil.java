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
}
