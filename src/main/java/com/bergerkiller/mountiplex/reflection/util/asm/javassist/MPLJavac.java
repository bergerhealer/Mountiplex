package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.ResolvedClassPool;
import com.bergerkiller.mountiplex.reflection.util.NullInstantiator;

import javassist.ClassPool;
import javassist.CtClass;
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
}
