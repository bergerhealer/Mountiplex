package com.bergerkiller.mountiplex.reflection.util.asm.javassist;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.compiler.CompileError;
import javassist.compiler.JvstCodeGen;
import javassist.compiler.JvstTypeChecker;
import javassist.compiler.MemberResolver;
import javassist.compiler.ast.ASTList;
import javassist.compiler.ast.ASTree;
import javassist.compiler.ast.CallExpr;
import javassist.compiler.ast.Expr;

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
        ASTree method = expr.oprand1();
        if (method instanceof Expr && ((Expr)method).getOperator() == '.') {
            // Method is being called on a variable (not a static method)
            // This has the unfortunate side-effect of trying to resolve the
            // CtClass object matching the type of field. It does not cache this.
            // This runs the risk of double-resolving the class name, breaking things.

            // To fix this, set a temporary flag to instruct the member resolver to
            // NOT resolve class names. As soon as the trailing atMethodCallCore() is
            // called, revoke this flag. Also do so in a finally, as we don't want
            // this to linger in the case of errors.
            MPLMemberResolver resolver = (MPLMemberResolver) this.resolver;
            try {
                resolver.setIsResolvingMethodCallFieldType(true);
                super.atCallExpr(expr);
            } finally {
                resolver.setIsResolvingMethodCallFieldType(false);
            }
        } else {
            // Default
            super.atCallExpr(expr);
        }
    }

    @Override
    public MemberResolver.Method atMethodCallCore(CtClass targetClass, String mname, ASTList args) throws CompileError {
        // Clear flag
        // See large explanation inside atCallExpr up above ^
        ((MPLMemberResolver) this.resolver).setIsResolvingMethodCallFieldType(false);

        return super.atMethodCallCore(targetClass, mname, args);
    }
}
