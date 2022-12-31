package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import java.io.ByteArrayInputStream;
import java.io.IOException;

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
     * Creates a new CtClass from the (ASM) bytecode representation. Does not
     * automatically generate a default constructor to initialize added member fields.
     *
     * @param classPool Class Pool used to resolve types
     * @param byteCode Bytecode to load in
     * @return New CtClass
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    public static CtClass makeNewClass(ClassPool classPool, byte[] byteCode)
            throws CannotCompileException, NotFoundException
    {
        try {
            return classPool.makeClass(new ByteArrayInputStream(byteCode));
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IO Exception", e);
        }
    }

    /**
     * Creates a new CtClass that is actually a CtNewClass which automatically generates a default
     * constructor to initialize member fields. This is not done by the normal makeNew, where this
     * is up to the caller to do.
     *
     * @param classPool Class Pool used to resolve types
     * @param name Name of the new class
     * @param byteCode Bytecode to load in
     * @return New CtClass
     * @throws CannotCompileException
     * @throws NotFoundException
     */
    public static CtClass makeNewClassWithDefaultConstructor(ClassPool classPool, String name, byte[] byteCode)
            throws CannotCompileException, NotFoundException
    {
        // Convert the current byte representation into a ByteArray, and then into a CtClass
        //
        // We cannot use the hande makeNewClass() with input stream, because the produced class
        // isn't a CtNewClass instance, and as such doesn't generate the default constructor for us.
        javassist.ByteArrayClassPath selfClassPath = new javassist.ByteArrayClassPath(name, byteCode);
        try {
            classPool.insertClassPath(selfClassPath);

            // While the bytecode is made available on a class path, load the CtClass object
            // This will only remain valid for this sort try-finally block
            CtClass fromBytecode = classPool.get(name);

            // However, this representation doesn't support the mechanism of generating a default constructor
            // For that reason we do need to create a new CtNewClass, and stream all old methods/fields/etc. over
            // This new class will also self-resolve after we remove the temporary class path
            return asNewClass(fromBytecode);
        } finally {
            classPool.removeClassPath(selfClassPath);
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
        {
            CtConstructor initializer = ctClass.getClassInitializer();
            if (initializer != null) {
                newClass.makeClassInitializer().setBody(initializer, null);
            }
        }

        return newClass;
    }
}
