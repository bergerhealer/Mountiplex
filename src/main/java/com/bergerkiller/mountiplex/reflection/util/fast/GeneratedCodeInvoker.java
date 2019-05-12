package com.bergerkiller.mountiplex.reflection.util.fast;

import java.net.URL;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.declarations.Declaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;

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

    private static final CtClass getClass(Class<?> type) throws NotFoundException {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(type));
        return pool.getCtClass(type.getName());
    }

    private static final CtClass getExtendedClass(ClassPool pool, Class<?> type) throws NotFoundException {
        CtClass origClazz = getClass(type);
        return pool.makeClass(origClazz.getName() + ExtendedClassWriter.getNextPostfix(), origClazz);
    }

    private static CtMethod makeMethodAndLog(String methodBody, CtClass invoker) {
        try {
            return CtNewMethod.make(methodBody, invoker);
        } catch (CannotCompileException ex) {
            MountiplexUtil.LOGGER.severe("Failed to compile method body (" + ex.getReason() + "):");
            MountiplexUtil.LOGGER.severe(methodBody);
            throw MountiplexUtil.uncheckedRethrow(ex.getCause());
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.severe("Failed to generate method body:");
            MountiplexUtil.LOGGER.severe(methodBody);
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static final class ResolvedClassPool extends ClassPool {

        public ResolvedClassPool() {
            super(true);
        }

        @Override
        public URL find(String classname) {
            return super.find(resolveClassName(classname));
        }

        @Override
        public CtClass get(String classname) throws NotFoundException {
            return super.get(resolveClassName(classname));
        }

        @Override
        public CtClass[] get(String[] classnames) throws NotFoundException {
            if (classnames == null) {
                return super.get((String[]) null);
            }
            String[] names = classnames.clone();
            for (int i = 0; i < names.length; i++) {
                names[i] = resolveClassName(names[i]);
            }
            return super.get(names);
        }

        @Override
        public CtClass getCtClass(String classname) throws NotFoundException {
            return super.getCtClass(resolveClassName(classname));
        }

        private String resolveClassName(String classname) {
            if (classname == null) {
                return null;
            }

            // First try to use Class.forName to prevent double-resolving of generated code
            // If the class exists, skip resolveClassPath
            try {
                Class.forName(classname);
                return classname;
            } catch (ClassNotFoundException ex) {
                return Resolver.resolveClassPath(classname);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> GeneratedCodeInvoker<T> create(MethodDeclaration declaration) {
        try {
            int argCount = declaration.parameters.parameters.length;
            Class<?> instanceType = declaration.getResolver().getDeclaredClass();
            Class<?> decClass = declaration.getResolver().getDeclaredClass();
            ClassPool pool = new ResolvedClassPool();
            if (decClass != null) {
                pool.importPackage(decClass.getPackage().getName());
            }
            pool.appendClassPath(new ClassClassPath(GeneratedCodeInvoker.class));
            CtClass invoker = getExtendedClass(pool, GeneratedCodeInvoker.class);

            // Add all the requirements to the class
            for (Declaration bDec : declaration.bodyRequirements) {
                bDec.addAsRequirement(invoker);
            }

            CtMethod m;
            StringBuilder invokeBody = new StringBuilder();
            StringBuilder proxyInvokeBody = new StringBuilder();

            // Special code when > 5 arguments, as none of the invoke methods work here
            // Implement all code under invokeVA directly
            if (argCount > 5) {
                // Main invoke body is invokeVA
                invokeBody.append("public Object invokeVA(Object instance_raw, Object[] args_raw) {");

                // Arg count check
                invokeBody.append("if (args_raw.length != ").append(argCount).append(")");
                invokeBody.append("{ throw failArgs(args_raw.length); }");

                Class<?> boxedReturnType = BoxedType.getBoxedType(declaration.returnType.type);
                if (boxedReturnType != null) {
                    // Generate a proxy method that calls the actual method, and boxes the return value
                    // For void methods, we append a return null; to satisfy the method
                    proxyInvokeBody = invokeBody;
                    if (declaration.returnType.type != void.class) {
                        proxyInvokeBody.append("return ").append(boxedReturnType.getSimpleName());
                        proxyInvokeBody.append(".valueOf(");
                    }
                    proxyInvokeBody.append("invoke_ub(instance_raw, args_raw)");
                    if (declaration.returnType.type == void.class) {
                        proxyInvokeBody.append("; return null;");
                    } else {
                        proxyInvokeBody.append(");");
                    }
                    proxyInvokeBody.append("}");

                    // Reset and use a different invoke method, that has an unboxed return type
                    invokeBody = new StringBuilder();
                    invokeBody.append("private final ").append(declaration.returnType.type.getName());
                    invokeBody.append(" invoke_ub(Object instance_raw, Object[] args_raw) {");
                }
            } else {
                // Generate the variable arguments invoke method that delegates to the real method
                String invokeVAArgs = "";
                for (int i = 0; i < argCount; i++) {
                    invokeVAArgs += ", args[" + i + "]";
                }
                m = makeMethodAndLog(
                             "public Object invokeVA(Object instance, Object[] args) {" +
                             "    if (args.length != " + argCount + ") {" +
                             "        throw failArgs(args.length);" +
                             "    }" +
                             "    return invoke(instance" + invokeVAArgs + ");" +
                             "}",
                             invoker );
                invoker.addMethod(m);

                // Generate the standard invoke method header
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
            }

            // Cast the instance type to the correct type (if available)
            // Does not apply when the method is static, and instance will always be null
            if (!declaration.modifiers.isStatic()) {
                if (instanceType == null || !Resolver.isPublic(instanceType)) {
                    invokeBody.append("Object instance = instance_raw;");
                } else {
                    invokeBody.append(instanceType.getName()).append(" instance = ");
                    invokeBody.append("(").append(instanceType.getName()).append(") instance_raw;");
                }
            }

            // Generate a section that casts all parameters to the correct type
            // When args > 5, use args[index] instead of the real parameter names
            for (int param_idx = 0; param_idx < declaration.parameters.parameters.length; param_idx++) {
                ParameterDeclaration param = declaration.parameters.parameters[param_idx];
                String raw_name;
                if (argCount > 5) {
                    raw_name = "args_raw[" + param_idx + "]";
                } else {
                    raw_name = param.name.real() + "_raw";
                }

                invokeBody.append(ReflectionUtil.getTypeName(param.type.type)).append(' ').append(param.name.real());
                invokeBody.append("=");
                Class<?> boxedType = BoxedType.getBoxedType(param.type.type);
                if (boxedType != null) {
                    // Need to use '((Integer) arg_raw).intValue();' to get the unboxed type
                    invokeBody.append('(').append(ReflectionUtil.getCastString(boxedType)).append(' ');
                    invokeBody.append(raw_name).append(").");
                    invokeBody.append(param.type.type.getSimpleName()).append("Value();");
                } else {
                    // Simple cast
                    invokeBody.append(ReflectionUtil.getCastString(param.type.type)).append(' ');
                    invokeBody.append(raw_name).append(";");
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
            m = makeMethodAndLog(invokeBody.toString(), invoker);
            invoker.addMethod(m);

            // Add a proxy invoke body, if set
            if (proxyInvokeBody.length() > 0) {
                invoker.addMethod(CtNewMethod.make(proxyInvokeBody.toString(), invoker));
            }

            try {
                Class<?> invokerClass = invoker.toClass(GeneratedCodeInvoker.class.getClassLoader(), null);
                GeneratedCodeInvoker<T> result = (GeneratedCodeInvoker<T>) invokerClass.newInstance();
                result.argCount = argCount;
                return result;
            } catch (java.lang.VerifyError ex) {
                System.err.println("Failed to verify generated method: " + declaration.body);
                throw ex;
            }
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }
}
