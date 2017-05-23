package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.reflection.FieldAccessor;
import com.bergerkiller.mountiplex.reflection.MethodAccessor;
import com.bergerkiller.mountiplex.reflection.SafeField;
import com.bergerkiller.mountiplex.reflection.SafeMethod;
import com.bergerkiller.mountiplex.reflection.TranslatorFieldAccessor;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper.InitMethod;

public class Template {

    public static class Class<H extends Handle> {
        private boolean valid = false;
        private java.lang.Class<?> classType = null;
        private DuplexConverter<Object, H> handleConverter = null;
        private final java.lang.Class<H> handleType;

        @SuppressWarnings("unchecked")
        public Class() {
            this.handleType = (java.lang.Class<H>) getClass().getDeclaringClass();
        }

        /**
         * Creates a new Handle instance suitable for this Template Class type
         * 
         * @param instance to create a handle for
         * @return handle
         */
        public final H createHandle(Object instance) {
            try {
                H handle;
                handle = this.handleType.newInstance();
                handle.instance = instance;
                return handle;
            } catch (InstantiationException e) {
                throw new RuntimeException("Failed to instantiate", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not construct new handle", e);
            }
        }

        private final void init(java.lang.Class<?> classType) {
            this.classType = classType;
            this.valid = true;

            // Create duplex converter between handle type and instance type
            this.handleConverter = new DuplexConverter<Object, H>(classType, this.handleType) {
                @Override
                public H convertInput(Object value) {
                    try {
                        H handle;
                        handle = handleType.newInstance();
                        handle.instance = value;
                        return handle;
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to construct handle " + handleType.getName(), t);
                        return null;
                    }
                }

                @Override
                public Object convertOutput(H value) {
                    return value.instance;
                }
            };
            Conversion.registerConverter(this.handleConverter);

            // Resolve class declaration
            ClassDeclaration dec = Resolver.resolveClassDeclaration(this.classType);
            if (dec == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class Declaration for " + this.classType.getName() + " not found");
                valid = false;
                return;
            }

            // Initialize all declared fields
            for (java.lang.reflect.Field templateFieldRef : getClass().getFields()) {
                String templateFieldName = templateFieldRef.getName();
                try {
                    Object templateField = templateFieldRef.get(this);
                    if (templateField instanceof TemplateElement) {
                        Object result = ((TemplateElement<?>) templateField).init(dec, templateFieldName);
                        if (result == null) {
                            valid = false;
                        }
                    }
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize template field " +
                        "'" + templateFieldName + "' in " + classType.getName(), t);
                    valid = false;
                }
            }
        }

        /**
         * Gets whether this entire Class Template has been initialized without any errors
         * 
         * @return True if loaded successfully, False if errors had occurred
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Gets the internally stored Class Type of this Class
         * 
         * @return class type
         */
        public java.lang.Class<?> getType() {
            return this.classType;
        }

        /**
         * Gets the duplex converter used to convert between the internal Class type, and
         * the handle type for that type. This converter is automatically registered.
         * 
         * @return handle converter
         */
        public DuplexConverter<Object, H> getHandleConverter() {
            return this.handleConverter;
        }

        /**
         * Checks whether an instance is of the exact type as this
         * 
         * @param instance to check
         * @return True if the instance type is this type, False if not or the instance is null
         */
        public boolean isType(Object instance) {
            return instance != null && this.classType.equals(instance.getClass());
        }

        /**
         * Checks whether a type equals this type
         * 
         * @param type to check
         * @return True if equals, False if not or null
         */
        public boolean isType(java.lang.Class<?> type) {
            return this.classType.equals(type);
        }

        /**
         * Checks whether an Object value can be assigned to this type
         * @param instance to check
         * @return True if assignable, False if not or instance is <i>null</i>
         */
        public boolean isAssignableFrom(Object instance) {
            return instance != null && this.classType.isAssignableFrom(instance.getClass());
        }

        /**
         * Checks whether a Class type can be assigned to this type
         * 
         * @param type to check
         * @return True if assignable, False if not or type is <i>null</i>
         */
        public boolean isAssignableFrom(java.lang.Class<?> type) {
            return type != null && this.classType.isAssignableFrom(type);
        }
    }

    /**
     * Base class for objects that refer to a hidden type
     */
    public static class Handle implements StaticInitHelper.InitClass {
        protected Object instance;

        /**
         * Checks whether the backing raw type is an instance of a certain type of class
         * 
         * @param type to check (template type)
         * @return True if it is an instance, False if not
         */
        public final boolean isInstanceOf(Class<?> type) {
            return isInstanceOf(type.getType());
        }

