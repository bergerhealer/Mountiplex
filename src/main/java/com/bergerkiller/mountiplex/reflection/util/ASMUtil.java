package com.bergerkiller.mountiplex.reflection.util;

import java.io.InputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

import net.sf.cglib.asm.AnnotationVisitor;
import net.sf.cglib.asm.Attribute;
import net.sf.cglib.asm.ClassReader;
import net.sf.cglib.asm.ClassVisitor;
import net.sf.cglib.asm.FieldVisitor;
import net.sf.cglib.asm.Label;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;

public class ASMUtil {

    /**
     * Reads the raw .class file to resolve all Class types used in contained fields, methods and subclasses.
     * 
     * @param type to find all used types for
     * @return a collection of used types, sorted by name
     */
    public static final Collection<Class<?>> findUsedTypes(Class<?> type) {
        final FoundTypeSet result = new FoundTypeSet(type.getClassLoader());
        try {
            InputStream classStream = type.getResourceAsStream(type.getSimpleName() + ".class");
            if (classStream == null) {
                return result;
            }
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor() {

                @Override
                public void visit(int ver, int acc, String name, String sig, String supername, String[] interfaces) {
                    addType(result, Type.getObjectType(name));
                    if (supername != null) {
                        addType(result, Type.getObjectType(supername));
                    }
                    if (interfaces != null) {
                        for (String iif : interfaces) {
                            addType(result, Type.getObjectType(iif));
                        }
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(String arg0, boolean arg1) {
                    return null;
                }

                @Override
                public void visitAttribute(Attribute arg0) {
                }

                @Override
                public void visitEnd() {
                }

                @Override
                public FieldVisitor visitField(int arg0, String name, String desc, String sig, Object value) {
                    addType(result, Type.getType(desc));
                    return null;
                }

                @Override
                public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
                }

                @Override
                public MethodVisitor visitMethod(int arg0, String name, String desc, String sig, String[] exceptions) {
                    for (Type t : Type.getArgumentTypes(desc)) {
                        addType(result, t);
                    }
                    addType(result, Type.getReturnType(desc));
                    return new MethodVisitor() {

                        @Override
                        public AnnotationVisitor visitAnnotation(String arg0, boolean arg1) {
                            return null;
                        }

                        @Override
                        public AnnotationVisitor visitAnnotationDefault() {
                            return null;
                        }

                        @Override
                        public void visitAttribute(Attribute arg0) {
                        }

                        @Override
                        public void visitCode() {
                        }

                        @Override
                        public void visitEnd() {
                        }

                        @Override
                        public void visitFieldInsn(int arg0, String owner, String name, String desc) {
                            addType(result, Type.getObjectType(owner));
                            addType(result, Type.getType(desc));
                        }

                        @Override
                        public void visitFrame(int arg0, int arg1, Object[] arg2, int arg3, Object[] arg4) {
                        }

                        @Override
                        public void visitIincInsn(int arg0, int arg1) {
                        }

                        @Override
                        public void visitInsn(int arg0) {
                        }

                        @Override
                        public void visitIntInsn(int arg0, int arg1) {
                        }

                        @Override
                        public void visitJumpInsn(int arg0, Label arg1) {
                        }

                        @Override
                        public void visitLabel(Label arg0) {
                        }

                        @Override
                        public void visitLdcInsn(Object arg0) {
                            if (arg0 instanceof Type) {
                                addType(result, (Type) arg0);
                            }
                        }

                        @Override
                        public void visitLineNumber(int arg0, Label arg1) {
                        }

                        @Override
                        public void visitLocalVariable(String name, String desc, String sig, Label arg3, Label arg4, int arg5) {
                            addType(result, Type.getType(desc));
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {
                        }

                        @Override
                        public void visitMaxs(int arg0, int arg1) {
                        }

                        @Override
                        public void visitMethodInsn(int arg0, String owner, String name, String desc) {
                            addType(result, Type.getObjectType(owner));
                            for (Type t : Type.getArgumentTypes(desc)) {
                                addType(result, t);
                            }
                            addType(result, Type.getReturnType(desc));
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String arg0, int arg1) {
                            addType(result, Type.getType(arg0));
                        }

                        @Override
                        public AnnotationVisitor visitParameterAnnotation(int arg0, String arg1, boolean arg2) {
                            return null;
                        }

                        @Override
                        public void visitTableSwitchInsn(int arg0, int arg1, Label arg2, Label[] arg3) {
                        }

                        @Override
                        public void visitTryCatchBlock(Label arg0, Label arg1, Label arg2, String arg3) {
                        }

                        @Override
                        public void visitTypeInsn(int arg0, String arg1) {
                            addType(result, Type.getObjectType(arg1));
                        }

                        @Override
                        public void visitVarInsn(int arg0, int arg1) {
                        }
                    };
                }

                @Override
                public void visitOuterClass(String arg0, String arg1, String arg2) {
                }

                @Override
                public void visitSource(String arg0, String arg1) {
                }
            }, 0);

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return result;
    }

    private static void addType(FoundTypeSet result, Type type) {
        result.add(type);
    }

    private static class FoundTypeSet extends TreeSet<Class<?>> {
        private static final long serialVersionUID = 1L;
        private final ClassLoader loader;

        public FoundTypeSet(ClassLoader loader) {
            super(new Comparator<Class<?>>() {
                @Override
                public int compare(Class<?> o1, Class<?> o2) {
                    if (o1.isPrimitive() != o2.isPrimitive()) {
                        if (o1.isPrimitive()) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                    return o1.getName().compareTo(o2.getName());
                }
            });
            this.loader = loader;
        }

        public void add(Type type) {
            String name = type.getClassName();
            while (name.endsWith("[]")) {
                name = name.substring(0, name.length() - 2);
            }
            try {
                Class<?> primType = BoxedType.getUnboxedType(name);
                if (primType != null) {
                    add(primType);
                } else {
                    add(Class.forName(name, false, loader));
                }
            } catch (Throwable t) {
            }
        }
    }
}
