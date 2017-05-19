package com.bergerkiller.mountiplex;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Type;
import static net.sf.cglib.asm.Opcodes.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.ClassTemplate;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
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

    private static class OwnClassLoader extends ClassLoader {
        
        public OwnClassLoader(ClassLoader base) {
            super(base);
        }
        
        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static Class<?> createClass(String name, byte[] b) {
        return new OwnClassLoader(IntProperty.class.getClassLoader()).defineClass(name, b);
    }

    void createSetter(ClassWriter cw, String className, String propertyName, String type) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;" + type + ")V", null, null);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitFieldInsn(PUTFIELD, className, propertyName, type);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
    }

    void createGetter(ClassWriter cw, String className, String propertyName, String returnType) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;)" + returnType, null, null);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitFieldInsn(GETFIELD, className, propertyName, returnType);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPrimitive() {
        final SpeedTestObject object = new SpeedTestObject();
        final SpeedTestObjectHandle handle = SpeedTestObjectHandle.createHandle(object);
        final IntSetter setter = new IntSetter();


        //TestUtil.printASM(IntSetter.class);


        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        
        String selfname = "MyGeneratedClass";
        cw.visit(V1_7, ACC_PUBLIC, selfname, null, "java/lang/Object", new String[] {Type.getInternalName(IntProperty.class)});
        
        createGetter(cw, Type.getInternalName(SpeedTestObject.class), "i", Type.getDescriptor(int.class));
        createSetter(cw, Type.getInternalName(SpeedTestObject.class), "i", Type.getDescriptor(int.class));
        cw.visitEnd();
        
        Class<IntProperty> setterGenType = (Class<IntProperty>) createClass(selfname, cw.toByteArray());
        ClassTemplate<IntProperty> templ = ClassTemplate.create(setterGenType);
        
        final IntProperty setterGen;
        try {
            setterGen = templ.newInstanceNull();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

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
    }
}
