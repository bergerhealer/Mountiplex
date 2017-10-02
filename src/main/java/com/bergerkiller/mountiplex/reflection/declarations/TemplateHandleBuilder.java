package com.bergerkiller.mountiplex.reflection.declarations;

import static net.sf.cglib.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.TemplateSpeedTest.GenSetter;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;

import net.sf.cglib.asm.FieldVisitor;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Opcodes;
import net.sf.cglib.asm.Type;

/**
 * Reads the abstract class information of a Template Handle type and generates an appropriate
 * implementation of the abstract methods.
 */
public class TemplateHandleBuilder<H> {
    private final Class<H> handleType;
    private final ClassDeclaration classDec;
    private Class<? extends H> handleImplType;
    private Constructor<H> constructor;

    public TemplateHandleBuilder(Class<H> handleType, ClassDeclaration classDeclaration) {
        this.handleType = handleType;
        this.classDec = classDeclaration;
    }

    public Class<? extends H> getImplType() {
        return this.handleImplType;
    }

    public H create(Object instance) {
        try {
            return constructor.newInstance(instance);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    public void build() {
        String typeName = Type.getDescriptor(this.classDec.type.type);

        ExtendedClassWriter<H> cw = new ExtendedClassWriter<H>(0, this.handleType);
        MethodVisitor mv;
        FieldVisitor fv;

        fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "instance", typeName, null, null);
        fv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + typeName + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(this.handleType), "<init>", "()V");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, cw.getInternalName(), "instance", typeName);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
        
        mv = cw.visitMethod(ACC_PUBLIC, "getRaw", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "instance", typeName);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        this.handleImplType = cw.generate();

        try {
            this.constructor = (Constructor<H>) this.handleImplType.getConstructor(this.classDec.type.type);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
