package com.bergerkiller.mountiplex.reflection.util.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Takes the original bytecode of a Class and alters
 * String-storing annotation values used within.
 */
public class AnnotationReplacer {

    public static byte[] replace(byte[] originalBytecode, Transformer transformer) {
        ClassReader cr = new ClassReader(originalBytecode);
        ClassWriter cw = new ClassWriter(0);
        Adapter a = new Adapter(cw, transformer);
        cr.accept(a, 0);
        return cw.toByteArray();
    }

    public static interface Transformer {
        String transform(String annotationName, String originalValue);
    }

    private static class Adapter extends ClassVisitor {
        private final Transformer transformer;

        public Adapter(ClassVisitor cv, Transformer transformer) {
            super(org.objectweb.asm.Opcodes.ASM6, cv);
            this.transformer = transformer;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
        {
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions)
        {
            return new MethodVisitor(Adapter.this.api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                    return new AnnotationAdapter(super.visitAnnotation(descriptor, visible), descriptor);
                }
            };
        }

        @Override
        public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value)
        {
            return new FieldVisitor(Adapter.this.api, super.visitField(access, name, descriptor, signature, value)) {
                @Override
                public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                    return new AnnotationAdapter(super.visitAnnotation(descriptor, visible), descriptor);
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            return new AnnotationAdapter(super.visitAnnotation(descriptor, visible), descriptor);
        }

        private class AnnotationAdapter extends AnnotationVisitor {
            private final String annotationName;

            public AnnotationAdapter(AnnotationVisitor annotationVisitor, String annotationDescriptor) {
                super(Adapter.this.api, annotationVisitor);
                this.annotationName = Type.getType(annotationDescriptor).getClassName();
            }

            @Override
            public void visit(final String name, final Object value) {
                if (value instanceof String) {
                    super.visit(name, transformer.transform(this.annotationName, (String) value));
                } else {
                    super.visit(name, value);
                }
            }
        }
    }
}
