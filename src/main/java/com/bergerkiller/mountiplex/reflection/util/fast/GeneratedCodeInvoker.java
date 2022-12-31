package com.bergerkiller.mountiplex.reflection.util.fast;

import static org.objectweb.asm.Opcodes.*;

import java.util.logging.Level;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.Requirement;
import com.bergerkiller.mountiplex.reflection.resolver.ResolvedClassPool;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.IgnoresRemapping;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLCtNewMethod;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Generates an invoker that executes a method body
 */
public abstract class GeneratedCodeInvoker<T> implements GeneratedExactSignatureInvoker<T>, IgnoresRemapping {

    @Override
    public String getInvokerClassInternalName() {
        return MPLType.getInternalName(getClass());
    }

    @Override
    public String getInvokerClassTypeDescriptor() {
        return MPLType.getDescriptor(getClass());
    }

    public static <T> GeneratedCodeInvoker<T> create(MethodDeclaration declaration) {
        ExtendedClassWriter<GeneratedCodeInvoker<T>> writer = ExtendedClassWriter.builder(GeneratedCodeInvoker.class)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setClassLoader(declaration.getResolver().getClassLoader())
                .setSingleton(true)
                .build();
        return generate(writer, declaration);
    }

    public static <T> ExtendedClassWriter.Deferred<GeneratedCodeInvoker<T>> createDefer(MethodDeclaration declaration) {
        return ExtendedClassWriter.<GeneratedCodeInvoker<T>>builder(GeneratedCodeInvoker.class)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setClassLoader(declaration.getResolver().getClassLoader())
                .setSingleton(true)
                .defer(writer -> generate(writer, declaration));
    }

