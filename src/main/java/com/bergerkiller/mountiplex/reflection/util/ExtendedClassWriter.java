package com.bergerkiller.mountiplex.reflection.util;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/**
 * A class writer with the sole aim of extending a class and re-implementing certain methods in it
 * 
 * @param <T> type of base class
 */
public class ExtendedClassWriter<T> extends ClassWriter {
    private static final UniqueHash generatedClassCtr = new UniqueHash();
    private final String name;
    private final String internalName;
    private final String typeDescriptor;
    private final GeneratorClassLoader loader;
    private final List<StaticFieldInit> pendingStaticFields = new ArrayList<StaticFieldInit>();
    private CtClass ctClass = null;

    private ExtendedClassWriter(Builder<T> options) {
        super(options.flags);

        // Get or generate postfix
        String postfix = (options.postfix != null) ? options.postfix : getNextPostfix();

        // This is multi-thread safe
        this.loader = GeneratorClassLoader.get(options.superClass.getClassLoader());

        // Bugfix: pick a different postfix if another class already exists with this name
        // This can happen by accident as well, when a jar is incorrectly reloaded
        // Namespace clashes are nasty!
        {
            String postfix_original = postfix;
            for (int i = 1;; i++) {
                String tmpClassPath = MPLType.getName(options.superClass) + postfix;
                boolean classExists = false;

                try {
                    this.loader.loadClass(tmpClassPath);
                    classExists = true;
                } catch (ClassNotFoundException e) {}

                try {
                    MPLType.getClassByName(tmpClassPath);
                    classExists = true;
                } catch (ClassNotFoundException ex) {}

                if (classExists) {
                    postfix = postfix_original + "_" + i;
                } else {
                    break;
                }
            }
        }

        // Extends Object instead of SuperClass when it is an interface
        Class<?> superType = options.superClass.isInterface() ? Object.class : options.superClass;

        // If interfaces are specified, then the signature must be generated also
        String signature = null;
        if (!options.interfaces.isEmpty()) {
            signature = MPLType.getDescriptor(superType);
            for (Class<?> interfaceType : options.interfaces) {
                signature += MPLType.getDescriptor(interfaceType);
            }
        }

        // Class that is extended, is Object when super type is an interface
        String superName = MPLType.getInternalName(superType);

        // Interfaces List<Class<?>> -> String[] if set
        String[] interfaceNames = null;
        if (!options.interfaces.isEmpty()) {
            interfaceNames = new String[options.interfaces.size()];
            for (int i = 0; i < interfaceNames.length; i++) {
                interfaceNames[i] = MPLType.getInternalName(options.interfaces.get(i));
            }
        }

        this.name = MPLType.getName(options.superClass) + postfix;
        this.internalName = MPLType.getInternalName(options.superClass) + postfix;
        this.typeDescriptor = computeNameDescriptor(options.superClass, postfix);
        this.visit(V1_8, options.access, this.internalName, signature, superName, interfaceNames);
    }

    // TODO: make cleaner
    private static String computeNameDescriptor(Class<?> type, String postfix) {
        String basePath = MPLType.getDescriptor(type);
        return basePath.substring(0, basePath.length()-1) + postfix + ";";
    }

    /**
     * Gets the name of the class being generated
     * 
     * @return class name
     */
    public final String getName() {
        return this.name;
    }

    public final String getInternalName() {
        return this.internalName;
    }

    public final String getTypeDescriptor() {
        return this.typeDescriptor;
    }

    /**
     * Gets the ClassLoader which will be used to turn bytecode into a generated Class
     * 
     * @return ClassLoader
     */
    public final ClassLoader getClassLoader() {
        return this.loader;
    }

