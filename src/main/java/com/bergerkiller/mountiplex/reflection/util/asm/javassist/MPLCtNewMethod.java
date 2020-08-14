package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMember;
import javassist.CtMethod;
import javassist.compiler.CompileError;
import javassist.compiler.Javac;

/**
 * Implementation of the {@link javassist.CtNewMethod CtNewMethod} utility Class
 * that performs symbol renaming using the Resolver. It does this by
 * using MPLJvstCodeGen instead of JvstCodeGen while compiling.
 */
public class MPLCtNewMethod {

    /**
     * Compiles the given source code and creates a method.
     * The source code must include not only the method body
     * but the whole declaration, for example,
     *
     * <pre>"public Object id(Object obj) { return obj; }"</pre>
     *
     * @param src               the source text. 
     * @param declaring    the class to which the created method is added.
     */
    public static CtMethod make(String src, CtClass declaring)
        throws CannotCompileException
    {
        Javac compiler = MPLJavac.create(declaring);
        try {
            CtMember obj = compiler.compile(src);
            if (obj instanceof CtMethod)
                return (CtMethod)obj;
        }
        catch (CompileError e) {
            throw new CannotCompileException(e);
        }

        throw new CannotCompileException("not a method");
    }
}
