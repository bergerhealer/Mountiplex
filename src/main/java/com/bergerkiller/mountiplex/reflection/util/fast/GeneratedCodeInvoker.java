package com.bergerkiller.mountiplex.reflection.util.fast;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;

/**
 * Generates an invoker that executes a method body
 */
public abstract class GeneratedCodeInvoker<T> implements Invoker<T> {
    private int argCount;

    @Override
    public T invoke(Object instance) {
        throw failArgs(0);
    }

    @Override
    public T invoke(Object instance, Object arg0) {
        throw failArgs(1);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1) {
        throw failArgs(2);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        throw failArgs(3);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        throw failArgs(4);
    }

    @Override
    public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        throw failArgs(5);
    }

    protected static final void verifyArgCount(Object[] args, int expected) {
        if (args.length != expected) {
            throw newInvalidArgs(args.length, expected);
        }
    }

    protected static final RuntimeException newInvalidArgs(int numArgs, int expected) {
        return new IllegalArgumentException("Invalid amount of arguments for method (" +
                numArgs + " given, " + expected + " expected)");
    }

    protected final RuntimeException failArgs(int numArgs) {
        return newInvalidArgs(numArgs, argCount);
    }

    @SuppressWarnings("unchecked")
    public static <T> GeneratedCodeInvoker<T> create(MethodDeclaration declaration) {
        try {
            int argCount = declaration.parameters.parameters.length;
            Class<?> instanceType = declaration.getResolver().getDeclaredClass();
            CtClass origClazz = ClassPool.getDefault().getCtClass(GeneratedCodeInvoker.class.getName());
            CtClass invoker = ClassPool.getDefault().makeClass(origClazz.getName() + ExtendedClassWriter.getNextPostfix(), origClazz);

            // Generate the variable arguments invoke method that delegates to the real method
            String invokeVAArgs = "";
            for (int i = 0; i < argCount; i++) {
                invokeVAArgs += ", args[" + i + "]";
            }
            CtMethod m = CtNewMethod.make(
                         "public Object invokeVA(Object instance, Object[] args) {" +
                         "    if (args.length != " + argCount + ") {" +
                         "        throw failArgs(args.length);" +
                         "    }" +
                         "    return invoke(instance" + invokeVAArgs + ");" +
                         "}",
                         invoker );
            invoker.addMethod(m);

            // Generate the standard invoke method header
            StringBuilder proxyInvokeBody = new StringBuilder();
            StringBuilder invokeBody = new StringBuilder();
            invokeBody.append("public Object invoke(");
            invokeBody.append("Object instance_raw");
            for (ParameterDeclaration param : declaration.parameters.parameters) {
                invokeBody.append(", Object ").append(param.name.real() + "_raw");
            }
            invokeBody.append(") {");

            Class<?> boxedReturnType = BoxedType.getBoxedType(declaration.returnType.type);
            if (boxedReturnType != null) {
                // Generate a proxy method that calls the actual method, and boxes the return value
                // For void methods, we append a return null; to satisfy the method
                proxyInvokeBody = invokeBody;
                if (declaration.returnType.type != void.class) {
                    proxyInvokeBody.append("return ").append(boxedReturnType.getSimpleName());
                    proxyInvokeBody.append(".valueOf(");
                }
                proxyInvokeBody.append("invoke_ub(instance_raw");
                for (ParameterDeclaration param : declaration.parameters.parameters) {
                    proxyInvokeBody.append(", ").append(param.name.real() + "_raw");
                }
                proxyInvokeBody.append(")");
                if (declaration.returnType.type == void.class) {
                    proxyInvokeBody.append("; return null;");
                } else {
                    proxyInvokeBody.append(");");
                }
                proxyInvokeBody.append("}");

                // Reset and use a different invoke method, that has an unboxed return type
                invokeBody = new StringBuilder();
                invokeBody.append("private final ").append(declaration.returnType.type.getName());
                invokeBody.append(" invoke_ub(");
                invokeBody.append("Object instance_raw");
                for (ParameterDeclaration param : declaration.parameters.parameters) {
                    invokeBody.append(", Object ").append(param.name.real() + "_raw");
                }
                invokeBody.append(") {");
            }

            // Cast the instance type to the correct type
            invokeBody.append(instanceType.getName()).append(" instance = ");
            invokeBody.append("(").append(instanceType.getName()).append(") instance_raw;");

            // Generate a section that casts all parameters to the correct type
            for (ParameterDeclaration param : declaration.parameters.parameters) {
                invokeBody.append(param.type.type.getName()).append(' ').append(param.name.real());
                invokeBody.append("=");
                Class<?> boxedType = BoxedType.getBoxedType(param.type.type);
                if (boxedType != null) {
                    // Need to use '((Integer) arg_raw).intValue();' to get the unboxed type
                    invokeBody.append("((").append(boxedType.getSimpleName()).append(") ");
                    invokeBody.append(param.name.real()).append("_raw).");
                    invokeBody.append(param.type.type.getSimpleName()).append("Value();");
                } else {
                    // Simple cast
                    invokeBody.append("(").append(param.type.type.getName()).append(") ");
                    invokeBody.append(param.name.real()).append("_raw;");
                }
            }

            // Add the actual method body
            invokeBody.append(declaration.body);

            // Guarantee a return statement at the end of the function
            if (declaration.returnType.type == void.class) {
                invokeBody.append("return;");
            } else {
                invokeBody.append("return ");
                invokeBody.append(BoxedType.getDefaultValue(declaration.returnType.type));
                invokeBody.append(";");
            }

            // Close the method body
            invokeBody.append("}");

            // Add the method to the class
            m = CtNewMethod.make(invokeBody.toString(), invoker);
            invoker.addMethod(m);

            // Add a proxy invoke body, if set
            if (proxyInvokeBody.length() > 0) {
                invoker.addMethod(CtNewMethod.make(proxyInvokeBody.toString(), invoker));
            }

            GeneratedCodeInvoker<T> result = (GeneratedCodeInvoker<T>) invoker.toClass().newInstance();
            result.argCount = argCount;
            return result;
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
