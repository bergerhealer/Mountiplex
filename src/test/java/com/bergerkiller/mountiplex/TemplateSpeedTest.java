package com.bergerkiller.mountiplex;

import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;
import static net.sf.cglib.asm.Opcodes.*;
import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.types.IntProperty;
import com.bergerkiller.mountiplex.types.SpeedTestObject;
import com.bergerkiller.mountiplex.types.SpeedTestObjectHandle;

public class TemplateSpeedTest {

    static {
        Resolver.registerClassDeclarationResolver(new ClassDeclarationResolver() {
            @Override
            public ClassDeclaration resolveClassDeclaration(Class<?> classType) {
                if (classType.equals(SpeedTestObject.class)) {
                    String template = "package com.bergerkiller.mountiplex.types;\n" +
                                      "\n" +
                                      "public class SpeedTestObject {\n" +
                                      "    private int i;\n" +
                                      "    private double d;\n" +
                                      "    private String s;\n" +
                                      "    public final void setS(String value);\n" +
                                      "    public final String getS();\n" +
                                      "}\n";

                    return SourceDeclaration.parse(template).classes[0];
                }
                return null;
            }
        });
    }

    private void measure(String testName, Runnable runnable) {
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

    public static class IntSetter {
        public void set(Object instance, int value) {
            ((SpeedTestObject) instance).i = value;
        }

        public int get(Object instance) {
            return ((SpeedTestObject) instance).i;
        }
    }

    public static abstract class GenSetterBase {
        public final java.lang.reflect.Field field;

        public GenSetterBase(java.lang.reflect.Field field) {
            this.field = field;
        }

    }

    public static class GenSetter extends GenSetterBase implements IntProperty {

        public GenSetter(java.lang.reflect.Field field) {
            super(field);
        }

        @Override
        public void set(Object instance, int value) {
        }

        @Override
        public int get(Object instance) {
            return 0;
        }

        public void doStuff() {
            System.out.println("STUFF");
        }
    }

    @Test
    public void testPrimitive() {
        final SpeedTestObject object = new SpeedTestObject();
        final SpeedTestObjectHandle handle = SpeedTestObjectHandle.createHandle(object);
        final IntSetter setter = new IntSetter();

        //TestUtil.printASM(BlaBla.class);

        final String fieldType = Type.getDescriptor(int.class);
        final String className = Type.getInternalName(SpeedTestObject.class);
        final String propertyName = "i";

        ExtendedClassWriter<GenSetter> cw = new ExtendedClassWriter<GenSetter>(0, GenSetter.class);

        MethodVisitor mv;

        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/Field;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(GenSetter.class), "<init>", "(" + Type.getDescriptor(java.lang.reflect.Field.class) + ")V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;)" + fieldType, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitFieldInsn(GETFIELD, className, propertyName, fieldType);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;" + fieldType + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitFieldInsn(PUTFIELD, className, propertyName, fieldType);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 3);
        mv.visitEnd();

        final GenSetter setterGen = cw.generateInstance(new Class<?>[] {java.lang.reflect.Field.class}, new Object[] { null });
        
        object.i = 200;
        assertEquals(200, setterGen.get(object));
        
        setterGen.set(object, 300);
        assertEquals(300, object.i);
        
        
        setterGen.doStuff();

        measure("direct field access", new Runnable() {
            @Override
            public void run() {
                object.i++;
            }
        });
        measure("property field access", new Runnable() {
            @Override
            public void run() {
                object.setI(object.getI() + 1);
            }
        });
        measure("handle field access", new Runnable() {
            @Override
            public void run() {
                handle.setI(handle.getI() + 1);
            }
        });
        measure("class field access", new Runnable() {
            @Override
            public void run() {
                SpeedTestObjectHandle.T.i.setInteger(object, SpeedTestObjectHandle.T.i.getInteger(object) + 1);
            }
        });
        measure("compiled property field access", new Runnable() {
            @Override
            public void run() {
                setter.set(object, setter.get(object) + 1);
            }
        });
        measure("generated property field access", new Runnable() {
            @Override
            public void run() {
                setterGen.set(object, setterGen.get(object) + 1);
            }
        });

        SpeedTestObjectHandle.T.d.setDouble(object, 2.0);
        assertEquals(2.0, SpeedTestObjectHandle.T.d.getDouble(object), 0.001);

        SpeedTestObjectHandle.T.s.set(object, "test");
        assertEquals("test", SpeedTestObjectHandle.T.s.get(object));
    }
}
