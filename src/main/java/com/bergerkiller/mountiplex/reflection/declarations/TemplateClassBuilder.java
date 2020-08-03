package com.bergerkiller.mountiplex.reflection.declarations;

import static org.objectweb.asm.Opcodes.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.logging.Level;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.Template.Handle;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Reads the abstract class information of a Template {@link Template.Class Class} type and generates an appropriate
 * implementation of the abstract methods. Since the Class is a singleton, this builder produces an instance
 * of the Class rather than the Class object itself. Of this instance, all the fields are initialized.
 */
public class TemplateClassBuilder<C extends Template.Class<H>, H extends Handle> {
    public final java.lang.Class<C> classType;
    public final java.lang.Class<H> handleType;
    public final java.lang.Class<?> instanceType;
    public final String instanceClassPath;
    public final boolean isOptional;
    public final ClassDeclaration classDec;

    @SuppressWarnings("unchecked")
    public TemplateClassBuilder(java.lang.Class<C> classType, ClassDeclarationResolver classDeclarationResolver) {
        this.classType = classType;

        // Identify the Handle type used alongside this Class
        // If this is a top-level Class that is not inside a Handle, it creates
        // dummy Handle classes without any special implementation inside.
        java.lang.Class<?> handleType = this.classType.getDeclaringClass();
        if (handleType != null && Handle.class.isAssignableFrom(handleType)) {
            this.handleType = (java.lang.Class<H>) handleType;
        } else {
            this.handleType = (java.lang.Class<H>) Handle.class;
        }

        // Identify what instance type is represented by this Class
        // If we can't deduce it, assume Object for maximum compatibility
        this.instanceClassPath = recurseFindAnnotationValue(classType, Template.InstanceType.class,
                Template.InstanceType::value, "java.lang.Object");

        // Identify whether the Optional annotation is specified
        this.isOptional = recurseFindAnnotationValue(classType, Template.Optional.class,
                (a) -> Boolean.TRUE, Boolean.FALSE);

        // Resolve the Class Path to an actual Class object
        // If not found, and the Class is not meant to be optional, log an error
        this.instanceType = Resolver.loadClass(this.instanceClassPath, false);
        if (this.instanceType == null && !this.isOptional) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Class " + this.instanceClassPath + " not found; Template '" +
                    handleType.getSimpleName() + " not initialized.");
        }

        // If found, also resolve the class declaration to use
        // If one is expected but not found, log an error
        if (this.instanceType != null && this.instanceType != Object.class && classDeclarationResolver != null) {
            this.classDec = classDeclarationResolver.resolveClassDeclaration(this.instanceClassPath, this.instanceType);
            if (this.classDec == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class Declaration for " + this.instanceClassPath + " not found");
            }
        } else {
            this.classDec = null;
        }
    }

    /**
     * Builds the most appropriate {@link Template.Class Class} instance given the provided information
     * 
     * @return Class
     */
    public C build() {
        // If the class type is abstract then we must extend it, and implement whatever we can
        if (Modifier.isAbstract(this.classType.getModifiers())) {
            return buildExtend();
        }

        // By default, just call the default constructor on the Class type
        return buildDefault();
    }

    /**
     * Builds the Class object by using the original class, no new class is generated
     * 
     * @return constructed Class
     */
    private C buildDefault() {
        try {
            C generatedClass = this.classType.newInstance();
            generatedClass.init(this);
            return generatedClass;
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    /**
     * Extends the Class type specified and implements the missing methods
     * 
     * @return constructed Class
     */
    private C buildExtend() {
        // Set up the class writer for the implementation of the class type
        ExtendedClassWriter<C> cw = ExtendedClassWriter.builder(this.classType)
                .setFlags(ClassWriter.COMPUTE_MAXS)
                .setAccess(ACC_FINAL)
                .setPostfix("$impl").build();

        MethodVisitor mv;

        // Add default empty constructor
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(this.classType), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Override getSelfClassType() and return getClass().getSuperClass() instead
        mv = cw.visitMethod(ACC_PUBLIC, "getSelfClassType", "()Ljava/lang/Class;", "()Ljava/lang/Class<*>;", null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Generate the final Class instance and further initialize it
        C generatedClass = cw.generateInstance(new Class[0], new Object[0]);
        generatedClass.init(this);
        return generatedClass;
    }

    /**
     * Recursively looks up the Class hierarchy to find the first annotation of a given type.
     * If found, the property of the annotation as specified is returned. If not found, the
     * default value is returned instead.
     * 
     * @param type The Class type from which to recursively look for the annotation
     * @param annotationClass Annotation class type to find
     * @param method Method of the annotation class type to call
     * @param defaultValue Default value to return if the annotation is not found
     * @return value
     */
    private static <A extends Annotation, V> V recurseFindAnnotationValue(java.lang.Class<?> type, java.lang.Class<A> annotationClass, Function<A, V> method, V defaultValue) {
        java.lang.Class<?> currentType = type;
        while (currentType != null) {
            A annotation = currentType.getAnnotation(annotationClass);
            if (annotation != null) {
                return method.apply(annotation);
            } else {
                currentType = currentType.getDeclaringClass();
            }
        }
        return defaultValue;
    }
}
