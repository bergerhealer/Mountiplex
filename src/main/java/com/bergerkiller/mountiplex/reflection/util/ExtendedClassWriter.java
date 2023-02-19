package com.bergerkiller.mountiplex.reflection.util;

import static org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLJavac;
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
    private static final UniqueHash generatedStaticFieldCtr = new UniqueHash();
    private final GeneratedClassName name;
    private final GeneratorClassLoader loader;
    private final Class<?> superType;
    private final List<StaticFieldInit> pendingStaticFields = new ArrayList<>();
    private final boolean singleton;
    private final List<StaticFieldInit> singletonMemberFields;
    private CtClass ctClass = null;
    private List<JavassistAction> javassistActions = Collections.emptyList();

    private ExtendedClassWriter(GeneratorClassLoader loader, Builder<T> options) {
        super(options.flags);
        this.loader = loader;
        this.name = options.exactName;

        // Extends Object instead of SuperClass when it is an interface
        this.superType = options.superClass.isInterface() ? Object.class : options.superClass;

        // Only works if singleton
        this.singleton = options.singleton;
        this.singletonMemberFields = options.singleton ? new ArrayList<>(5) : Collections.emptyList();

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

        // Class header stuff
        this.visit(V1_8, options.access, name.internalName, signature, superName, interfaceNames);

        // ASM: Add a static field to the generated class storing the singleton invoker instance
        if (singleton) {
            FieldVisitor fv;
            fv = visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "INSTANCE", getTypeDescriptor(), null, null);
            fv.visitEnd();
        }
    }

    /**
     * Gets the name of the class being generated
     * 
     * @return class name
     */
    public final String getName() {
        return name.name;
    }

    public final String getInternalName() {
        return name.internalName;
    }

    public final String getTypeDescriptor() {
        return name.typeDescriptor;
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
        if (singleton || !pendingStaticFields.isEmpty()) {
            MethodVisitor mv;

            // Write static initializer block sections for all static fields added
            // For singletons, also initialize the INSTANCE field with a new instance
            mv = this.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            if (singleton) {
                mv.visitTypeInsn(NEW, this.getInternalName());
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, this.getInternalName(), "<init>", "()V", false);
                mv.visitFieldInsn(PUTSTATIC, this.getInternalName(), "INSTANCE", this.getTypeDescriptor());
            }
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
            mv.visitMaxs(singleton ? 2 : 1, 0);
            mv.visitEnd();
        }

        // If not null then this is a singleton class, and this needs a default constructor
        if (singleton) {
            MethodVisitor mv;

            // Write an empty constructor initializer block sections for all member fields added
            // The <clinit> static initializer will call this one to initialize the member fields, too
            mv = this.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, MPLType.getInternalName(this.superType), "<init>", "()V", false);

            for (StaticFieldInit init : singletonMemberFields) {
                mv.visitVarInsn(ALOAD, 0);
                visitPushInt(mv, init.record);
                mv.visitMethodInsn(INVOKESTATIC, MPLType.getInternalName(GeneratorArgumentStore.class),
                        "fetch", "(I)Ljava/lang/Object;", false);
                if (init.fieldType != Object.class) {
                    mv.visitTypeInsn(CHECKCAST, MPLType.getInternalName(init.fieldType));
                }
                mv.visitFieldInsn(PUTFIELD, getInternalName(), init.fieldName, MPLType.getDescriptor(init.fieldType));
            }

            mv.visitInsn(RETURN);
            mv.visitMaxs(singletonMemberFields.isEmpty() ? 1 : 2, 1);
            mv.visitEnd();
        }
        this.visitEnd();
    }

    @FunctionalInterface
    public static interface JavassistAction {
        void run(CtClass invokerClass) throws NotFoundException, CannotCompileException;
    }

    /**
     * Adds a Javassist action that is run at the end after ASM finished generating the
     * main structure of the class. These actions are run after {@link #getCtClass()}
     * is first run, after which actions are added to the class right away.
     *
     * @param action Action to be executed. Is executed right away if {@link #getCtClass()}
     *               was called before.
     * @throws NotFoundException From action
     * @throws CannotCompileException From action
     */
    public void addJavassist(JavassistAction action) throws NotFoundException, CannotCompileException {
        if (ctClass != null) {
            action.run(ctClass);
        } else {
            if (javassistActions.isEmpty()) {
                javassistActions = new ArrayList<>(3);
            }
            javassistActions.add(action);
        }
    }

    /**
     * Takes the current ByteCode already generated and finishes it, turning it into
     * a JavaAssist CtClass ready for further modifications. The returned CtClass
     * can be further changed, such as adding methods or fields. When done, the
     * {@link #generate()} or {@link #generateInstance(Class[], Object[])} methods will
     * compile the final CtClass result.<br>
     * <br>
     * No more ASM commands (visit*) should be called after calling this method, as they
     * will have no effect.
     *
     * @return Javassist CtClass
     */
    public CtClass getCtClass() throws NotFoundException, CannotCompileException {
        return getCtClass(javassist.ClassPool.getDefault());
    }

    /**
     * Takes the current ByteCode already generated and finishes it, turning it into
     * a JavaAssist CtClass ready for further modifications. The returned CtClass
     * can be further changed, such as adding methods or fields. When done, the
     * {@link #generate()} or {@link #generateInstance(Class[], Object[])} methods will
     * compile the final CtClass result.<br>
     * <br>
     * No more ASM commands (visit*) should be called after calling this method, as they
     * will have no effect.
     *
     * @param classPool The ClassPool used to resolve types found in method bodies
     * @return Javassist CtClass
     */
    public CtClass getCtClass(javassist.ClassPool classPool) throws NotFoundException, CannotCompileException {
        if (ctClass == null) {
            this.closeASM();

            // Create a temporary class pool to create the initial CtClass object with from bytecode
            // Note: can use the other makeNewClassWithDefaultConstructor method if that's important
            CtClass newCtClass;
            try {
                if (singleton) {
                    // We assume the caller is going to add singleton fields and not use the Javassist API for
                    // this at all.
                    newCtClass = MPLJavac.makeNewClass(classPool, this.toByteArray());
                } else {
                    // Better be safe than sorry!
                    newCtClass = MPLJavac.makeNewClassWithDefaultConstructor(classPool, name.name, this.toByteArray());
                }
            } catch (NotFoundException e) {
                throw new RuntimeException("Failed to instantiate CtClass " + name.name + " from bytecode");
            }

            // Using for-i loop in case one action adds further actions
            for (int i = 0; i < javassistActions.size(); i++) {
                javassistActions.get(i).run(newCtClass);
            }
            javassistActions = Collections.emptyList();
            ctClass = newCtClass;
        }
        return ctClass;
    }

    @SuppressWarnings("unchecked")
    public Class<T> generate() {
        try {
            if (ctClass == null && !javassistActions.isEmpty()) {
                this.getCtClass();
            }
            if (ctClass == null) {
                this.closeASM();
                return (Class<T>) this.loader.createClassFromBytecode(name.name, this.toByteArray(), null);
            } else {
                return (Class<T>) this.ctClass.toClass(this.loader, null);
            }
        } catch (CannotCompileException e) {
            throw new RuntimeException("Failed to compile class " + name.name, e);
        } catch (NotFoundException e) {
            throw new RuntimeException("Compiling failed because a class was not found", e);
        }
    }

    /**
     * Generates the class and obtains a suitable Constructor for it
     *
     * @param parameterTypes Parameters of the constructor
     * @return Constructor of the generated class
     */
    public Constructor<T> generateConstructor(Class<?>... parameterTypes) {
        if (singleton) {
            throw new UnsupportedOperationException("Cannot create new instances of a singleton class");
        }

        Class<T> type = this.generate();
        try {
            return type.getConstructor(parameterTypes);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to generate class", t);
        }
    }

    /**
     * Generates a new instance by calling the empty constructor. For singleton classes
     * this will return the singleton instance.
     *
     * @return constructed instance of the generated class
     */
    @SuppressWarnings("unchecked")
    public T generateInstance() {
        if (singleton) {
            // Write the actual class
            Class<?> type = this.generate();

            // Return the singleton's INSTANCE field
            try {
                return (T) MPLType.getDeclaredField(type, "INSTANCE").get(null);
            } catch (VerifyError e) {
                throw new RuntimeException("Failed to load generated class " + name.name, e);
            } catch (Throwable t) {
                throw new IllegalStateException("INSTANCE not found in singleton. This should not happen!", t);
            }
        }

        return generateInstance(new Class<?>[0], new Object[0]);
    }

    /**
     * Generates a new instance by calling a constructor matching the parameter types,
     * with the arguments as specified.
     *
     * @param parameterTypes Parameter types
     * @param initArgs Argument values for the parameters
     * @return new instance
     */
    public T generateInstance(Class<?>[] parameterTypes, Object[] initArgs) {
        Constructor<T> constructor = this.generateConstructor(parameterTypes);
        try {
            return (T) constructor.newInstance(initArgs);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to generate class", t);
        }
    }

    /**
     * Generates a new instance by making use of Objenesis to null-instantiate
     * an instance of the class. No constructor will be called.
     *
     * @return null-instantiated instance of the generated class
     */
    public T generateInstanceNull() {
        return NullInstantiator.of(this.generate()).create();
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
                MPLType.getClassByName(resultName);
                resultName = name + "_" + i;
            } catch (ClassNotFoundException ex) {
                return resultName;
            }
        }
    }

    /**
     * Includes instructions to push a char constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushChar(MethodVisitor mv, char value) {
        visitPushInt(mv, (int) value);
    }

    /**
     * Includes instructions to push a boolean constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushBoolean(MethodVisitor mv, boolean value) {
        mv.visitInsn(value ? ICONST_1 : ICONST_0);
    }

    /**
     * Includes instructions to push a float constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushFloat(MethodVisitor mv, float value) {
        if (value == 0.0f) {
            mv.visitInsn(FCONST_0);
        } else if (value == 1.0f) {
            mv.visitInsn(FCONST_1);
        } else if (value == 2.0f) {
            mv.visitInsn(FCONST_2);
        } else {
            mv.visitLdcInsn(Float.valueOf(value));
        }
    }

    /**
     * Includes instructions to push a double constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushDouble(MethodVisitor mv, double value) {
        if (value == 0.0f) {
            mv.visitInsn(DCONST_0);
        } else if (value == 1.0f) {
            mv.visitInsn(DCONST_1);
        } else {
            mv.visitLdcInsn(Double.valueOf(value));
        }
    }

    /**
     * Includes instructions to push a constant onto the stack. The right instruction for the size
     * of the value is selected. If the value being stored cannot be represented using instructions
     * alone, then a static field is added initialized with the value from which the value is loaded
     * onto the stack.<br>
     * <br>
     * If a null value is specified, and the type is a primitive type, then the default primitive
     * type value (0, false, etc.) is loaded instead.
     *
     * @param mv method visitor
     * @param type Type of value to load onto the stack
     * @param value Value to push onto the stack
     */
    public void visitPush(MethodVisitor mv, Class<?> type, Object value) {
        // Nothing is loaded for void types
        if (type == void.class) {
            return;
        }

        // null -> 0, false, etc.
        if (type.isPrimitive() && value == null) {
            value = BoxedType.getDefaultValue(type);
        }

        // Load primitive types
        if (value == null) {
            mv.visitInsn(ACONST_NULL);
        } else if (type == byte.class) {
            visitPushByte(mv, ((Byte) value).byteValue());
        } else if (type == short.class) {
            visitPushShort(mv, ((Short) value).shortValue());
        } else if (type == int.class) {
            visitPushInt(mv, ((Integer) value).intValue());
        } else if (type == long.class) {
            visitPushLong(mv, ((Long) value).longValue());
        } else if (type == float.class) {
            visitPushFloat(mv, ((Float) value).floatValue());
        } else if (type == double.class) {
            visitPushDouble(mv, ((Double) value).doubleValue());
        } else if (type == char.class) {
            visitPushChar(mv, ((Character) value).charValue());
        } else if (type == boolean.class) {
            visitPushBoolean(mv, ((Boolean) value).booleanValue());
        } else if (type == String.class) {
            // JVM allows for storing strings
            mv.visitLdcInsn(value);
        } else {
            // Object constant value
            String name = "mplgen_cinit_field_" + generatedStaticFieldCtr.nextHex();
            this.visitStaticField(name, type, value);
            mv.visitFieldInsn(GETSTATIC, this.name.internalName, name, MPLType.getDescriptor(type));
        }
    }

    /**
     * Includes instructions to push a byte constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushByte(MethodVisitor mv, byte value) {
        visitPushInt(mv, value);
    }

    /**
     * Includes instructions to push a short constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushShort(MethodVisitor mv, short value) {
        visitPushInt(mv, value);
    }

    /**
     * Includes instructions to push an int constant onto the stack. The right instruction for the size
     * of the number is selected.
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
            mv.visitLdcInsn(Integer.valueOf(value));
        }
    }

    /**
     * Includes instructions to push a long constant onto the stack. The right instruction for the size
     * of the number is selected.
     * 
     * @param mv method visitor
     * @param value Value to push onto the stack
     */
    public static void visitPushLong(MethodVisitor mv, long value) {
        if (value >= 0 && value <= 5) {
            mv.visitInsn(LCONST_0 + (int) value);
        } else {
            mv.visitLdcInsn(Long.valueOf(value));
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
     * Implements a method with a body that ignores all input parameters and returns
     * a constant value instead. For non-null value types, a static field is added
     * to the class definition setting this constant value to be returned.
     *
     * @param method
     * @param value
     */
    public void visitMethodReturnConstant(Method method, Object value) {
        Class<?> returnType = method.getReturnType();
        MethodVisitor mv;

        // Make sure return type is compatible with method signature
        if (returnType != void.class) {
            if (returnType.isPrimitive()) {
                if (value == null) {
                    throw new IllegalArgumentException("Cannot return 'null' from method " + method);
                }
                Class<?> primType = BoxedType.getUnboxedType(value.getClass());
                if (primType != returnType) {
                    throw new IllegalArgumentException("Cannot return type " + primType.getName() + " from method " + method);
                }
            } else if (value != null && !returnType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Cannot return type " + value.getClass().getName() + " from method " + method);
            }
        }

        // Compute total stack size of the method parameters
        int maxLocals = 1;
        for (Class<?> param : method.getParameterTypes()) {
            maxLocals += MPLType.getType(param).getSize();
        }

        mv = this.visitMethod(ACC_PUBLIC, MPLType.getName(method), MPLType.getMethodDescriptor(method), null, null);
        mv.visitCode();
        visitPush(mv, returnType, value);
        mv.visitInsn(MPLType.getOpcode(returnType, IRETURN));
        mv.visitMaxs(MPLType.getType(returnType).getSize(), maxLocals);
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
        fv = this.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, fieldName,
                MPLType.getDescriptor(field.fieldType), null, null);
        fv.visitEnd();
    }

    /**
     * Adds a <b>private</b> final member field to the class, initialized only once when
     * {@link #generateInstance()} is called with a generated default no-arg constructor.
     * This method is only suitable for generating singleton classes. The parent class may
     * not contain any parameters to be filled.<br>
     * <br>
     * The added field can be used by other member methods added to the class.<br>
     * <br>
     * This method is only valid is <code>setSingleton(true)</code> was called on the Builder.
     *
     * @param fieldName Name of the static field
     * @param fieldType Type of the field, which is the public facing field type
     * @param value Value the field will have during first-time class initialization
     */
    public void visitSingletonField(String fieldName, Class<?> fieldType, Object value) {
        if (singletonMemberFields == null) {
            throw new IllegalStateException("The Class being generated isn't a singleton. Missing setSingleton(true) in Builder?");
        }

        StaticFieldInit field = new StaticFieldInit(fieldName, fieldType, value);
        this.singletonMemberFields.add(field);

        // Write the field definition
        FieldVisitor fv;
        fv = this.visitField(ACC_PRIVATE | ACC_FINAL, fieldName,
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
    @SuppressWarnings("unchecked")
    public static <T> Builder<T> builder(Class<? super T> superClass) {
        return new Builder<T>((Class<T>) superClass);
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
        private boolean singleton = false;
        private String postfix = null;
        private GeneratedClassName exactName = null;
        private ClassLoader classLoader = null;

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
         * Sets whether to build a singleton class. Singleton classes automatically generate
         * a default constructor, in which singleton member fields can be initialized. A
         * public static final <b>INSTANCE</b> field is also added storing the generated
         * instance. Only one instance can ever exist.
         *
         * @param isSingleton Whether to generate a singleton class
         * @return this Builder
         */
        public Builder<T> setSingleton(boolean isSingleton) {
            this.singleton = isSingleton;
            return this;
        }

        /**
         * Forces a class to be generated with exactly the name as specified.
         * Will throw an error if a Class by this name already exists.
         *
         * @param name
         * @return builder
         */
        public Builder<T> setExactName(String name) {
            this.exactName = new GeneratedClassName(name);
            return this;
        }

        public Builder<T> setClassLoader(ClassLoader loader) {
            this.classLoader = loader;
            return this;
        }

        /**
         * Builds the extended class writer to begin writing the class
         * 
         * @return extended class writer
         */
        @SuppressWarnings("unchecked")
        public <TO> ExtendedClassWriter<TO> build() {
            GeneratorClassLoader classLoader = initClassLoader();
            computeExactName(classLoader);
            return (ExtendedClassWriter<TO>) new ExtendedClassWriter<T>(classLoader, this);
        }

        /**
         * Defers building the extended class to a later time. The final name
         * to be generated is decided right away, and is stored in a registry
         * until a GeneratorClassLoader loads this exact same name. When that
         * happens, or someone forces initialization sooner, the callback is
         * called and the class is generated.
         *
         * @param callback Callback called when the actual class needs to be generated.
         *                 An ExtendedClassWriter instance is passed to it, on which the
         *                 {@link ExtendedClassWriter#generateInstance()} method should be called.
         * @return deferred builder
         */
        public Deferred<T> defer(Function<ExtendedClassWriter<T>, T> callback) {
            GeneratorClassLoader classLoader = initClassLoader();
            computeExactName(classLoader);
            return new Deferred<T>(classLoader, this, callback);
        }

        private GeneratorClassLoader initClassLoader() {
            // This is multi-thread safe
            if (classLoader == null) {
                return GeneratorClassLoader.get(superClass.getClassLoader());
            } else if (classLoader instanceof GeneratorClassLoader) {
                return (GeneratorClassLoader) classLoader;
            } else {
                return GeneratorClassLoader.get(classLoader);
            }
        }

        private void computeExactName(GeneratorClassLoader loader) {
            if (exactName != null) {
                return;
            }

            // Get or generate postfix
            String postfix = (this.postfix != null) ? this.postfix : ("$mpldefgen" + generatedClassCtr.nextHex());

            // Bugfix: pick a different postfix if another class already exists with this name
            // This can happen by accident as well, when a jar is incorrectly reloaded
            // Namespace clashes are nasty!
            {
                String postfix_original = postfix;
                for (int i = 1;; i++) {
                    String tmpClassPath = MPLType.getName(superClass) + postfix;
                    boolean classExists = false;

                    try {
                        loader.loadClass(tmpClassPath);
                        classExists = true;
                    } catch (ClassNotFoundException e) {}

                    try {
                        Resolver.getClassByExactName(tmpClassPath);
                        classExists = true;
                    } catch (ClassNotFoundException ex) {}

                    if (classExists) {
                        postfix = postfix_original + "_" + i;
                    } else {
                        break;
                    }
                }
            }

            this.exactName = new GeneratedClassName(superClass, postfix);
        }
    }

    /**
     * Deferred builder. The class name is already known, but it may or may not have
     * already been generated. If not already loaded by a GeneratorClassLoader,
     * the object is stored in a registry for easy retrieval.
     *
     * @param <T> Class Type
     */
    public static final class Deferred<T> implements LazyInitializedObject {
        private static final Map<String, Deferred<?>> pending = new ConcurrentHashMap<>();
        private final GeneratorClassLoader classLoader;
        private final Builder<T> builder;
        private final GeneratedClassName name;
        private final Function<ExtendedClassWriter<T>, T> callback;
        private T generated;
        private RuntimeException generateError;

        private Deferred(GeneratorClassLoader classLoader, Builder<T> builder, Function<ExtendedClassWriter<T>, T> callback) {
            this.classLoader = classLoader;
            this.builder = builder;
            this.name = builder.exactName;
            this.callback = callback;
            this.generated = null;
            this.generateError = null;

            synchronized (pending) {
                pending.put(name.name, this);
            }
        }

        // Called by GeneratorClassLoader
        static Class<?> load(String name) throws ClassNotFoundException {
            Deferred<?> deferred = pending.get(name);
            if (deferred == null) {
                return null;
            }

            try {
                return deferred.generate().getClass();
            } catch (Throwable t) {
                throw new ClassNotFoundException("Failed to generate class " + name, t);
            }
        }

        /**
         * Gets the JVM name of this deferred class
         *
         * @return JVM Class Name
         */
        public String getName() {
            return name.name;
        }

        /**
         * Gets the internal name of this deferred class
         *
         * @return internal name
         */
        public String getInternalName() {
            return name.internalName;
        }

        /**
         * Gets the ASM Type Descriptor of this deferred class
         *
         * @return type descriptor
         */
        public String getTypeDescriptor() {
            return name.typeDescriptor;
        }

        /**
         * Generates the class and an instance of the class that should be loaded.
         * Only one instance can ever be generated. If called multiple times, the last-generated
         * instance is returned.
         *
         * @return Generated class instance
         */
        public synchronized T generate() {
            // Check already generated
            {
                T generated = this.generated;
                if (generated != null) {
                    return generated;
                }
            }

            // Check there was an error before, and throw again if so
            {
                RuntimeException err = this.generateError;
                if (err != null) {
                    throw err;
                }
            }

            // Generate
            try {
                ExtendedClassWriter<T> writer = new ExtendedClassWriter<T>(classLoader, builder);
                T result = callback.apply(writer);
                if (result == null) {
                    throw new IllegalStateException("Deferred callback " + callback.getClass().getName() + " returned null");
                }
                this.generated = result;
                this.generateError = null;

                // Successful, no longer needed to defer-generate this class
                // If not successful, it sticks around to re-throw the same error over and over
                pending.remove(name.name);

                return result;
            } catch (RuntimeException err) {
                this.generated = null;
                this.generateError = err;
                throw err;
            }
        }

        @Override
        public void forceInitialization() {
            generate();
        }
    }

    /**
     * The name, internal name and type descriptor of a generated class
     */
    private static final class GeneratedClassName {
        public final String name;
        public final String internalName;
        public final String typeDescriptor;

        public GeneratedClassName(String name) {
            this.name = name;
            this.internalName = name.replace('.', '/');
            this.typeDescriptor = "L" + this.internalName + ";"; // Might fail sometimes...
        }

        public GeneratedClassName(Class<?> superClass, String postfix) {
            this.name = MPLType.getName(superClass) + postfix;
            this.internalName = MPLType.getInternalName(superClass) + postfix;
            this.typeDescriptor = computeNameDescriptor(superClass, postfix);
        }

        // TODO: make cleaner
        private static String computeNameDescriptor(Class<?> type, String postfix) {
            String basePath = MPLType.getDescriptor(type);
            return basePath.substring(0, basePath.length()-1) + postfix + ";";
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
