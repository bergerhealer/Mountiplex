package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.IgnoresRemapping;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimally swaps out one or more fields in a record class by calling
 * the constructor and creating a new instance. Existing field values are
 * copied over except for the ones requested.
 */
public abstract class RecordClassFieldChanger<T> implements GeneratedExactSignatureInvoker<T>, IgnoresRemapping {

    /**
     * Generates a new RecordClassFieldChanger
     *
     * @param type Record Class whose fields are changed
     * @param nameAlias Name of the static change method that is generated as well
     * @param recordNamesToSet List of record class field names to change
     * @return RecordClassFieldChanger
     * @param <T> Record Class type
     */
    public static <T> RecordClassFieldChanger<T> create(Class<?> type, String nameAlias, List<String> recordNamesToSet) {
        // Identify all record field (and method()) names that the record class type contains
        // For JDK16+ there are record classes so we can use the getRecordComponents() / RecordComponent API for them
        // For non-record classes we have a fallback that simply uses all fields declared inside, and assumes the
        // same are declared in the constructor. This is mostly for testing. This library is supposed to work
        // on JDK8 so this stuff gets kind of hairy otherwise.
        List<RecordComponent> records;
        List<RecordComponent> recordsToSet;
        {
            records = null;
            try {
                Class<?> recordComponentType = Class.forName("java.lang.reflect.RecordComponent");
                Object[] recordComponents = (Object[]) type.getMethod("getRecordComponents").invoke(type);
                if (recordComponents != null) {
                    Method getAccessorMethod = recordComponentType.getMethod("getAccessor");
                    Method getTypeMethod = recordComponentType.getMethod("getType");
                    records = new ArrayList<>(recordComponents.length);
                    for (Object comp : recordComponents) {
                        Method r_acc = (Method) getAccessorMethod.invoke(comp);
                        Class<?> r_type = (Class<?>) getTypeMethod.invoke(comp);
                        records.add(new RecordComponent(MPLType.getName(r_acc), r_type));
                    }
                }
            } catch (Throwable t) { /* ignore */ }
            if (records == null) {
                records = new ArrayList<>();
                for (Field f : type.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    records.add(new RecordComponent(f.getName(), f.getType()));
                }
            }

            // Verify all record names we specified are set in some way
            recordsToSet = new ArrayList<>(recordNamesToSet.size());
            int position = 1;
            for (String name : recordNamesToSet) {
                boolean found = false;
                for (RecordComponent comp : records) {
                    if (comp.name.equals(name)) {
                        comp.changed = true;
                        comp.argPosition = position;
                        position += org.objectweb.asm.Type.getType(comp.type).getSize();
                        found = true;
                        recordsToSet.add(comp);
                        break;
                    }
                }
                if (!found) {
                    throw new IllegalArgumentException("Record class " + type.getName() + " does not contain record with name '" + name + "'!");
                }
            }
        }

        ExtendedClassWriter<RecordClassFieldChanger<T>> classWriter = ExtendedClassWriter.builder(RecordClassFieldChanger.class)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setSingleton(true)
                .build();

        MethodVisitor methodVisitor;
        String typeInternalName = MPLType.getInternalName(type);
        String changeMethodDescriptor;
        {
            StringBuilder desc = new StringBuilder();
            desc.append('(');
            desc.append(MPLType.getDescriptor(type));
            for (RecordComponent comp : recordsToSet) {
                desc.append(MPLType.getDescriptor(comp.type));
            }
            desc.append(')');
            desc.append(MPLType.getDescriptor(type));
            changeMethodDescriptor = desc.toString();
        }

        // Generate a static change() method that can be called directly, and is called from the invoke/invokeVA methods
        {
            StringBuilder ctorDesc = new StringBuilder();
            ctorDesc.append('(');
            for (RecordComponent comp : records) {
                ctorDesc.append(MPLType.getDescriptor(comp.type));
            }
            ctorDesc.append(")V");

            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, nameAlias, changeMethodDescriptor, null, null);
            methodVisitor.visitCode();
            methodVisitor.visitTypeInsn(NEW, typeInternalName);
            methodVisitor.visitInsn(DUP);

            for (RecordComponent comp : records) {
                if (comp.changed) {
                    methodVisitor.visitVarInsn(org.objectweb.asm.Type.getType(comp.type).getOpcode(ILOAD), comp.argPosition);
                } else {
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, typeInternalName, comp.name,
                            "()" + MPLType.getDescriptor(comp.type), false);
                }
            }

            methodVisitor.visitMethodInsn(INVOKESPECIAL, typeInternalName, "<init>", ctorDesc.toString(), false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        // Generate an invoke() method, providing the number of records to change is less than or equal to 5
        if (recordsToSet.size() <= 5) {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "invoke", GeneratedInvoker.buildInvokeDescriptor(recordsToSet.size()), null, null);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, typeInternalName);

            int position = 2;
            for (RecordComponent comp : recordsToSet) {
                methodVisitor.visitVarInsn(ALOAD, position++);
                ExtendedClassWriter.visitUnboxObjectVariable(methodVisitor, comp.type);
            }

            methodVisitor.visitMethodInsn(INVOKESTATIC, classWriter.getInternalName(), nameAlias, changeMethodDescriptor, false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        // Generate an invokeVA() method
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_VARARGS, "invokeVA", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            methodVisitor.visitCode();
            GeneratedInvoker.visitInvokeVAArgCountCheck(methodVisitor, recordsToSet.size());

            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitTypeInsn(CHECKCAST, typeInternalName);

            int index = 0;
            for (RecordComponent comp : recordsToSet) {
                methodVisitor.visitVarInsn(ALOAD, 2);
                ExtendedClassWriter.visitPushInt(methodVisitor, index++);
                methodVisitor.visitInsn(AALOAD);
                ExtendedClassWriter.visitUnboxObjectVariable(methodVisitor, comp.type);
            }

            methodVisitor.visitMethodInsn(INVOKESTATIC, classWriter.getInternalName(), nameAlias, changeMethodDescriptor, false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        return classWriter.generateInstance();
    }

    @Override
    public String getInvokerClassInternalName() {
        return MPLType.getInternalName(getClass());
    }

    @Override
    public String getInvokerClassTypeDescriptor() {
        return MPLType.getDescriptor(getClass());
    }

    private static final class RecordComponent {
        public final String name;
        public final Class<?> type;
        public boolean changed = false;
        public int argPosition = -1;

        public RecordComponent(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }
    }
}
