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
                    result.add(Type.getObjectType(name));
                    if (supername != null) {
                        result.add(Type.getObjectType(supername));
                    }
                    if (interfaces != null) {
                        for (String iif : interfaces) {
                            result.add(Type.getObjectType(iif));
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
                    result.add(Type.getType(desc));
                    return null;
                }

                @Override
                public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
                }

                @Override
                public MethodVisitor visitMethod(int arg0, String name, String desc, String sig, String[] exceptions) {
                    for (Type t : Type.getArgumentTypes(desc)) {
                        result.add(t);
                    }
                    result.add(Type.getReturnType(desc));
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
                            result.add(Type.getObjectType(owner));
                            result.add(Type.getType(desc));
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
                                result.add((Type) arg0);
                            }
                        }

                        @Override
                        public void visitLineNumber(int arg0, Label arg1) {
                        }

                        @Override
                        public void visitLocalVariable(String name, String desc, String sig, Label arg3, Label arg4, int arg5) {
                            result.add(Type.getType(desc));
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {
                        }

                        @Override
                        public void visitMaxs(int arg0, int arg1) {
                        }

                        @Override
                        public void visitMethodInsn(int arg0, String owner, String name, String desc) {
                            result.add(Type.getObjectType(owner));
                            for (Type t : Type.getArgumentTypes(desc)) {
                                result.add(t);
                            }
                            result.add(Type.getReturnType(desc));
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String arg0, int arg1) {
                            result.add(Type.getType(arg0));
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
                            result.add(Type.getObjectType(arg1));
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

    /**
     * Retrieves the source file and line number of a method declaration, and combines it into a StackTraceElement
     * for later exception logging.
     * 
     * @param method to find method details of
     * @return method details stack trace element
     */
    public static StackTraceElement findMethodDetails(java.lang.reflect.Method method) {
        final MethodInfo info = new MethodInfo(method);
        Class<?> declaringClass = method.getDeclaringClass();
        try {
            InputStream classStream = declaringClass.getResourceAsStream(declaringClass.getSimpleName() + ".class");
            if (classStream == null) {
                return info.toTrace();
            }
            ClassReader reader = new ClassReader(classStream);
            reader.accept(new ClassVisitor() {

                @Override
                public void visit(int ver, int acc, String name, String sig, String supername, String[] interfaces) {
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
                    return null;
                }

                @Override
                public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
                }

                @Override
                public MethodVisitor visitMethod(int arg0, String name, String desc, String sig, String[] exceptions) {
                    if (!name.equals(info.method.getName())) {
                        return null;
                    }

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
                        }

                        @Override
                        public void visitLineNumber(int arg0, Label arg1) {
                            if (info.lineNumber == -1) {
                                info.lineNumber = arg0 - 1;
                            }
                        }

                        @Override
                        public void visitLocalVariable(String name, String desc, String sig, Label arg3, Label arg4, int arg5) {
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label arg0, int[] arg1, Label[] arg2) {
                        }

                        @Override
                        public void visitMaxs(int arg0, int arg1) {
                        }

                        @Override
                        public void visitMethodInsn(int arg0, String owner, String name, String desc) {
                        }

                        @Override
                        public void visitMultiANewArrayInsn(String arg0, int arg1) {
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
                    info.source = arg0;
                }
            }, 0);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return info.toTrace();
    }

    private static class MethodInfo {
        public String source = null;
        public int lineNumber = -1;
        public java.lang.reflect.Method method;

        public MethodInfo(java.lang.reflect.Method method) {
            this.method = method;
        }

        public StackTraceElement toTrace() {
            return new StackTraceElement(method.getDeclaringClass().getName(), method.getName(), source, lineNumber);
        }
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
