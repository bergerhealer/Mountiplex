package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.ResolvedClassPool;

import javassist.CtClass;
import javassist.bytecode.Bytecode;
import javassist.bytecode.MethodInfo;
import javassist.compiler.CodeGen;
import javassist.compiler.CompileError;
import javassist.compiler.JvstCodeGen;
import javassist.compiler.MemberCodeGen;
import javassist.compiler.MemberResolver;
import javassist.compiler.NoFieldException;
import javassist.compiler.TypeChecker;
import javassist.compiler.ast.ASTList;
import javassist.compiler.ast.ASTree;
import javassist.compiler.ast.CallExpr;
import javassist.compiler.ast.Expr;
import javassist.compiler.ast.Keyword;
import javassist.compiler.ast.Symbol;

/**
 * Overrides several methods that are used to resolve field and method names.
 * The protected MemberResolver field of the code gen and the type checker
 * are also replaced.
 */
public final class MPLJvstCodeGen extends JvstCodeGen {
    private static final java.lang.reflect.Method membercodegen_atMethodCallCore2;
    private static final java.lang.reflect.Field codegen_typeChecker;
    private static final java.lang.reflect.Field typechecker_resolver;
    private static final java.lang.reflect.Method argTypesToString;
    static {
        try {
            membercodegen_atMethodCallCore2 = MemberCodeGen.class.getDeclaredMethod("atMethodCallCore2",
                    CtClass.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    int.class,
                    MemberResolver.Method.class);
            membercodegen_atMethodCallCore2.setAccessible(true);
            codegen_typeChecker = CodeGen.class.getDeclaredField("typeChecker");
            codegen_typeChecker.setAccessible(true);
            typechecker_resolver = TypeChecker.class.getDeclaredField("resolver");
            typechecker_resolver.setAccessible(true);
            argTypesToString = TypeChecker.class.getDeclaredMethod("argTypesToString",
                    int[].class, int[].class, String[].class);
            argTypesToString.setAccessible(true);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    public MPLJvstCodeGen(Bytecode b, CtClass cc, ResolvedClassPool cp) {
        super(b, cc, cp);
        this.setTypeChecker(new MPLJvstTypeChecker(cc, cp, this));
        this.resolver = new MPLMemberResolver(cp);

        // Also change MemberResolver of the TypeChecker
        try {
            Object typeChecker = codegen_typeChecker.get(this);
            typechecker_resolver.set(typeChecker, new MPLMemberResolver(cp));
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    @Override
    public void atCallExpr(CallExpr expr) throws CompileError {
        if (MPLJvstTypeChecker.mustSuppressResolvingOfCallExpr(expr)) {
            atCallExprSuppressLookup(expr);
        } else {
            super.atCallExpr(expr);
        }
    }

    /**
     * Full mirror implementation of atCallExpr, but filtered for an Expr type and for a
     * static class definition. We avoid resolving the class name.
     *
     * @param expr
     * @throws CompileError
     */
    private void atCallExprSuppressLookup(CallExpr expr) throws CompileError {
        String mname = null;
        CtClass targetClass = null;
        Expr e = (Expr) expr.oprand1(); // method
        ASTList args = (ASTList)expr.oprand2();
        boolean isStatic = false;
        boolean isSpecial = false;
        int aload0pos = -1;

        MemberResolver.Method cached = expr.getMethod();
        mname = ((Symbol)e.oprand2()).get();
        ASTree target = e.oprand1();
        if (target instanceof Keyword)
            if (((Keyword)target).get() == SUPER)
                isSpecial = true;

        boolean resolveClassName = false;
        try {
            target.accept(this);
        }
        catch (NoFieldException nfe) {
            if (nfe.getExpr() != target)
                throw nfe;

            // it should be a static method.
            resolveClassName = true;
            exprType = CLASS;
            arrayDim = 0;
            className = nfe.getField(); // JVM-internal
            isStatic = true;
        }

        MPLMemberResolver resolver = (MPLMemberResolver) this.resolver;
        if (arrayDim > 0)
            targetClass = resolver.lookupClass("java.lang.Object", true);
        else if (exprType == CLASS /* && arrayDim == 0 */)
            targetClass = resolveClassName ? resolver.lookupClassByJvmName(className)
                                           : resolver.lookupClassByJvmNameIgnoreResolver(className);
        else
            throw new CompileError("bad method");

        atMethodCallCore(targetClass, mname, args, isStatic, isSpecial,
                         aload0pos, cached);
    }

    @Override
    public void atMethodCallCore(CtClass targetClass, String mname,
            ASTList args, boolean isStatic, boolean isSpecial,
            int aload0pos, MemberResolver.Method found) throws CompileError
    {
        // Normal code resumes here
        int nargs = getMethodArgsLength(args);
        int[] types = new int[nargs];
        int[] dims = new int[nargs];
        String[] cnames = new String[nargs];

        if (!isStatic && found != null && found.isStatic()) {
            bytecode.addOpcode(POP);
            isStatic = true;
        }

        @SuppressWarnings("unused")
        int stack = bytecode.getStackDepth();

        // generate code for evaluating arguments.
        atMethodArgs(args, types, dims, cnames);

        if (found == null)
            found = resolver.lookupMethod(targetClass, thisClass, thisMethod, 
                    mname, types, dims, cnames);

        if (found == null) {
            String clazz = targetClass.getName();
            String signature;
            try {
                signature = (String) argTypesToString.invoke(null, types, dims, cnames);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
            String msg;
            if (mname.equals(MethodInfo.nameInit))
                msg = "cannot find constructor " + clazz + signature;
            else
                msg = mname + signature +  " not found in " + clazz;

            throw new CompileError(msg);
        }

        // Change done by us: use found.info.getName() rather than mname
        // This preserves the renaming done by lookupMethod
        // Unfortunately we do lose some performance using reflection,
        // especially because we box a lot of variables :(
        try {
            membercodegen_atMethodCallCore2.invoke(this,
                    targetClass,
                    found.info.getName(),
                    isStatic,
                    isSpecial,
                    aload0pos,
                    found);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