    // Completes the class, initializes any pending static fields before doing so
    private void closeASM() {
        if (!this.pendingStaticFields.isEmpty()) {
            MethodVisitor mv;

            // Write static initializer block sections for all static fields added
            mv = this.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            for (StaticFieldInit init : this.pendingStaticFields) {
                visitPushInt(mv, init.record);
                mv.visitMethodInsn(INVOKESTATIC, MPLType.getInternalName(GeneratorArgumentStore.class),
                        "fetch", "(I)Ljava/lang/Object;", false);
                if (init.fieldType != Object.class) {
                    mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(init.fieldType));
                }
                mv.visitFieldInsn(PUTSTATIC, getInternalName(), init.fieldName, MPLType.getDescriptor(init.fieldType));
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 0);
            mv.visitEnd();
        }
        this.visitEnd();
    }

    /**
     * Takes the current ByteCode already generated and finishes it, turning it into
     * a JavaAssist CtClass ready for further modifications. The return CtClass
     * can be further changed, such as adding methods or fields. When done, the
     * {@link #generate()} or {@link #generateInstance(Class[], Object[])} methods will
     * compile the final CtClass result.<br>
     * <br>
     * No more ASM commands (visit*) should be called after calling this method, as they
     * will have no effect.
     * 
     * @return Javassist CtClass
     */
    public CtClass getCtClass() {
        if (this.ctClass == null) {
            this.closeASM();

            // Convert the current byte representation into a ByteArray, and then into a CtClass
            // Next time generate() is called, we instead generate using JavaAssist
            javassist.ClassPool cp = javassist.ClassPool.getDefault();
            cp.insertClassPath(new javassist.ByteArrayClassPath(this.name, this.toByteArray()));
            try {
                this.ctClass = cp.get(this.name);
            } catch (NotFoundException e) {
                throw new RuntimeException("Failed to instantiate CtClass " + this.name + " from bytecode");
            }
        }
        return this.ctClass;
    }

    @SuppressWarnings("unchecked")
    public Class<T> generate() {
        if (this.ctClass == null) {
            this.closeASM();
            return (Class<T>) this.loader.createClassFromBytecode(this.name, this.toByteArray());
        } else {
            try {
                return (Class<T>) this.ctClass.toClass(this.loader, null);
            } catch (CannotCompileException e) {
                throw new RuntimeException("Failed to compile class " + this.name, e);
            }
        }
    }

    /**
     * Generates a new instance by calling the empty constructor
     * 
     * @return constructed instance of the generated class
     */
    public T generateInstance() {
        return generateInstance(new Class<?>[0], new Object[0]);
    }

    public T generateInstance(Class<?>[] parameterTypes, Object[] initArgs) {
        Class<T> type = this.generate();
        try {
            return (T) type.getConstructor(parameterTypes).newInstance(initArgs);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to generate class", t);
        }
    }

    /**
     * Gets a unique class name postfix to be used for a generated class
     * 
     * @return unique class name postfix
     */
    public static String getNextPostfix() {
        return "$mplgen" + generatedClassCtr.nextHex();
    }

    /**
     * If a Class Name is already taken, appends a number until the name is no longer used.
     * This may be needed if the server reloads and stale classes stay behind.
     * 
     * @param name
     * @return available class name
     */
    public static String getAvailableClassName(String name) {
        String resultName = name;
        for (int i = 1;; i++) {
            try {
                Class.forName(resultName);
                resultName = name + "_" + i;
            } catch (ClassNotFoundException ex) {
                return resultName;
            }
        }
    }

    /**
     * Includes instructions to push an int constant onto the stack. The right instruction for the size
     * of the number if selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushInt(MethodVisitor mv, int value) {
        if (value >= 0 && value <= 5) {
            mv.visitInsn(ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(new Integer(value));
        }
    }

    /**
     * Includes instructions to unbox a boxed value to a primitive value on the stack.
     * If no primitive type is requested, only a checked cast is performed.
     * 
     * @param mv method visitor
     * @param outType type to unbox or cast to
     */
    public static void visitUnboxVariable(MethodVisitor mv, java.lang.Class<?> outType) {
        if (outType.isPrimitive()) {
            Class<?> boxedType = BoxedType.getBoxedType(outType);
            if (boxedType != null) {
                mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(boxedType));
                mv.visitMethodInsn(INVOKEVIRTUAL, MPLType.getInternalName(boxedType),
                        MPLType.getName(outType) + "Value", "()" + MPLType.getDescriptor(outType), false);
            }
        } else if (outType.isArray()) {
            mv.visitTypeInsn(CHECKCAST, MPLType.getDescriptor(outType));
        } else if (!Object.class.equals(outType)) {
            mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(outType));
        }
    }

    /**
     * Includes instructions to invoke a static or member method in a class or interface, automatically
     * choosing the correct opcode.
     * 
     * @param mv method visitor
     * @param instanceType the method should be invoked on
     * @param method to be invoked
     */
    public static void visitInvoke(MethodVisitor mv, Class<?> instanceType, Method method) {
        final String instanceName = MPLType.getInternalName(instanceType);
        final boolean isInterface = instanceType.isInterface();
        if (Modifier.isStatic(method.getModifiers())) {
            mv.visitMethodInsn(INVOKESTATIC, instanceName, MPLType.getName(method), MPLType.getMethodDescriptor(method), isInterface);
        } else if (instanceType.isInterface()) {
            mv.visitMethodInsn(INVOKEINTERFACE, instanceName, MPLType.getName(method), MPLType.getMethodDescriptor(method), isInterface);
        } else {
            mv.visitMethodInsn(INVOKEVIRTUAL, instanceName, MPLType.getName(method), MPLType.getMethodDescriptor(method), isInterface);
        }
    }

    /**
     * Includes instructions to invoke a constructor
     * 
     * @param mv method visitor
     * @param instanceType type to construct, can not be an interface
     * @param constructor to be invoked
     */
    public static void visitInit(MethodVisitor mv, Class<?> instanceType, java.lang.reflect.Constructor<?> constructor) {
        final String instanceName = MPLType.getInternalName(instanceType);
        mv.visitMethodInsn(INVOKESPECIAL, instanceName, "<init>", MPLType.getConstructorDescriptor(constructor), false);
    }

    /**
     * Implements a method with a body that throws an UnsupportedOperationException
     * with a given message when called.
     * 
     * @param method
     * @param message
     */
    public void visitMethodUnsupported(Method method, String message) {
        MethodVisitor mv;

        mv = this.visitMethod(ACC_PUBLIC, MPLType.getName(method), MPLType.getMethodDescriptor(method), null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn(message);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(3, 1 + method.getParameterCount());
        mv.visitEnd();
    }

    /**
     * Adds a public static Invoker field to this Class definition, which will be initialized
     * to call the method by the MethodDeclaration specified.
     * 
     * @param fieldName The field name where the invoker will be stored
     * @param methodDec MethodDeclaration of the method to call
     */
    public void visitStaticInvokerField(String fieldName, MethodDeclaration methodDec) {
        visitStaticField(fieldName, Invoker.class, InitInvoker.forMethodLate(
                getClassLoader(),
                getName(),
                fieldName, methodDec));
    }

    /**
     * Adds a public static field to this Class definition with the initial value
     * as specified. The {@link GeneratorArgumentStore} is used to initialize the field
     * at class construction.
     * 
     * @param fieldName Name of the static field
     * @param fieldType Type of the field, which is the public facing field type
     * @param value Value the field will have during first-time class initialization
     */
    public void visitStaticField(String fieldName, Class<?> fieldType, Object value) {
        StaticFieldInit field = new StaticFieldInit(fieldName, fieldType, value);
        this.pendingStaticFields.add(field);

        // Write the field definition
        FieldVisitor fv;
        fv = this.visitField(ACC_PUBLIC | ACC_STATIC, fieldName,
                MPLType.getDescriptor(field.fieldType), null, null);
        fv.visitEnd();
    }

    /**
     * Creates a builder for extending the super class specified.
     * If the super class is an interface, Object is extended instead,
     * adding this super class as an interface to implement.
     * 
     * @param superClass
     */
    public static <T> Builder<T> builder(Class<T> superClass) {
        return new Builder<T>(superClass);
    }

    /**
     * Builder for setting up the extended class writer. Call build() to create the writer
     * and start writing the class.
     *
     * @param <T> Super class type
     */
    public static final class Builder<T> {
        private final Class<T> superClass;
        private List<Class<?>> interfaces = new ArrayList<Class<?>>(0);
        private int flags = 0;
        private int access = ACC_PUBLIC | ACC_STATIC;
        private String postfix = null;

        private Builder(Class<T> superClass) {
            this.superClass = superClass;
            if (superClass.isInterface()) {
                this.interfaces.add(superClass);
            }
        }

        public Builder<T> addInterface(Class<?> interfaceType) {
            this.interfaces.add(interfaceType);
            return this;
        }

        public Builder<T> addInterfaces(Collection<Class<?>> interfaceTypes) {
            this.interfaces.addAll(interfaceTypes);
            return this;
        }

        public Builder<T> setFlags(int flags) {
            this.flags |= flags;
            return this;
        }

        public Builder<T> setAccess(int access) {
            this.access |= access;
            return this;
        }

        public Builder<T> setPostfix(String postfix) {
            this.postfix = postfix;
            return this;
        }

        /**
         * Builds the extended class writer to begin writing the class
         * 
         * @return extended class writer
         */
        @SuppressWarnings("unchecked")
        public <TO> ExtendedClassWriter<TO> build() {
            return (ExtendedClassWriter<TO>) new ExtendedClassWriter<T>(this);
        }
    }

    private static class StaticFieldInit {
        public final String fieldName;
        public final Class<?> fieldType;
        public final int record;

        public StaticFieldInit(String fieldName, Class<?> fieldType, Object value) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.record = GeneratorArgumentStore.store(value);
        }
    }
}
