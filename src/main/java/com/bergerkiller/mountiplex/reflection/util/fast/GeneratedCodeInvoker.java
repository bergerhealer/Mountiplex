package com.bergerkiller.mountiplex.reflection.util.fast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Requirement;
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
public abstract class GeneratedCodeInvoker<T> implements GeneratedInvoker<T> {
    private static final List<ResolvedClassPool> CACHED_CLASS_POOLS = new ArrayList<ResolvedClassPool>();

    private static ResolvedClassPool retrieveClassPool() {
        synchronized (CACHED_CLASS_POOLS) {
            if (CACHED_CLASS_POOLS.isEmpty()) {
                return new ResolvedClassPool();
            } else {
                return CACHED_CLASS_POOLS.remove(CACHED_CLASS_POOLS.size()-1);
            }
        }
    }

    private static void storeClassPool(ResolvedClassPool pool) {
        pool.clearImportedPackages();
        synchronized (CACHED_CLASS_POOLS) {
            CACHED_CLASS_POOLS.add(pool);
        }
    }

    private static final CtClass getExtendedClass(ClassPool pool, Class<?> type, Class<?> interfaceClass) throws NotFoundException {
        CtClass origClazz = pool.getCtClass(Resolver.resolveClassName(type));
        String newClassName = origClazz.getName() + ExtendedClassWriter.getNextPostfix();
        newClassName = ExtendedClassWriter.getAvailableClassName(newClassName);

        CtClass extendedClass = pool.makeClass(newClassName, origClazz);
        if (interfaceClass != null) {
            extendedClass.addInterface(pool.makeInterface(Resolver.resolveClassName(interfaceClass)));
        }
        return extendedClass;
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

    private static String getAccessibleTypeName(Class<?> type) {
        if (Resolver.isPublic(type)) {
            return ReflectionUtil.getTypeName(type);
        } else {
            return "Object";
        }
    }

    private static boolean mustCastType(Class<?> type) {
        return type != null && type != Object.class && Resolver.isPublic(type);
    }

    private static void buildInvokeBody(MethodDeclaration declaration, StringBuilder methodBody, boolean isInvokeVA) {
        int argCount = declaration.parameters.parameters.length;

        // Cast the instance
        Class<?> instanceType = declaration.modifiers.isStatic() ? null : declaration.getDeclaringClass();
        if (mustCastType(instanceType)) {
            methodBody.append(ReflectionUtil.getTypeName(instanceType))
            .append(" instance=").append(ReflectionUtil.getCastString(instanceType)).append("instance_raw;\n");
        }

        // Unpack all the parameters
        String arg_prefix = isInvokeVA ? "args_raw[" : "arg_raw_num_";
        String arg_postfix = isInvokeVA ? "]" : "";
        for (int i = 0; i < argCount; i++) {
            ParameterDeclaration param = declaration.parameters.parameters[i];
            if (!mustCastType(param.type.type)) {
                continue; // skip, there is no cast needed
            }

            methodBody.append(ReflectionUtil.getTypeName(param.type.type))
                      .append(' ')
                      .append(param.name.real())
                      .append('=');
            Class<?> boxedType = BoxedType.getBoxedType(param.type.type);
            if (boxedType != null) {
                // Requires unboxing
                methodBody.append('(').append(ReflectionUtil.getCastString(boxedType))
                          .append(arg_prefix).append(i).append(arg_postfix).append(").")
                          .append(param.type.type.getSimpleName()).append("Value();\n");
            } else {
                // Simple cast, is omitted if the parameter type is already Object
                methodBody.append(ReflectionUtil.getCastString(param.type.type))
                          .append(arg_prefix).append(i).append(arg_postfix).append(";\n");
            }
        }

        // Add 'return ' if a return value is specified
        boolean hasReturnType = (declaration.returnType.type != void.class);
        Class<?> boxedReturnType = null;
        if (hasReturnType) {
            methodBody.append("return ");
            boxedReturnType = BoxedType.getBoxedType(declaration.returnType.type);
        }

        // Need to wrap the entire call in a valueOf if the return value is a primitive type
        if (boxedReturnType != null) {
            methodBody.append(boxedReturnType.getSimpleName()).append(".valueOf(");
        }

        // Call the method with the unpacked parameters
        methodBody.append("this.").append(declaration.name.real()).append('(');
        if (instanceType != null) {
            if (mustCastType(instanceType)) {
                methodBody.append("instance");
            } else {
                methodBody.append("instance_raw");
            }
            if (argCount > 0) {
                methodBody.append(',');
            }
        }
        for (int i = 0; i < argCount; i++) {
            ParameterDeclaration param = declaration.parameters.parameters[i];
            if (mustCastType(param.type.type)) {
                methodBody.append(param.name.real());
            } else {
                methodBody.append(arg_prefix).append(i).append(arg_postfix);
            }
            if (i < (argCount-1)) {
                methodBody.append(",");
            }
        }
        methodBody.append(')');

        // Close valueOf() if needed
        if (boxedReturnType != null) {
            methodBody.append(')');
        }

        // Close method call
        methodBody.append(";\n");

        // Add another return null; if method is void
        if (!hasReturnType) {
            methodBody.append("return null;\n");
        }

        // Close the method body
        methodBody.append('}');
    }

    private static final class ResolvedClassPool extends ClassPool {

        public ResolvedClassPool() {
            super(true);
            appendClassPath(new ClassClassPath(GeneratedCodeInvoker.class));
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

        @Override
        public URL find(String classname) {
            // First try to find the classname without further resolving.
            // If it exists, skip resolveClassPath
            URL url;
            if (classname == null) {
                return null;
            } else if ((url = super.find(classname)) != null) {
                //System.out.println("[MPL] FIND " + classname + " UNCHANGED_URI");
                return url;
            } else {
                return super.find(Resolver.resolveClassPath(classname));
            }
        }

        private String resolveClassName(String classname) {
            // First try to find the classname without further resolving.
            // If it exists, skip resolveClassPath
            if (classname == null) {
                return null;
            } else if (super.find(classname) != null) {
                //System.out.println("[MPL] FIND " + classname + " UNCHANGED");
                return classname;
            } else {
                String str = Resolver.resolveClassPath(classname);
                //System.out.println("[MPL] FIND " + classname + " -> " + str);
                return str;
            }
        }
    }

    public static <T> GeneratedCodeInvoker<T> create(MethodDeclaration declaration) {
        return create(declaration, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> GeneratedCodeInvoker<T> create(MethodDeclaration declaration, Class<?> interfaceClass) {
        if (!declaration.isResolved()) {
            throw new IllegalArgumentException("Declaration not resolved: " + declaration.toString());
        }

        ResolvedClassPool pool = retrieveClassPool();
        try {
            int argCount = declaration.parameters.parameters.length;
            Class<?> instanceType = declaration.getDeclaringClass();
            Class<?> decClass = declaration.getResolver().getDeclaredClass();
            if (decClass != null) {
                Package p = decClass.getPackage();
                if (p != null) {
                    pool.importPackage(p.getName());
                } else {
                    // Decode the package path from class name ourselves
                    // This might fail :(
                    String package_path = Resolver.resolveClassName(decClass);
                    int lastDot;
                    while ((lastDot = package_path.lastIndexOf('.')) != -1) {
                        String lastPart = package_path.substring(lastDot+1);
                        if (lastPart.isEmpty() || !Character.isUpperCase(lastPart.charAt(0))) {
                            break;
                        } else {
                            package_path = package_path.substring(0, lastDot);
                        }
                    }
                    pool.importPackage(package_path);
                }
            }
            CtClass invoker = getExtendedClass(pool, GeneratedCodeInvoker.class, interfaceClass);
            CtMethod m;
            StringBuilder methodBody = new StringBuilder();

            // Add all the requirements to the class
            for (Requirement req : declaration.bodyRequirements) {
                req.declaration.addAsRequirement(invoker, req.name);
            }

            // If interfaceClass is null then the getInterface() is not implemented, so implement it here
            // In this default implementation we simply return the class we are generating here
            if (interfaceClass == null) {
                m = makeMethodAndLog(
                        "public Class getInterface() {" +
                        "    return " + invoker.getName() + ".class;" +
                        "}",
                        invoker );
                invoker.addMethod(m);
            }

            // Add the exact method that the method declaration exposes
            // This implements the method declared in the interfaceClass
            {
                methodBody.setLength(0);

                // Add the method signature information
                methodBody.append("public ")
                          .append(getAccessibleTypeName(declaration.returnType.type))
                          .append(" ").append(declaration.name.real()).append("(");
                if (!declaration.modifiers.isStatic()) {
                    methodBody.append(getAccessibleTypeName(instanceType))
                              .append(" instance");
                    if (argCount > 0) {
                        methodBody.append(',');
                    }
                }
                for (int i = 0; i < argCount; i++) {
                    ParameterDeclaration param = declaration.parameters.parameters[i];
                    methodBody.append(getAccessibleTypeName(param.type.type))
                              .append(' ')
                              .append(param.name.real());
                    if (i < (argCount-1)) {
                        methodBody.append(',');
                    }
                }
                methodBody.append(") {\n");

                // Add the actual method body
                methodBody.append(declaration.body);

                // Guarantee a return statement at the end of the function
                if (declaration.returnType.type == void.class) {
                    methodBody.append("return;");
                } else {
                    methodBody.append("return ")
                              .append(BoxedType.getDefaultValue(declaration.returnType.type))
                              .append(';');
                }

                // Close the method body
                methodBody.append('}');

                // Add method to the invoker
                invoker.addMethod(makeMethodAndLog(methodBody.toString(), invoker));
            }

            // Implement invokeVA to check arg count and unpack the parameters, then call the method we added earlier
            {
                methodBody.setLength(0);

                // Add invokeVA method signature
                methodBody.append("public Object invokeVA(Object instance_raw, Object[] args_raw) {\n");

                // Arg count check
                methodBody.append("if (args_raw.length!=").append(argCount).append(")")
                          .append("{throw new com.bergerkiller.mountiplex.reflection.util.fast.InvalidArgumentCountException(")
                          .append("\"method\",args_raw.length,").append(argCount).append(");}\n");

                // Complete the rest of the invoke body and add the method
                buildInvokeBody(declaration, methodBody, true);
                invoker.addMethod(makeMethodAndLog(methodBody.toString(), invoker));
            }

            // Implement invoke(instance, argn) for improved performance (avoids Object[] allocation)
            if (argCount <= 5) {
                methodBody.setLength(0);

                // Build the invoke method header
                methodBody.append("public Object invoke(Object instance_raw");
                for (int i = 0; i < argCount; i++) {
                    methodBody.append(",Object arg_raw_num_").append(i);
                }
                methodBody.append("){\n");

                // Complete the rest of the invoke body and add the method
                buildInvokeBody(declaration, methodBody, false);
                invoker.addMethod(makeMethodAndLog(methodBody.toString(), invoker));
            }

            try {
                ClassLoader generatorLoader = ExtendedClassWriter.getGeneratorClassLoader(GeneratedCodeInvoker.class.getClassLoader());
                Class<?> invokerClass = invoker.toClass(generatorLoader, null);
                return (GeneratedCodeInvoker<T>) invokerClass.newInstance();
            } catch (java.lang.VerifyError ex) {
                System.err.println("Failed to verify generated method: " + declaration.body);
                throw ex;
            }
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        } finally {
            storeClassPool(pool);
        }
    }
}