    private static <T> GeneratedCodeInvoker<T> generate(ExtendedClassWriter<GeneratedCodeInvoker<T>> writer, MethodDeclaration declaration) {
        if (!declaration.isResolved()) {
            throw new IllegalArgumentException("Declaration not resolved: " + declaration.toString());
        }

        // Make sure warnings/errors are handled before we try to compile anything
        declaration.checkTemplateErrors();

        try (ResolvedClassPool pool = ResolvedClassPool.create()) {
            int argCount = declaration.parameters.parameters.length;

            // ASM: Add an invokeVA method which calls the soon-to-be-generated method with the cast
            asmAddInvokeMethod(writer, declaration, true);

            // ASM: Also add the invoke() method if less than 5 arguments
            if (argCount <= 5) {
                asmAddInvokeMethod(writer, declaration, false);
            }

            // ASM: Before we write the CtClass out, let declarations add requirements using ASM if they can
            // Things that require Javassist are delayed until getCtClass() is called
            for (Requirement req : declaration.bodyRequirements) {
                req.declaration.addAsRequirement(writer, req, req.name);
            }

            // Use the resolver to add needed imports
            {
                ClassResolver classResolver = declaration.getResolver();
                if (classResolver.hasPackage()) {
                    // Import using the predefined #package
                    pool.importPackage(classResolver.getPackage());
                } else if (classResolver.getDeclaredClass() != null) {
                    // Decode the package path from class name ourselves
                    // This might fail :(
                    String class_path = declaration.getResolver().getDeclaredClassName();
                    String package_path = MountiplexUtil.getPackagePathFromClassPath(class_path);
                    if (!package_path.isEmpty()) {
                        pool.importPackage(package_path);
                    }
                }
                for (String importName : classResolver.getImports()) {
                    if (importName.endsWith(".*")) {
                        String packagePath = importName.substring(0, importName.length()-2);
                        if (!packagePath.contains("*")) {
                            pool.importPackage(packagePath);
                        }
                    } else {
                        pool.importPackage(importName);
                    }
                }
            }

            CtClass invoker = writer.getCtClass(pool);
            StringBuilder methodBody = new StringBuilder();

            // Add the exact method that the method declaration exposes
            // This implements the method declared in the interfaceClass
            {
                methodBody.setLength(0);

                // Add the method signature information
                methodBody.append("public ")
                          .append(ReflectionUtil.getAccessibleTypeName(declaration.returnType.type))
                          .append(" ").append(declaration.name.real()).append("(");
                if (!declaration.modifiers.isStatic()) {
                    methodBody.append(ReflectionUtil.getAccessibleTypeName(declaration.getDeclaringClass()))
                              .append(" instance");
                    if (argCount > 0) {
                        methodBody.append(',');
                    }
                }
                for (int i = 0; i < argCount; i++) {
                    ParameterDeclaration param = declaration.parameters.parameters[i];
                    methodBody.append(ReflectionUtil.getAccessibleTypeName(param.type.type))
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

            try {
                return (GeneratedCodeInvoker<T>) writer.generateInstance();
            } catch (java.lang.VerifyError ex) {
                MountiplexUtil.LOGGER.severe("Failed to verify generated method: " + declaration.body);
                throw ex;
            }
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static CtMethod makeMethodAndLog(String methodBody, CtClass invoker) {
        try {
            return MPLCtNewMethod.make(methodBody, invoker);
        } catch (CannotCompileException ex) {
            MountiplexUtil.LOGGER.severe("Failed to compile method body (" + ex.getReason() + "):");
            MountiplexUtil.LOGGER.severe(methodBody);
            throw MountiplexUtil.uncheckedRethrow(ex.getCause());
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to generate method body:", t);
            MountiplexUtil.LOGGER.severe(methodBody);
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static boolean mustCastType(Class<?> type) {
        return type != null && type != Object.class && Resolver.isPublic(type);
    }

    private static <T> void asmAddInvokeMethod(ExtendedClassWriter<GeneratedCodeInvoker<T>> writer, MethodDeclaration declaration, boolean invokeVA) {
        final int argCount = declaration.parameters.parameters.length;
        final int stackStart = invokeVA ? 3 : (2 + argCount);
        final Class<?> instanceType = declaration.modifiers.isStatic() ? null : declaration.getDeclaringClass();
        final Class<?> returnType = declaration.returnType.type;

        MethodVisitor mv;
        if (invokeVA) {
            mv = writer.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "invokeVA",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();

            // Check correct number of arguments
            GeneratedInvoker.visitInvokeVAArgCountCheck(mv, argCount);
        } else {
            StringBuilder desc = new StringBuilder();
            desc.append("(Ljava/lang/Object;");
            for (int n = 0; n < argCount; n++) {
                desc.append("Ljava/lang/Object;");
            }
            desc.append(")Ljava/lang/Object;");
            mv = writer.visitMethod(ACC_PUBLIC + ACC_FINAL, "invoke", desc.toString(), null, null);
        }

        // Cast all input parameters
        // Start storing at 3 (0=this, 1=instance, 2=args)
        for (int i = 0, storeIndex = stackStart; i < argCount; i++) {
            ParameterDeclaration param = declaration.parameters.parameters[i];
            if (!mustCastType(param.type.type)) {
                continue; // skip, there is no cast needed
            }

            if (invokeVA) {
                // Load args[] and load array element [i]
                mv.visitVarInsn(ALOAD, 2);
                ExtendedClassWriter.visitPushInt(mv, i);
                mv.visitInsn(AALOAD);
            } else {
                // Load argument i
                mv.visitVarInsn(ALOAD, 2 + i);
            }

            // Cast/unbox it
            ExtendedClassWriter.visitUnboxVariable(mv, param.type.type);

            // Store on stack
            storeIndex = MPLType.visitVarIStore(mv, storeIndex, param.type.type);
        }

        // Load everything from stack again and invoke the method
        StringBuilder invokeDescriptor = new StringBuilder();
        {
            mv.visitVarInsn(ALOAD, 0); // this

            invokeDescriptor.append('(');
            if (instanceType != null) {
                mv.visitVarInsn(ALOAD, 1);
                if (mustCastType(instanceType)) {
                    ExtendedClassWriter.visitUnboxVariable(mv, instanceType);
                    invokeDescriptor.append(MPLType.getDescriptor(instanceType));
                } else {
                    invokeDescriptor.append("Ljava/lang/Object;");
                }
            }

            for (int i = 0, storeIndex = stackStart; i < argCount; i++) {
                ParameterDeclaration param = declaration.parameters.parameters[i];
                if (mustCastType(param.type.type)) {
                    // Load from what we stored on stack previously
                    invokeDescriptor.append(MPLType.getDescriptor(param.type.type));
                    storeIndex = MPLType.visitVarILoad(mv, storeIndex, param.type.type);
                } else if (invokeVA) {
                    // Load from args array
                    invokeDescriptor.append("Ljava/lang/Object;");
                    mv.visitVarInsn(ALOAD, 2);
                    ExtendedClassWriter.visitPushInt(mv, i);
                    mv.visitInsn(AALOAD);
                } else {
                    // Load from arg
                    invokeDescriptor.append("Ljava/lang/Object;");
                    mv.visitVarInsn(ALOAD, 2 + i);
                }
            }
            invokeDescriptor.append(')');

            if (mustCastType(returnType)) {
                invokeDescriptor.append(MPLType.getDescriptor(returnType));
            } else {
                invokeDescriptor.append("Ljava/lang/Object;");
            }
        }

        // With this, instance (optional) and all arguments on the stack, invoke the actual method
        mv.visitMethodInsn(INVOKEVIRTUAL, writer.getInternalName(), declaration.name.real(), invokeDescriptor.toString(), false);

        // Might be it returns a primitive type, which we would have to wrap properly
        MPLType.visitBoxVariable(mv, returnType);

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
