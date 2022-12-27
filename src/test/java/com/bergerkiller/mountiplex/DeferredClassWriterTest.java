package com.bergerkiller.mountiplex;

import org.junit.Test;
import org.objectweb.asm.MethodVisitor;

import static org.junit.Assert.*;
import static org.objectweb.asm.Opcodes.*;

import java.util.concurrent.atomic.AtomicBoolean;

import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

/**
 * Tests the correct functioning and timing of ExtendedClassWriter Builder defer(callback)
 */
public class DeferredClassWriterTest {

    public static interface DoThing {
        int doThing();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testStatic() {
        final AtomicBoolean generated = new AtomicBoolean(false);
        ExtendedClassWriter.Deferred<DoThing> deferred = ExtendedClassWriter.builder(DoThing.class).defer(writer -> {
            generated.set(true);

            MethodVisitor mv;

            {
                mv = writer.visitMethod(ACC_PUBLIC, "doThing", "()I", null, null);
                mv.visitCode();
                mv.visitIntInsn(BIPUSH, 22);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            {
                mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "doThingStatic", "()I", null, null);
                mv.visitCode();
                mv.visitIntInsn(BIPUSH, 23);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            return writer.generateInstanceNull();
        });

        assertFalse(generated.get());

        // Generate a class which calls the deferred class static method
        DoThing tester;
        {
            ExtendedClassWriter<DoThing> writer = ExtendedClassWriter.builder(DoThing.class)
                    .build();

            MethodVisitor mv;

            {
                mv = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = writer.visitMethod(ACC_PUBLIC, "doThing", "()I", null, null);
                mv.visitCode();
                mv.visitMethodInsn(INVOKESTATIC, deferred.getInternalName(), "doThingStatic", "()I", false);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            assertFalse(generated.get());
            Class<DoThing> typeGenerated = writer.generate();
            assertFalse(generated.get());
            tester = null;
            try {
                tester = typeGenerated.newInstance();
            } catch (InstantiationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        // It should be generated the first time the method is called, and not before
        assertFalse(generated.get());
        assertEquals(23, tester.doThing());
        assertTrue(generated.get());
    }
}
