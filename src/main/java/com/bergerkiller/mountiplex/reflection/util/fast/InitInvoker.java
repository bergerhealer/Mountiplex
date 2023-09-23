package com.bergerkiller.mountiplex.reflection.util.fast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.FieldAccessor;
import com.bergerkiller.mountiplex.reflection.IgnoredFieldAccessor;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.util.ExtendedClassWriter;
import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Special Invoker that initializes an Invoker field when first called, allowing for
 * lazy initialization.
 *
 * @param <T>
 */
public abstract class InitInvoker<T> implements Invoker<T>, LazyInitializedObject {
    private static final InitInvoker<?> defaultUnavailableMethod = unavailable("method", "!!UNKNOWN!!");
    private final FieldAccessor<Invoker<T>> fieldAccessor;
    private final Object fieldInstance;

    /**
     * Init invoker that always runs create() and never updates a field. Primarily used
     * to throw exceptions.
     */
    protected InitInvoker() {
        this.fieldInstance = null;
        this.fieldAccessor = new IgnoredFieldAccessor<Invoker<T>>(this);
    }

    /**
     * Creates an InitInvoker that updates the place where the invoker is stored using a field accessor.
     * 
     * @param instance Instance on which get and set operations are performed
     * @param accessor The accessor using which get and set operations are performed
     */
    public <I> InitInvoker(I instance, FieldAccessor<Invoker<T>> accessor) {
        this.fieldInstance = instance;
        this.fieldAccessor = accessor;
    }

    /**
     * Creates the invoker which is meant to be used, initializing it.
     * Should throw an exception when initialization fails.
     * 
     * @return invoker
     */
    protected abstract Invoker<T> create();

    /**
     * Initializes this invoker. If this is a generated method body,
     * the method is compiled. In other cases the method will be found through
     * reflection and optimized accessors compiled. All of this is only performed once.
     * 
     * All calls to invoke() in this class explicitly call initializeInvoker() first.
     * 
     * @return the new invoker that should be used from now on
     */
    @Override
    public final Invoker<T> initializeInvoker() {
        Invoker<T> invoker = this.fieldAccessor.get(this.fieldInstance);
        if (invoker == this) {
            synchronized (this) {
                invoker = this.fieldAccessor.get(this.fieldInstance);
                if (invoker == this) {
                    invoker = this.create();
                    this.fieldAccessor.set(this.fieldInstance, invoker);
                }
            }
        }
        return invoker;
    }

    @Override
    public final T invoke(Object instance) {
        return initializeInvoker().invoke(instance);
    }

