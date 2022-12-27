package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.ResolvedClassPool;
import com.bergerkiller.mountiplex.reflection.util.NullInstantiator;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Bytecode;
import javassist.compiler.Javac;
import javassist.compiler.SymbolTable;

/**
 * Creates instances of Javac with our modifications made. This is more efficient than altering
 * an existing Javac instance.
 */
public class MPLJavac {
    private static final NullInstantiator<Javac> javac_instantiator = NullInstantiator.of(Javac.class);
    private static final java.lang.reflect.Field javac_gen;
    private static final java.lang.reflect.Field javac_stable;
    private static final java.lang.reflect.Field javac_bytecode;
    static {
        try {
            javac_gen = Javac.class.getDeclaredField("gen");
            javac_gen.setAccessible(true);
            javac_stable = Javac.class.getDeclaredField("stable");
            javac_stable.setAccessible(true);
            javac_bytecode = Javac.class.getDeclaredField("bytecode");
            javac_bytecode.setAccessible(true);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Creates a new Javac instance, with Mountiplex modifications
     * to support field and method name remapping.
     * 
     * @param thisClass
     * @return Javac
     */
    public static Javac create(CtClass thisClass) {
        ClassPool classPool = thisClass.getClassPool();
        if (!(classPool instanceof ResolvedClassPool)) {
            throw new IllegalArgumentException("Class " + thisClass.getName() + " does not use a resolved class pool");
        }

        try {
            Javac compiler = javac_instantiator.create();
            Bytecode bytecode = new Bytecode(thisClass.getClassFile2().getConstPool(), 0, 0);
            javac_gen.set(compiler, new MPLJvstCodeGen(bytecode, thisClass, (ResolvedClassPool) classPool));
            javac_stable.set(compiler, new SymbolTable());
            javac_bytecode.set(compiler, bytecode);
            return compiler;
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Converts a loaded CtClass object into a CtNewClass object. This type of class can self-resolve
     * in class name lookup and automatically generates default constructors.
     *
     * @param ctClass
     * @return ctNewClass
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    public static CtClass asNewClass(CtClass ctClass) throws CannotCompileException, NotFoundException {
        // However, this representation doesn't support the mechanism of generating a default constructor
        // For that reason we do need to create a new CtNewClass, and stream all old methods/fields/etc. over
        CtClass newClass = ctClass.getClassPool().makeClass(ctClass.getName(), ctClass.getSuperclass());
        for (CtClass ctInterface : ctClass.getInterfaces()) {
            newClass.addInterface(ctInterface);
        }
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            newClass.addMethod(new CtMethod(method, newClass, null));
        }
        for (CtField field : ctClass.getDeclaredFields()) {
            newClass.addField(new CtField(field, newClass));
        }
        for (CtConstructor constr : ctClass.getDeclaredConstructors()) {
            newClass.addConstructor(new CtConstructor(constr, newClass, null));
        }

        return newClass;
    }
}