        /**
         * Checks whether the backing raw type is an instance of a certain type of class
         * 
         * @param type to check
         * @return True if it is an instance, False if not
         */
        public final boolean isInstanceOf(java.lang.Class<?> type) {
            return type != null && type.isAssignableFrom(instance.getClass());
        }

        /**
         * Gets the raw instance backing this Handle
         * 
         * @return raw instance
         */
        public final Object getRaw() {
            return this.instance;
        }

        /**
         * Casts this handle to a different handle Class type.
         * If casting fails, an exception is thrown.
         * 
         * @param type template Class type to cast to
         * @return handle for the casted type
         */
        public <T extends Handle> T cast(Class<T> type) {
            if (this.isInstanceOf(type)) {
                return type.createHandle(this.instance);
            } else {
                throw new ClassCastException("Failed to cast handle of type " +
                        this.instance.getClass().getName() + " to " +
                        type.getType().getName());
            }
        }

        /**
         * Attempts to cast this handle to a different handle Class type.
         * If casting fails, null is returned instead.
         * 
         * @param type template Class type to cast to
         * @return handle for the casted type, null if casting fails
         */
        public <T extends Handle> T tryCast(Class<T> type) {
            if (this.isInstanceOf(type)) {
                return type.createHandle(this.instance);
            } else {
                return null;
            }
        }

        public static Handle createHandle(Object instance) {
            if (instance == null) return null;
            Handle h = new Handle();
            h.instance = instance;
            return h;
        }

        @InitMethod
        protected static final void initialize(final java.lang.Class<? extends Handle> handleType, String classPath) {
            try {
                // Load the class at the path and retrieve the Class Declaration belonging to it
                java.lang.Class<?> classType = Resolver.loadClass(classPath, false);
                if (classType == null) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Class " + classPath + " not found; Template '" +
                            handleType.getSimpleName() + " not initialized.");
                    return;
                }