    @Override
    public final T invoke(Object instance, Object arg0) {
        return initializeInvoker().invoke(instance, arg0);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1) {
        return initializeInvoker().invoke(instance, arg0, arg1);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2, arg3);
    }

    @Override
    public final T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return initializeInvoker().invoke(instance, arg0, arg1, arg2, arg3, arg4);
    }

    @Override
    public final T invokeVA(Object instance, Object... args) {
        return initializeInvoker().invokeVA(instance, args);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the invoker is not available
     * 
     * @param type The type of invoker that is missing (method, constructor, etc.)
     * @param missingInfo Description of the invoker that is missing
     * @return init invoker that throws when invoke() is called
     */
    public static <T> InitInvoker<T> unavailable(String type, String missingInfo) {
        return new UnavailableInvoker<T>(type, missingInfo);
    }

    /**
     * Creates an init invoker that throws an exception when initializing,
     * indicating the method is not available. A default description (!!UNKNOWN!!)
     * is in this exception. This can be used to initialize fields before they are assigned.
     * 
     * @return init invoker
     */
    @SuppressWarnings("unchecked")
    public static <T> InitInvoker<T> unavailableMethod() {
        return (InitInvoker<T>) defaultUnavailableMethod;
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     *
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param method The method to create an invoker for
     * @return init invoker
     */
    public static <T> InitInvoker<T> forMethod(ClassLoader classLoader, Object fieldInstance, String fieldName, java.lang.reflect.Method method) {
        ClassResolver resolver = new ClassResolver();
        resolver.setClassLoader(classLoader);
        return forMethod(fieldInstance, fieldName, new MethodDeclaration(resolver, method));
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     *
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param method The method to create an invoker for
     * @return init invoker
     */
    public static <T> InitInvoker<T> forMethod(Object fieldInstance, String fieldName, MethodDeclaration method) {
        return forMethod(fieldInstance, new ReflectionFieldAccessor<T>(fieldInstance.getClass(), fieldName), method);
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The static invoker field is updated
     * using reflection by setting a field declared in the class by the name specified. Meant to be used
     * with generated code, when the declaring class name is not yet known.
     *
     * @param fieldDeclaringClass Class in which the field to update is stored
     * @param fieldName The name of the invoker field in the declaring class
     * @param method The method to create an invoker for
     * @return init invoker
     */
    public static <T> InitInvoker<T> forMethodLate(String fieldDeclaringClass, String fieldName, MethodDeclaration method) {
        ClassLoader classLoader = (method == null) ? InitInvoker.class.getClassLoader() : method.getResolver().getClassLoader();
        return forMethod(null, new ReflectionFieldAccessorLate<T>(classLoader, fieldDeclaringClass, fieldName), method);
    }

    /**
     * Creates an init invoker that invokes a method defined by a method declaration. The invoker field is updated
     * using the field accessor, on the instance specified.
     *
     * @param instance Object on which to set the invoker field
     * @param accessor Accessor for the invoker field
     * @param method The method to create an invoker for
     * @return init invoker
     */
    @SuppressWarnings("unchecked")
    public static <T> InitInvoker<T> forMethod(Object instance, FieldAccessor<Invoker<T>> accessor, MethodDeclaration method) {
        if (method == null) {
            return unavailableMethod();
        } else if (method.body != null) {
            // Runtime-generated code invoker
            return (InitInvoker<T>) InitGeneratedCodeInvoker.create(instance, accessor, method);
        } else if (method.method != null) {
            // Runtime-generated method invoker, or one using reflection
            return new InitGeneratedExecutableInvoker<T>(instance, accessor, method.method);
        } else if (method.constructor != null) {
            // Runtime-generated constructor invoker, or one using reflection
            return new InitGeneratedExecutableInvoker<T>(instance, accessor, method.constructor);
        } else if (method.isRecordFieldChanger) {
            // Runtime-generated record class field changer
            String nameAlias = method.name.hasAlias() ? method.name.alias() : "change";
            List<String> fields = new ArrayList<>(method.parameters.parameters.length);
            for (ParameterDeclaration param : method.parameters.parameters) {
                fields.add(param.name.value());
            }
            return new InitGeneratedRecordFieldChangerInvoker<T>(instance, accessor,
                    method.getDeclaringClass(), nameAlias, fields);
        } else {
            return unavailable("method", method.toString());
        }
    }

    /**
     * Reuses another invoker, initializing it when first called and updating the field where it is stored. Effectively,
     * this allows storing the same invoker in more than one place. The invoker field is updated
     * using reflection by setting a field in the field instance specified.
     * 
     * @param fieldInstance Object on which to set the invoker field
     * @param fieldName The name of the invoker field in fieldInstance
     * @param invoker The invoker to initialize and use
     * @return init invoker
     */
    public static <T> InitInvoker<T> proxy(Object fieldInstance, String fieldName, Invoker<T> invoker) {
        return proxy(fieldInstance, new ReflectionFieldAccessor<T>(fieldInstance.getClass(), fieldName), invoker);
    }

    /**
     * Reuses another invoker, initializing it when first called and updating the field where it is stored. Effectively,
     * this allows storing the same invoker in more than one place.
     * 
     * @param instance Object on which to set the invoker field
     * @param accessor Accessor for the invoker field
     * @param invoker The invoker to initialize and use
     * @return init invoker
     */
    public static <T> InitInvoker<T> proxy(Object instance, FieldAccessor<Invoker<T>> accessor, Invoker<T> invoker) {
        return new ProxyInvoker<T>(instance, accessor, invoker);
    }

    /**
     * Invoker used by {@link #unavailable(String, String)}
     *
     * @param <T>
     */
    public static final class UnavailableInvoker<T> extends InitInvoker<T> {
        private final String type;
        private final String missingInfo;

        private UnavailableInvoker(String type, String missingInfo) {
            this.type = type;
            this.missingInfo = missingInfo;
        }

        @Override
        public final Invoker<T> create() {
            throw new UnsupportedOperationException(type + " " + missingInfo + " is not available");
        }
    }

    /**
     * Reuses another invoker, proxying it, while also updating the field this proxy is stored in
     *
     * @param <T>
     */
    private static final class ProxyInvoker<T> extends InitInvoker<T> {
        private final Invoker<T> invoker;

        public ProxyInvoker(Object fieldInstance, FieldAccessor<Invoker<T>> fieldAccessor, Invoker<T> invoker) {
            super(fieldInstance, fieldAccessor);
            this.invoker = invoker;
        }

        @Override
        public Invoker<T> create() {
            return invoker.initializeInvoker();
        }
    }

    /**
     * Helper class that initializes a runtime-generated invoker that clones a record class
     * with one or more record field values changed.
     *
     * @param <T>
     * @see RecordClassFieldChanger
     */
    private static final class InitGeneratedRecordFieldChangerInvoker<T> extends InitInvoker<T> {
        private final Class<?> declaringClass;
        private final String nameAlias;
        private final List<String> recordFields;

        protected InitGeneratedRecordFieldChangerInvoker(Object instance, FieldAccessor<Invoker<T>> accessor,
                                                         Class<?> declaringClass, String nameAlias, List<String> recordFields) {
            super(instance, accessor);
            this.declaringClass = declaringClass;
            this.nameAlias = nameAlias;
            this.recordFields = recordFields;
        }

        @Override
        protected Invoker<T> create() {
            return RecordClassFieldChanger.create(declaringClass, nameAlias, recordFields);
        }
    }

    /**
     * Helper class that initializes a runtime-generated invoker based on a Method or Constructor signature.
     * Generates a class that calls the method or constructor directly. If calling is not possible,
     * reflection is initialized and a reflection invoker is created instead.
     *
     * @param <T>
     */
    private static final class InitGeneratedExecutableInvoker<T> extends InitInvoker<T> {
        private final java.lang.reflect.Executable executable;

        protected InitGeneratedExecutableInvoker(Object instance, FieldAccessor<Invoker<T>> accessor, java.lang.reflect.Executable executable) {
            super(instance, accessor);
            this.executable = executable;
        }

        @Override
        protected Invoker<T> create() {
            if (GeneratedInvoker.canCreate(executable)) {
                return GeneratedInvoker.create(executable);
            } else {
                return ReflectionInvoker.create(executable);
            }
        }
    }

    /**
     * Helper class that initializes a runtime-generated invoker based on a code body in a Method Declaration.
     * This is the base class, as the actual class is runtime-generated to implement the non-generic
     * interface for calling this generated method directly without casting or boxing overhead.
     */
    public static class InitGeneratedCodeInvoker extends InitInvoker<Object> implements GeneratedExactSignatureInvoker<Object> {
        private final ExtendedClassWriter.Deferred<? extends GeneratedCodeInvoker<Object>> invoker;

        private InitGeneratedCodeInvoker(Object instance, FieldAccessor<Invoker<Object>> accessor,
                ExtendedClassWriter.Deferred<? extends GeneratedCodeInvoker<Object>> invoker
        ) {
            super(instance, accessor);
            this.invoker = invoker;
        }

        @Override
        public String getInvokerClassInternalName() {
            return invoker.getInternalName();
        }

        @Override
        public String getInvokerClassTypeDescriptor() {
            return invoker.getTypeDescriptor();
        }

        @Override
        protected Invoker<Object> create() {
            return invoker.generate();
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public static <T> InitGeneratedCodeInvoker create(Object instance, FieldAccessor<Invoker<T>> accessor, MethodDeclaration methodDeclaration) {
            // Defer generate the actual generated code invoker for this method
            ExtendedClassWriter.Deferred<? extends GeneratedCodeInvoker<Object>> invoker;
            invoker = GeneratedCodeInvoker.createDefer(methodDeclaration);

            return new InitGeneratedCodeInvoker(instance, (FieldAccessor) accessor, invoker);
        }
    }

    /**
     * Similar to {@link ReflectionFieldAccessor} but loads the class by name at the time it is needed.
     * This way the field of a not-yet generated class can be used.
     * 
     * @param <T>
     */
    private static final class ReflectionFieldAccessorLate<T> extends ReflectionFieldAccessorBase<T> {
        private final ClassLoader classLoader;
        private final String declaringClassName;
        private final String fieldName;
        private java.lang.reflect.Field field = null;

        public ReflectionFieldAccessorLate(ClassLoader classLoader, String declaringClassName, String fieldName) {
            this.classLoader = classLoader;
            this.declaringClassName = declaringClassName;
            this.fieldName = fieldName;
        }

        @Override
        public java.lang.reflect.Field getField() {
            if (field == null) {
                try {
                    Class<?> declaringClass = classLoader.loadClass(this.declaringClassName);
                    field = findField(declaringClass, fieldName);
                } catch (Throwable t) {
                    throw MountiplexUtil.uncheckedRethrow(t);
                }
            }
            return field;
        }
    }

    /**
     * Helper class for changing a field using reflection. Not making use of SafeField or Generated Field
     * logic because this logic only has to occur once, and generating code for that is a waste of time.
     *
     * @param <T>
     */
    private static final class ReflectionFieldAccessor<T> extends ReflectionFieldAccessorBase<T> {
        private final java.lang.reflect.Field field;

        public ReflectionFieldAccessor(Class<?> declaringClass, String fieldName) {
            try {
                field = findField(declaringClass, fieldName);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }

        @Override
        public Field getField() {
            return field;
        }
    }

    // Base implementation for accessing the field using reflection
    private static abstract class ReflectionFieldAccessorBase<T> implements FieldAccessor<Invoker<T>> {

        public abstract java.lang.reflect.Field getField();

        protected static java.lang.reflect.Field findField(Class<?> type, String fieldName) throws Throwable {
            try {
                return MPLType.getDeclaredField(type, fieldName);
            } catch (NoSuchFieldException err) {
                Class<?> supertype = type.getSuperclass();
                if (supertype == null) {
                    throw err;
                } else {
                    return findField(supertype, fieldName);
                }
            }
        }

        @Override
        @SuppressWarnings({ "unchecked", "deprecation" })
        public synchronized Invoker<T> get(Object instance) {
            try {
                java.lang.reflect.Field field = getField();
                boolean wasAccessible = field.isAccessible();
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(instance);
                } finally {
                    field.setAccessible(wasAccessible);
                }
                return (Invoker<T>) value;
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public synchronized boolean set(Object instance, Invoker<T> value) {
            try {
                java.lang.reflect.Field field = getField();

                // Special logic for static final fields
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                    // Hax!
                    // Make sure <clinit> is fired
                    field.get(instance);
                    // Set it now the class/field is initialized - avoids weirdness!
                    GeneratedAccessor.GeneratedStaticFinalAccessor.setUninitializedField(field, value);
                } else {
                    // Boring old reflection
                    boolean wasAccessible = field.isAccessible();
                    try {
                        field.setAccessible(true);
                        field.set(instance, value);
                    } finally {
                        field.setAccessible(wasAccessible);
                    }
                }

                return true;
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }
    }
}
