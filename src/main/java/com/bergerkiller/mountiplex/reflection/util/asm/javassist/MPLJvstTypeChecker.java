package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.compiler.CompileError;
import javassist.compiler.JvstCodeGen;
import javassist.compiler.JvstTypeChecker;
import javassist.compiler.MemberResolver;
import javassist.compiler.NoFieldException;
import javassist.compiler.ast.ASTList;
import javassist.compiler.ast.ASTree;
import javassist.compiler.ast.CallExpr;
import javassist.compiler.ast.Expr;
import javassist.compiler.ast.Keyword;
import javassist.compiler.ast.Symbol;

/**
 * Overrides some methods to better handle the invoking of methods on
 * method-local field names. This avoids accidental class double-resolving
 * problems.
 */
public class MPLJvstTypeChecker extends JvstTypeChecker {

    public MPLJvstTypeChecker(CtClass cc, ClassPool cp, JvstCodeGen gen) {
        super(cc, cp, gen);
    }

    @Override
    public void atCallExpr(CallExpr expr) throws CompileError {
        if (mustSuppressResolvingOfCallExpr(expr)) {
            // Method is being called on a variable (not a static method)
            // This has the unfortunate side-effect of trying to resolve the
            // CtClass object matching the type of field. It does not cache this.
            // This runs the risk of double-resolving the class name, breaking things.

            // To fix this, set a temporary flag to instruct the member resolver to
            // NOT resolve class names. As soon as the trailing atMethodCallCore() is
            // called, revoke this flag. Also do so in a finally, as we don't want
            // this to linger in the case of errors.
            atCallExprSuppressLookup(expr);
        } else {
            // Default
            super.atCallExpr(expr);
        }
    }

    public static boolean mustSuppressResolvingOfCallExpr(CallExpr expr) {
        ASTree method = expr.oprand1();
        if (!(method instanceof Expr) || ((Expr)method).getOperator() != '.') {
            return false;
        }

        // Check for isDotSuper() != null
        ASTree target = ((Expr) method).oprand1();
        if (target instanceof Expr) {
            Expr e = (Expr)target;
            if (e.getOperator() == '.') {
                ASTree right = e.oprand2();
                if (right instanceof Keyword && ((Keyword)right).get() == SUPER && ((Symbol)e.oprand1()).get() != null)
                    return false;
            }
        }

        return true;
    }

    /**
     * Full mirror implementation of atCallExpr, but filtered for an Expr type and for a
     * static class definition. We avoid resolving the class name.
     *
     * @param expr
     * @throws CompileError
     */
    private void atCallExprSuppressLookup(CallExpr expr) throws CompileError {
        Expr e = (Expr) expr.oprand1(); // = method

        String mname = null;
        CtClass targetClass = null;
        ASTList args = (ASTList)expr.oprand2();

        mname = ((Symbol)e.oprand2()).get();
        ASTree target = e.oprand1();

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
            e.setOperator(MEMBER);
            e.setOprand1(new Symbol(MemberResolver.jvmToJavaName(
                                                    className)));
        }

        // Disable resolving during this part!
        MPLMemberResolver resolver = (MPLMemberResolver) this.resolver;
        if (arrayDim > 0)
            targetClass = resolver.lookupClass("java.lang.Object", true);
        else if (exprType == CLASS /* && arrayDim == 0 */)
            targetClass = resolveClassName ? resolver.lookupClassByJvmName(className)
                                           : resolver.lookupClassByJvmNameIgnoreResolver(className);
        else
            throw new CompileError("bad method");

        MemberResolver.Method minfo
                = atMethodCallCore(targetClass, mname, args);
        expr.setMethod(minfo);
    }
}