                // Initialize the template class fields
                ((Class<?>) handleType.getField("T").get(null)).init(classType);
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to register " + handleType.getName(), t);
            }
        }

        @Override
        public final int hashCode() {
            return instance.hashCode();
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o instanceof Handle) {
                return ((Handle) o).instance.equals(this.instance);
            } else {
                return false;
            }
        }

        @Override
        public final String toString() {
            return instance.toString();
        }
    }

    // provides a default 'init' method to use when initializing the Template
    // all declared class element types must extend this type
    public static abstract class TemplateElement<T extends Declaration> {
        protected abstract T init(ClassDeclaration dec, String name);

        protected final <V> V failGetSafe(RuntimeException ex, V def) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get static field value", ex);
            return def;
        }
    }

    public static class AbstractField<T> extends TemplateElement<FieldDeclaration> {
        protected final FastField<T> field = new FastField<T>();

        @Override
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            for (FieldDeclaration fieldDec : dec.fields) {
                if (fieldDec.field != null && fieldDec.name.real().equals(name)) {
                    this.field.init(fieldDec.field);
                    return fieldDec;
                }
            }
            MountiplexUtil.LOGGER.warning("Field '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        /**
         * Turns this templated field into a reflection Field Accessor (legacy)
         * 
         * @return field accessor
         */
        public FieldAccessor<T> toFieldAccessor() {
            return new SafeField<T>(field);
        }
    }

    public static class AbstractMethod<T> extends TemplateElement<MethodDeclaration> {
        protected final FastMethod<T> method = new FastMethod<T>();

        // throws an exception when the method is not found
        protected final void failNotFound() {
            if (this.method.getMethod() == null) {
                throw new RuntimeException("Method not found");
            }
        }

        // throws an exception when arguments differ
        protected final void failInvalidArgs(Object[] arguments) {
            java.lang.Class<?>[] params = method.getMethod().getParameterTypes();
            if (params.length != arguments.length) {
                throw new IllegalArgumentException("Invalid number of argument specified! Expected " +
                        params.length + " arguments, but got " + arguments.length);
            }
            for (int i = 0; i < params.length; i++) {
                if (params[i].isPrimitive() && arguments[i] == null) {
                    throw new IllegalArgumentException("Null can not be assigned to primitive " +
                            params[i].getName() + " argument [" + i + "]");
                }
                if (arguments[i] != null && !params[i].isAssignableFrom(arguments[i].getClass())) {
                    throw new IllegalArgumentException("Value of type " + arguments[i].getClass().getName() +
                            " can not be assigned to " + params[i].getName() + " argument [" + i + "]");
                }
            }
        }

        @Override
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            for (MethodDeclaration methodDec : dec.methods) {
                if (methodDec.method != null && methodDec.name.real().equals(name)) {
                    this.method.init(methodDec.method);
                    return methodDec;
                }
            }
            MountiplexUtil.LOGGER.warning("Method '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        /**
         * Turns this templated method into a reflection Method Accessor (legacy)
         * 
         * @return method accessor
         */
        @SuppressWarnings("unchecked")
        public <R> MethodAccessor<R> toMethodAccessor() {
            return (MethodAccessor<R>) new SafeMethod<T>(this.method);
        }

        public java.lang.reflect.Method toJavaMethod() {
            return this.method.getMethod();
        }
    }

    public static class AbstractFieldConverter<F extends AbstractField<?>, T> extends TemplateElement<FieldDeclaration> {
        public final F raw;
        protected DuplexConverter<?, T> converter = null;

        protected AbstractFieldConverter(F raw) {
            this.raw = raw;
        }

        // throws an exception when the converter was not initialized
        protected final void failNoConverter() {
            if (converter == null) {
                throw new UnsupportedOperationException("Field converter was not found");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            FieldDeclaration fDec = this.raw.init(dec, name);
            if (fDec != null) {
                this.converter = (DuplexConverter<?, T>) Conversion.findDuplex(fDec.type, fDec.type.cast);
                if (this.converter == null) {
                    MountiplexUtil.LOGGER.warning("Converter for field " + fDec.name.toString() + 
                                                  " not found: " + fDec.type.toString());
                }
            }
            return fDec;
        }

        /**
         * Turns this templated converted field into a reflection translated Field Accessor (legacy)
         * 
         * @return field accessor
         */
        public TranslatorFieldAccessor<T> toFieldAccessor() {
            return new TranslatorFieldAccessor<T>(this.raw.toFieldAccessor(), this.converter);
        }
    }

    public static abstract class AbstractParamsConverter<R, T, D extends Declaration> extends TemplateElement<D> {
        public final R raw;
        protected Converter<?, T> resultConverter = null;
        protected Converter<?, ?>[] argConverters = null;
        protected boolean isConvertersInitialized = false;

        protected AbstractParamsConverter(R raw) {
            this.raw = raw;
        }

        protected final void failNoConverter() {
            if (!isConvertersInitialized) {
                throw new UnsupportedOperationException("Method converters could not be initialized");
            }
        }

        @SuppressWarnings("unchecked")
        protected final void initConverters(String name, TypeDeclaration returnType, ParameterListDeclaration parameters) {
            this.isConvertersInitialized = true;
            this.resultConverter = null;
            this.argConverters = null;

            // Initialize the converter for the return value
            if (returnType.cast != null) {
                this.resultConverter = (Converter<?, T>) Conversion.find(returnType, returnType.cast);
                if (this.resultConverter == null) {
                    this.isConvertersInitialized = false;
                    MountiplexUtil.LOGGER.warning("Converter for " + name + 
                            " return type not found: " + returnType.toString());
                }
            }

            // Converters for the arguments of the method
            ParameterDeclaration[] params = parameters.parameters;
            this.argConverters = new Converter<?, ?>[params.length];
            boolean hasArgumentConversion = false;
            for (int i = 0; i < params.length; i++) {
                if (params[i].type.cast != null) {
                    hasArgumentConversion = true;
                    this.argConverters[i] = Conversion.find(params[i].type.cast, params[i].type);
                    if (this.argConverters[i] == null) {
                        this.isConvertersInitialized = false;
                        MountiplexUtil.LOGGER.warning("Converter for " + name + 
                                " argument " + params[i].name.toString() + " not found: " + params[i].type.toString());
                    }
                }
            }
            if (!hasArgumentConversion) {
                this.argConverters = null;
            }
        }

        protected final void verifyConverterCount(int argCount) {
            if (this.argConverters != null) {
                if (this.argConverters.length != (argCount)) {
                    throw new IllegalArgumentException("Invalid number of arguments (" +
                            (this.argConverters.length - 1) + " expected, but got " + argCount + ")");
                }
            }
        }

        protected final Object convertArgument(int index, Object rawArgument) {
            if (this.argConverters[index] != null) {
                return this.argConverters[index].convert(rawArgument);
            } else {
                return rawArgument;
            }
        }

        @SuppressWarnings("unchecked")
        protected final T convertResult(Object result) {
            if (this.resultConverter != null) {
                return this.resultConverter.convert(result);
            } else {
                return (T) result;
            }
        }

        protected final Object[] convertArgs(Object[] arguments) {
            if (this.argConverters != null) {
                verifyConverterCount(arguments.length);

                // Got to convert the parameters
                Object[] convertedArgs = arguments.clone();
                for (int i = 0; i < convertedArgs.length; i++) {
                    if (this.argConverters[i] != null) {
                        convertedArgs[i] = this.argConverters[i].convert(convertedArgs[i]);
                    }
                }
                return convertedArgs;
            } else {
                return arguments;
            }
        }
    }

    public static class AbstractMethodConverter<M extends AbstractMethod<?>, T> extends AbstractParamsConverter<M, T, MethodDeclaration> {

        protected AbstractMethodConverter(M raw) {
            super(raw);
        }

        @Override
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            MethodDeclaration mDec = this.raw.init(dec, name);
            if (mDec != null) {
                initConverters("method " + mDec.name.toString(), mDec.returnType, mDec.parameters);
            }
            return mDec;
        }
    }

    public static final class Constructor<T> extends TemplateElement<ConstructorDeclaration> {
        protected java.lang.reflect.Constructor<?> constructor = null;

        /**
         * Constructs a new Instance of the class using this constructor.
         * 
         * @param args for the constructor
         * @return the constructed class instance
         */
        @SuppressWarnings("unchecked")
        public T newInstance(Object... args) {
            try {
                return (T) constructor.newInstance(args);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        // throws an exception when the method is not found
        protected final void failNotFound() {
            if (this.constructor == null) {
                throw new RuntimeException("Constructor not found");
            }
        }

        @Override
        protected ConstructorDeclaration init(ClassDeclaration dec, String name) {
            for (ConstructorDeclaration cDec : dec.constructors) {
                if (cDec.constructor != null && cDec.getName().equals(name)) {
                    try {
                        cDec.constructor.setAccessible(true);
                        this.constructor = cDec.constructor;
                        return cDec;
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.warning("Method '" + name + "' in template for " + dec.type.typePath + " not accessible");
                        return null;
                    }
                }
            }
            MountiplexUtil.LOGGER.warning("Constructor '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        public static final class Converted<T> extends AbstractParamsConverter<Constructor<Object>, T, ConstructorDeclaration> {

            public Converted() {
                super(new Constructor<Object>());
            }

            /**
             * Constructs a new Instance of the class using this constructor.
             * The input arguments and returned object are converted.
             * 
             * @param arguments for the constructor
             * @return the constructed class instance
             */
            public T newInstance(Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                    return null; // never reached
                }

                Object[] convArgs = convertArgs(arguments);
                Object rawInstance = raw.newInstance(convArgs);
                return convertResult(rawInstance);
            }

            @Override
            protected ConstructorDeclaration init(ClassDeclaration dec, String name) {
                ConstructorDeclaration cDec = this.raw.init(dec, name);
                if (cDec != null) {
                    initConverters("constructor " + cDec.parameters.toString(), cDec.type, cDec.parameters);
                }
                return cDec;
            }
        }
    }

    public static final class StaticMethod<T> extends AbstractMethod<T> {

        /**
         * Invokes this static method
         * 
         * @param arguments to pass along with the method
         * @return return value, null for void methods
         */
        public T invokeVA(Object... arguments) {
            return this.method.invokeVA(null, arguments);
        }

        public static final class Converted<T> extends AbstractMethodConverter<StaticMethod<Object>, T> {
 
            public Converted() {
                super(new StaticMethod<Object>());
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arguments to pass along with the method
             * @return return value, null for void methods
             */
            public final T invokeVA(Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                    return null; // never reached
                }

                Object[] convertedArgs = convertArgs(arguments);
                Object rawResult = this.raw.invokeVA(convertedArgs);
                return convertResult(rawResult);
            }
        }
    }

    public static final class Method<T> extends AbstractMethod<T> {

        /**
         * Invokes this method on the instance specified.
         * Uses variable arguments to allow for dynamic or >5 arguments to be used.
         * Note that this incurs a performance overhead.
         * 
         * @param instance to invoke the method on
         * @param arguments to pass along with the method
         * @return return value, null for void methods
         */
        public T invokeVA(Object instance, Object... arguments) {
            return this.method.invokeVA(instance, arguments);
        }

        /**
         * Invokes this method on the instance specified, with no method arguments.
         * 
         * @param instance to invoke the method on
         * @return return value, null for void methods
         */
        public T invoke(Object instance) {
            return this.method.invoke(instance);
        }

        /**
         * Invokes this method on the instance specified, with 1 method argument.
         * 
         * @param instance to invoke the method on
         * @param arg0 first argument
         * @return return value, null for void methods
         */
        public T invoke(Object instance, Object arg0) {
            return this.method.invoke(instance, arg0);
        }

        /**
         * Invokes this method on the instance specified, with 2 method arguments.
         * 
         * @param instance to invoke the method on
         * @param arg0 first argument
         * @param arg1 second argument
         * @return return value, null for void methods
         */
        public T invoke(Object instance, Object arg0, Object arg1) {
            return this.method.invoke(instance, arg0, arg1);
        }

        /**
         * Invokes this method on the instance specified, with 3 method arguments.
         * 
         * @param instance to invoke the method on
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @return return value, null for void methods
         */
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
            return this.method.invoke(instance, arg0, arg1, arg2);
        }

        /**
         * Invokes this method on the instance specified, with 4 method arguments.
         * 
         * @param instance to invoke the method on
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @return return value, null for void methods
         */
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
            return this.method.invoke(instance, arg0, arg1, arg2, arg3);
        }

        /**
         * Invokes this method on the instance specified, with 5 method arguments.
         * 
         * @param instance to invoke the method on
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @param arg4 fifth argument
         * @return return value, null for void methods
         */
        public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            return this.method.invoke(instance, arg0, arg1, arg2, arg3, arg4);
        }

        public static final class Converted<T> extends AbstractMethodConverter<Method<Object>, T> {
 
            public Converted() {
                super(new Method<Object>());
            }

            private final void verifyConverters(int argCount) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                }
                if (this.argConverters != null) {
                    if (this.argConverters.length != (argCount)) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                (this.argConverters.length - 1) + " expected, but got " + argCount + ")");
                    }
                }
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * Uses variable arguments to allow for dynamic or >5 arguments to be used.
             * Note that this incurs a performance overhead.
             * 
             * @param instance to invoke the method on
             * @param arguments to pass along with the method
             * @return return value, null for void methods
             */
            public final T invokeVA(Object instance, Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                }
                Object[] convertedArgs = convertArgs(arguments);
                Object rawResult = this.raw.invokeVA(instance, convertedArgs);
                return convertResult(rawResult);
            }

            /**
             * Invokes this method on the instance specified, performing
             * return type conversion as required. No arguments are passed on.
             * 
             * @param instance to invoke the method on
             * @return return value, null for void methods
             */
            public T invoke(Object instance) {
                verifyConverters(0);
                return convertResult(this.raw.invoke(instance));
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arg0 first argument
             * @return return value, null for void methods
             */
            public T invoke(Object instance, Object arg0) {
                verifyConverters(1);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(instance, arg0));
                } else {
                    return convertResult(this.raw.invoke(instance,
                            convertArgument(0, arg0)));
                }
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arg0 first argument
             * @param arg1 second argument
             * @return return value, null for void methods
             */
            public T invoke(Object instance, Object arg0, Object arg1) {
                verifyConverters(2);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(instance, arg0, arg1));
                } else {
                    return convertResult(this.raw.invoke(instance,
                            convertArgument(0, arg0),
                            convertArgument(1, arg1)));
                }
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @return return value, null for void methods
             */
            public T invoke(Object instance, Object arg0, Object arg1, Object arg2) {
                verifyConverters(3);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(instance, arg0, arg1, arg2));
                } else {
                    return convertResult(this.raw.invoke(instance,
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2)));
                }
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @return return value, null for void methods
             */
            public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3) {
                verifyConverters(4);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(instance, arg0, arg1, arg2, arg3));
                } else {
                    return convertResult(this.raw.invoke(instance,
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3)));
                }
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @param arg4 fifth argument
             * @return return value, null for void methods
             */
            public T invoke(Object instance, Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
                verifyConverters(5);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(instance, arg0, arg1, arg2, arg3, arg4));
                } else {
                    return convertResult(this.raw.invoke(instance,
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3),
                            convertArgument(4, arg4)));
                }
            }
        }
    }

    public static class EnumConstant<T extends Enum<?>> extends TemplateElement<FieldDeclaration> {
        protected T constant;

        public final T get() {
            if (constant == null) {
                throw new UnsupportedOperationException("Enumeration constant not initialized");
            }
            return constant;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            for (FieldDeclaration fDec : dec.fields) {
                if (fDec.isEnum && fDec.name.real().equals(name)) {
                    // Check if the class is initialized
                    if (dec.type.type == null) {
                        MountiplexUtil.LOGGER.warning("Enumeration constant " + name + " in class " +
                                dec.type + " not initialized: class not found");
                        return null;
                    }

                    // Find the enum constant
                    for (Object enumConstant : dec.type.type.getEnumConstants()) {
                        if (((Enum<?>) enumConstant).name().equals(fDec.name.value())) {
                            constant = (T) enumConstant;
                            return fDec;
                        }
                    }

                    // Not found, despite being declared
                    MountiplexUtil.LOGGER.warning("Enumeration constant " + name + " missing in class " + dec.type);
                    return null;
                }
            }
            MountiplexUtil.LOGGER.warning("Failed to find enumeration field " + name + " in class " + dec.type);
            return null;
        }

        public static final class Converted<T> extends TemplateElement<FieldDeclaration> {
            public final EnumConstant<Enum<?>> raw = new EnumConstant<Enum<?>>();
            protected DuplexConverter<?, T> converter = null;

            /**
             * Gets the converted enumeration constant
             * 
             * @return converted enumeration constant
             */
            public final T get() {
                Enum<?> value = raw.get();
                try {
                    return converter.convert(value);
                } catch (RuntimeException ex) {
                    failNoConverter();
                    throw ex;
                }
            }

            /**
             * Gets the converted enumeration constant, guaranteeing no exception is thrown.
             * This should be used when statically initializing constants.
             * 
             * @return converted enumeration constant, null on failure
             */
            public final T getSafe() {
                try {
                    return get();
                } catch (RuntimeException ex) {
                    return failGetSafe(ex, null);
                }
            }

            // throws an exception when the converter was not initialized
            protected final void failNoConverter() {
                if (converter == null) {
                    throw new UnsupportedOperationException("Enum constant converter was not found");
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            protected FieldDeclaration init(ClassDeclaration dec, String name) {
                FieldDeclaration fDec = raw.init(dec, name);
                if (fDec != null) {
                    this.converter = (DuplexConverter<?, T>) Conversion.findDuplex(fDec.type, fDec.type.cast);
                    if (this.converter == null) {
                        MountiplexUtil.LOGGER.warning("Converter for enum constant " + fDec.name.toString() + 
                                                      " not found: " + fDec.type.toString());
                    }
                }
                return fDec;
            }
        }
    }

    public static class StaticField<T> extends AbstractField<T> {

        /**
         * Gets the current static field value, guaranteeing to never throw an exception.
         * This should be used when reading static fields during static initialization blocks.
         * 
         * @return static field value, null on failure
         */
        public final T getSafe() {
            try{return get();}catch(RuntimeException ex){return failGetSafe(ex, null);}
        }

        /**
         * Gets the current static field value.
         * 
         * @return static field value
         */
        public final T get() {
            return field.reader.get(null);
        }

        /**
         * Sets the static field to a new value
         * 
         * @param value to set to
         */
        public final void set(T value) {
            field.writer.set(null, value);
        }

        /**
         * Converts an internal static field's value on the fly so it can be exposed with an available type.
         * 
         * @param <T> converted type
         */
        public static final class Converted<T> extends AbstractFieldConverter<StaticField<Object>, T> {
 
            public Converted() {
                super(new StaticField<Object>());
            }

            /**
             * Reads the static field value and converts it to the correct exposed type,
             * guaranteeing to never throw an exception.
             * This should be used when reading static fields during static initialization blocks.
             * 
             * @return converted static field value, null on failure
             */
            public final T getSafe() {
                try {return get();}catch(RuntimeException ex){return raw.failGetSafe(ex, null);}
            }

            /**
             * Reads the static field value and converts it to the correct exposed type
             * 
             * @return converted static field value
             */
            public final T get() {
                Object value = raw.get();
                try {
                    return converter.convert(value);
                } catch (RuntimeException t) {
                    failNoConverter();
                    throw t;
                }
            }

            /**
             * Converts the value to the correct built-in type and sets the static field value
             * 
             * @param value to convert and set the static field to
             */
            public final void set(T value) {
                Object rawValue;
                try {
                    rawValue = converter.convertReverse(value);
                } catch (RuntimeException t) {
                    raw.field.checkInit();
                    failNoConverter();
                    throw t;
                }
                raw.set(rawValue);
            }
        }

        /* ========================================================================================== */
        /* ================= Please don't look at the copy-pasted code down below =================== */
        /* ========================================================================================== */

        public static final class Double extends StaticField<java.lang.Double> {
            /** @see StaticField#getSafe() */
            public final double getDoubleSafe() {
                try{return getDouble();}catch(RuntimeException ex){return failGetSafe(ex, 0.0);}
            }

            /** @see StaticField#get() */
            public final double getDouble() {
                return field.reader.getDouble(null);
            }

            /** @see StaticField#set(value) */
            public final void setDouble(double value) {
                field.writer.setDouble(null, value);
            }
        }

        public static final class Float extends StaticField<java.lang.Float> {
            /** @see StaticField#getSafe() */
            public final float getFloatSafe() {
                try{return getFloat();}catch(RuntimeException ex){return failGetSafe(ex, 0.0f);}
            }

            /** @see StaticField#get() */
            public final float getFloat() {
                return field.reader.getFloat(null);
            }

            /** @see StaticField#set(value) */
            public final void setFloat(float value) {
                field.writer.setFloat(null, value);
            }
        }

        public static final class Byte extends StaticField<java.lang.Byte> {
            /** @see StaticField#getSafe() */
            public final byte getByteSafe() {
                try{return getByte();}catch(RuntimeException ex){return failGetSafe(ex, (byte) 0);}
            }

            /** @see StaticField#get() */
            public final byte getByte() {
                return field.reader.getByte(null);
            }

            /** @see StaticField#set(value) */
            public final void setByte(byte value) {
                field.writer.setByte(null, value);
            }
        }

        public static final class Short extends StaticField<java.lang.Short> {
            /** @see StaticField#getSafe() */
            public final short getShortSafe() {
                try{return getShort();}catch(RuntimeException ex){return failGetSafe(ex, (short) 0);}
            }

            /** @see StaticField#get() */
            public final short getShort() {
                return field.reader.getShort(null);
            }

            /** @see StaticField#set(value) */
            public final void setShort(short value) {
                field.writer.setShort(null, value);
            }
        }

        public static final class Integer extends StaticField<java.lang.Integer> {
            /** @see StaticField#getSafe() */
            public final int getIntegerSafe() {
                try{return getInteger();}catch(RuntimeException ex){return failGetSafe(ex, 0);}
            }

            /** @see StaticField#get() */
            public final int getInteger() {
                return field.reader.getInteger(null);
            }

            /** @see StaticField#set(value) */
            public final void setInteger(int value) {
                field.writer.setInteger(null, value);
            }
        }

        public static final class Long extends StaticField<java.lang.Long> {
            /** @see StaticField#getSafe() */
            public final long getLongSafe() {
                try{return getLong();}catch(RuntimeException ex){return failGetSafe(ex, 0L);}
            }

            /** @see StaticField#get() */
            public final long getLong() {
                return field.reader.getLong(null);
            }

            /** @see StaticField#set(value) */
            public final void setLong(long value) {
                field.writer.setLong(null, value);
            }
        }

        public static final class Character extends StaticField<java.lang.Character> {
            /** @see StaticField#getSafe() */
            public final char getCharacterSafe() {
                try{return getCharacter();}catch(RuntimeException ex){return failGetSafe(ex, '\0');}
            }

            /** @see StaticField#get() */
            public final char getCharacter() {
                return field.reader.getCharacter(null);
            }

            /** @see StaticField#set(value) */
            public final void setCharacter(char value) {
                field.writer.setCharacter(null, value);
            }
        }

        public static final class Boolean extends StaticField<java.lang.Boolean> {
            /** @see StaticField#getSafe() */
            public final boolean getBooleanSafe() {
                try{return getBoolean();}catch(RuntimeException ex){return failGetSafe(ex, false);}
            }

            /** @see StaticField#get() */
            public final boolean getBoolean() {
                return field.reader.getBoolean(null);
            }

            /** @see StaticField#set(value) */
            public final void setBoolean(boolean value) {
                field.writer.setBoolean(null, value);
            }
        }
    }

    public static class Field<T> extends AbstractField<T> {

        /**
         * Gets the field value from an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to get the field value for, <i>null</i> for static fields
         * @return field value
         */
        public final T get(Object instance) {
            return this.field.reader.get(instance);
        }

        /**
         * Sets the field value for an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to set the field value for, <i>null</i> for static fields
         * @param value to set to
         */
        public final void set(Object instance, T value) {
            this.field.writer.set(instance, value);
        }

        /**
         * Efficiently copies the field value from one instance to another.
         * 
         * @param instanceFrom to get the value
         * @param instanceTo to set the value
         */
        public void copy(Object instanceFrom, Object instanceTo) {
            this.field.copier.copy(instanceFrom, instanceTo);
        }

        /*
        protected final RuntimeException failGet(Throwable t, Object instance) {
            this.field.writer.checkCanWrite();
            this.field.checkInstance(instance);
            return new RuntimeException("Failed to get field", t);
        }

        protected final RuntimeException failSet(Throwable t, Object instance, Object value) {
            this.field.checkSet(value);
            this.field.checkInstance(instance);
            return new RuntimeException("Failed to set field", t);
        }

        protected final RuntimeException failCopy(Throwable t, Object instanceFrom, Object instanceTo) {
            this.field.checkInit();
            this.field.checkInstance(instanceFrom);
            this.field.checkInstance(instanceTo);
            return new RuntimeException("Failed to copy", t);
        }
        */

        /**
         * Converts an internal field's value on the fly so it can be exposed with an available type.
         * 
         * @param <T> converted type
         */
        public static final class Converted<T> extends AbstractFieldConverter<Field<Object>, T> {

            public Converted() {
                super(new Field<Object>());
            }

            /**
             * Reads the field value from an instance and converts it to the correct exposed type
             * 
             * @param instance to get the field from
             * @return converted field value
             */
            public final T get(Object instance) {
                Object rawValue = raw.get(instance);
                try {
                    return converter.convert(rawValue);
                } catch (RuntimeException t) {
                    failNoConverter();
                    throw t;
                }
            }

            /**
             * Converts the value to the correct built-in type and sets the field value for an instance
             * 
             * @param instance to set the field for
             * @param value to convert and set the field to
             */
            public final void set(Object instance, T value) {
                Object rawValue;
                try {
                    rawValue = converter.convertReverse(value);
                } catch (RuntimeException t) {
                    raw.field.checkInit();
                    failNoConverter();
                    throw t;
                }
                raw.set(instance, rawValue);
            }

            /**
             * Copies the field value from one instance to another. No conversion is performed.
             * 
             * @param instanceFrom to get the field value from
             * @param instanceTo to set the field value for
             */
            public final void copy(Object instanceFrom, Object instanceTo) {
                raw.copy(instanceFrom, instanceTo);
            }
        }

        /* ========================================================================================== */
        /* ================= Please don't look at the copy-pasted code down below =================== */
        /* ========================================================================================== */

        public static final class Double extends Field<java.lang.Double> {
            /** @see Field#get(instance) */
            public final double getDouble(Object instance) {
                return field.reader.getDouble(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setDouble(Object instance, double value) {
                field.writer.setDouble(instance, value);
            }
        }

        public static final class Float extends Field<java.lang.Float> {
            /** @see Field#get(instance) */
            public final float getFloat(Object instance) {
                return field.reader.getFloat(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setFloat(Object instance, float value) {
                field.writer.setFloat(instance, value);
            }
        }

        public static final class Byte extends Field<java.lang.Byte> {
            /** @see Field#get(instance) */
            public final byte getByte(Object instance) {
                return field.reader.getByte(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setByte(Object instance, byte value) {
                field.writer.setByte(instance, value);
            }
        }

        public static final class Short extends Field<java.lang.Short> {
            /** @see Field#get(instance) */
            public final short getShirt(Object instance) {
                return field.reader.getShort(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setShort(Object instance, short value) {
                field.writer.setShort(instance, value);
            }
        }

        public static final class Integer extends Field<java.lang.Integer> {
            /** @see Field#get(instance) */
            public final int getInteger(Object instance) {
                return field.reader.getInteger(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setInteger(Object instance, int value) {
                field.writer.setInteger(instance, value);
            }
        }

        public static final class Long extends Field<java.lang.Long> {
            /** @see Field#get(instance) */
            public final long getLong(Object instance) {
                return field.reader.getLong(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setLong(Object instance, long value) {
                field.writer.setLong(instance, value);
            }
        }

        public static final class Character extends Field<java.lang.Character> {
            /** @see Field#get(instance) */
            public final char getCharacter(Object instance) {
                return field.reader.getCharacter(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setCharacter(Object instance, char value) {
                field.writer.setCharacter(instance, value);
            }
        }

        public static final class Boolean extends Field<java.lang.Boolean> {
            /** @see Field#get(instance) */
            public final boolean getBoolean(Object instance) {
                return field.reader.getBoolean(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setBoolean(Object instance, boolean value) {
                field.writer.setBoolean(instance, value);
            }
        }
    }

}
