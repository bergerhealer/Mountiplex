package com.bergerkiller.mountiplex.reflection.declarations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.logging.Level;

import org.objenesis.ObjenesisHelper;
import org.objenesis.instantiator.ObjectInstantiator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.type.DisabledConverter;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.type.NullConverter;
import com.bergerkiller.mountiplex.reflection.FieldAccessor;
import com.bergerkiller.mountiplex.reflection.IgnoredFieldAccessor;
import com.bergerkiller.mountiplex.reflection.MethodAccessor;
import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.SafeField;
import com.bergerkiller.mountiplex.reflection.SafeMethod;
import com.bergerkiller.mountiplex.reflection.TranslatorFieldAccessor;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastConstructor;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper.InitMethod;

public class Template {

    public static class Class<H extends Handle> implements LazyInitializedObject {
        private boolean valid = false;
        private java.lang.Class<?> classType = null;
        private DuplexConverter<Object, H> handleConverter = null;
        private final java.lang.Class<H> handleType;
        private TemplateHandleBuilder<H> handleBuilder = null;
        private ObjectInstantiator<Object> instantiator = null;
        private String classPath = null;
        private TemplateElement<?>[] elements = new TemplateElement<?>[0];
        private FastField<?>[] fields = null;
        private ClassDeclaration classDec = null;

        @SuppressWarnings("unchecked")
        public Class() {
            this.handleType = (java.lang.Class<H>) getClass().getDeclaringClass();
        }

        /**
         * Creates a new Handle instance suitable for this Template Class type.
         * If the instance is null, null is returned.
         * 
         * @param instance to create a handle for
         * @return handle
         */
        public final H createHandle(Object instance) {
            return createHandle(instance, false);
        }

        /**
         * Creates a new Handle instance suitable for this Template Class type.
         * If the instance is null and allowNullInstance is false, null is returned.
         * 
         * @param instance to create a handle for
         * @param allowNullInstance whether an internal null instance is allowed
         * @return handle
         */
        public final H createHandle(Object instance, boolean allowNullInstance) {
            if (instance == null && !allowNullInstance) {
                return null;
            }
            if (this.handleBuilder == null) {
                synchronized (this) {
                    if (this.handleBuilder == null) {
                        TemplateHandleBuilder<H> builder = new TemplateHandleBuilder<H>(this.handleType);
                        builder.build();
                        if (this.handleBuilder == null) {
                            this.handleBuilder = builder;
                        }
                    }
                }
            }
            return this.handleBuilder.create(instance);
        }

        /**
         * Creates a new instance of this Class Type without calling any constructors.
         * All member fields will be initialized to their default values (null, 0, false, etc.).
         * The object will be returned wrapped in a Handle for this Class Type.
         * 
         * @return Handle to an uninitialized object of this Class Type
         */
        public final H newHandleNull() {
            return createHandle(newInstanceNull());
        }

        /**
         * Creates a new instance of this Class Type without calling any constructors.
         * All member fields will be initialized to their default values (null, 0, false, etc.).
         * 
         * @return Uninitialized object of this Class Type
         */
        @SuppressWarnings("unchecked")
        public final Object newInstanceNull() {
            if (this.classType == null) {
                throw new IllegalStateException("Class " + classPath + " is not available");
            }
            if (this.instantiator == null) {
                synchronized (this) {
                    if (this.instantiator == null) {
                        this.instantiator = (ObjectInstantiator<Object>) ObjenesisHelper.getInstantiatorOf(this.classType);
                        if (this.instantiator == null) {
                            throw new IllegalStateException("Class of type " + this.classType.getName() + " could not be instantiated");
                        }
                    }
                }
            }
            try {
                return this.instantiator.newInstance();
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        }

        @Override
        public final void forceInitialization() {
            if (this.isAvailable()) {
                for (TemplateElement<?> element : this.elements) {
                    if (!element.isOptional() || element.isAvailable()) {
                        element.forceInitialization();
                    }
                }
            }
        }

        private final void init(java.lang.Class<?> classType, ClassDeclarationResolver classDecResolver) {
            this.classType = classType;
            this.valid = (classType != null);

            // Create duplex converter between handle type and instance type
            if (this.valid) {
                this.handleConverter = new DuplexConverter<Object, H>(classType, this.handleType) {
                    @Override
                    public H convertInput(Object value) {
                        return createHandle(value);
                    }

                    @Override
                    public Object convertOutput(H value) {
                        return value.getRaw();
                    }
                };
                Conversion.registerConverter(this.handleConverter);

                // Resolve class declaration
                if (classDecResolver != null) {
                    this.classDec = Resolver.resolveClassDeclaration(this.classPath, classType);
                } else {
                    this.classDec = Resolver.resolveClassDeclaration(this.classPath, classType);
                }
                if (this.classDec == null) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Class Declaration for " + this.classType.getName() + " not found");
                    valid = false;
                }

                // Execute bootstrap code
                this.classDec.getResolver().runBootstrap();
            }

            // Initialize all declared fields
            boolean fieldsSuccessful = true;
            ArrayList<TemplateElement<?>> elementsList = new ArrayList<TemplateElement<?>>();
            for (java.lang.reflect.Field templateFieldRef : getClass().getFields()) {
                String templateFieldName = templateFieldRef.getName();
                try {
                    Object templateField = templateFieldRef.get(this);
                    if (templateField instanceof TemplateElement) {
                        TemplateElement<?> element = (TemplateElement<?>) templateField;
                        element.initElementName(this.classPath + "." + templateFieldName);
                        elementsList.add(element);
                        if (templateFieldRef.getAnnotation(Optional.class) != null) {
                            element.setOptional();
                        }
                        if (templateFieldRef.getAnnotation(Readonly.class) != null) {
                            element.setReadonly();
                        }
                        if (valid) {
                            Object result = element.init(this.classDec, templateFieldName);
                            if (result == null && !element._optional) {
                                fieldsSuccessful = false;
                            }
                        } else {
                            element.initNoClass();
                        }
                    }
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize template field " +
                        "'" + templateFieldName + "' in " + classType.getName(), t);
                    fieldsSuccessful = false;
                }
            }
            this.valid &= fieldsSuccessful;
            this.elements = MountiplexUtil.toArray(elementsList, TemplateElement.class);
        }

        /**
         * Gets whether the Class pointed to by this Class Template is available. If this is an optional
         * class declaration, this can be used to detect whether it can be used.
         * 
         * @return True if the class is available, False if not
         */
        public final boolean isAvailable() {
            return this.classType != null;
        }

        /**
         * Gets whether this entire Class Template has been initialized without any errors
         * 
         * @return True if loaded successfully, False if errors had occurred
         */
        public final boolean isValid() {
            return valid;
        }

        /**
         * Gets whether this entire Class Template is declared {@link Optional}.
         * If this returns True it indicates the class is not guaranteed to exist at runtime.
         * 
         * @return True if optional, False if it is guaranteed to exist
         */
        public final boolean isOptional() {
            return this.handleType.getAnnotation(Optional.class) != null; 
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
         * Gets the Class Declaration that was used to initialize this template
         * 
         * @return class declaration
         */
        public ClassDeclaration getClassDeclaration() {
            return this.classDec;
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
            return instance != null && this.classType != null && this.classType.equals(instance.getClass());
        }

        /**
         * Checks whether a type equals this type
         * 
         * @param type to check
         * @return True if equals, False if not or null
         */
        public boolean isType(java.lang.Class<?> type) {
            return this.classType != null && this.classType.equals(type);
        }

        /**
         * Checks whether a type stored in a Handle equals this type
         * 
         * @param handle to check
         * @return True if handle is not null and stores an instance of this type, False if not
         */
        public boolean isHandleType(Handle handle) {
            return handle != null && this.isType(handle.getRaw());
        }

        /**
         * Checks whether an Object value can be assigned to this type
         * @param instance to check
         * @return True if assignable, False if not or instance is <i>null</i>
         */
        public boolean isAssignableFrom(Object instance) {
            return instance != null && this.classType != null && this.classType.isAssignableFrom(instance.getClass());
        }

        /**
         * Checks whether a Class type can be assigned to this type
         * 
         * @param type to check
         * @return True if assignable, False if not or type is <i>null</i>
         */
        public boolean isAssignableFrom(java.lang.Class<?> type) {
            return type != null && this.classType != null && this.classType.isAssignableFrom(type);
        }

        /**
         * Copies all local field values from the instance of one handle to another.
         * If any of the handles or their instances is null, no copy is performed.
         * 
         * @param handleFrom
         * @param handleTo
         */
        public void copyHandle(H handleFrom, H handleTo) {
            if (handleFrom == null || handleTo == null) {
                return;
            }
            copy(handleFrom.getRaw(), handleTo.getRaw());
        }

        /**
         * Copies all local field values from one instance to another.
         * If any of the instances are null, no copy is performed.
         * 
         * @param instanceFrom
         * @param instanceTo
         */
        public void copy(Object instanceFrom, Object instanceTo) {
            if (this.fields == null) {
                ArrayList<FastField<?>> fields = new ArrayList<FastField<?>>();
                if (this.classType != null) {
                    ReflectionUtil.fillFastFields(fields, this.classType);
                }
                this.fields = fields.toArray(new FastField<?>[fields.size()]);
            }
            for (FastField<?> field : this.fields) {
                field.copy(instanceFrom, instanceTo);
            }
        }

    }

    /**
     * Base class for objects that refer to a hidden type
     */
    public static abstract class Handle implements StaticInitHelper.InitClass {
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
            return type != null && type.isAssignableFrom(getRaw().getClass());
        }

        /**
         * Gets the raw instance backing this Handle
         * 
         * @return raw instance
         */
        public Object getRaw() {
            return null;
        }

        /**
         * Gets the raw instance backing this Handle.
         * If this Handle is <i>null</i>, <i>null</i> is returned safely.
         * 
         * @param handle to get the raw instance from
         * @return raw instance
         */
        public static final Object getRaw(Handle handle) {
            return (handle == null) ? null : handle.getRaw();
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
                return type.createHandle(this.getRaw());
            } else {
                throw new ClassCastException("Failed to cast handle of type " +
                        this.getRaw().getClass().getName() + " to " +
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
                return type.createHandle(this.getRaw());
            } else {
                return null;
            }
        }

        public static Handle createHandle(final Object instance) {
            if (instance == null) {
                return null;
            }
            return new Handle() {
                @Override
                public Object getRaw() {
                    return instance;
                }
            };
        }

        @InitMethod
        protected static final void initialize(final java.lang.Class<? extends Handle> handleType, String classPath, ClassDeclarationResolver resolver) {
            try {
                Class<?> handleClass = ((Class<?>) handleType.getField("T").get(null));
                handleClass.classPath = classPath;

                // Load the class at the path and retrieve the Class Declaration belonging to it
                java.lang.Class<?> classType = Resolver.loadClass(classPath, false);
                if (classType == null) {
                    if (!handleClass.isOptional()) {
                        MountiplexUtil.LOGGER.log(Level.SEVERE, "Class " + classPath + " not found; Template '" +
                                handleType.getSimpleName() + " not initialized.");
                    }
                }

                // Initialize the template class fields
                handleClass.init(classType, resolver);
            } catch (Throwable t) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to register " + handleType.getName(), t);
            }
        }

        @Override
        public final int hashCode() {
            return getRaw().hashCode();
        }

        @Override
        public final boolean equals(Object o) {
            if (this.getRaw() == null) {
                if (o == null) {
                    return true;
                } else if (o instanceof Handle) {
                    return ((Handle) o).getRaw() == null;
                } else {
                    return false;
                }
            }
            if (this == o) {
                return true;
            } else if (o instanceof Handle) {
                return ((Handle) o).getRaw().equals(this.getRaw());
            } else {
                return false;
            }
        }

        @Override
        public final String toString() {
            return getRaw().toString();
        }
    }

    // provides a default 'init' method to use when initializing the Template
    // all declared class element types must extend this type
    public static abstract class TemplateElement<T extends Declaration> implements LazyInitializedObject {
        private boolean _optional = false;
        private boolean _readonly = false;
        protected boolean _hasClass = true;
        private String _elementName = "!!UNKNOWN!!";

        /**
         * Initializes the template element for when the containing Class is not available
         */
        protected void initNoClass() {
            _hasClass = false;
        }

        protected abstract T init(ClassDeclaration dec, String name);

        /**
         * Sets the name of this element during initialization
         * 
         * @param elementName to set to
         */
        protected void initElementName(String elementName) {
            this._elementName = elementName;
        }

        /**
         * Gets the name of this Template Element as declared in the original Template Class.
         * This name helps as a description of the element for debug messages.
         * 
         * @return template element name
         */
        public final String getElementName() {
            return this._elementName;
        }

        /**
         * Gets whether this Class element is initialized. If it is not initialized,
         * it indicates it is not available. This should always be checked when accessing
         * {@link #isOptional()} elements.
         * 
         * @return True if initialized, False if not
         */
        public abstract boolean isAvailable();

        protected final <V> V failGetSafe(RuntimeException ex, V def) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get static field value", ex);
            return def;
        }

        // verifies all parameters are correct, comparing values with the param Class types
        protected final void failInvalidArgs(java.lang.Class<?>[] paramTypes, Object[] arguments) {
            if (paramTypes.length != arguments.length) {
                throw new IllegalArgumentException("Invalid number of argument specified! Expected " +
                        paramTypes.length + " arguments, but got " + arguments.length);
            }
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].isPrimitive() && arguments[i] == null) {
                    throw new IllegalArgumentException("Null can not be assigned to primitive " +
                            paramTypes[i].getName() + " argument [" + i + "]");
                }
                if (arguments[i] != null && !BoxedType.tryBoxType(paramTypes[i]).isAssignableFrom(arguments[i].getClass())) {
                    throw new IllegalArgumentException("Value of type " + arguments[i].getClass().getName() +
                            " can not be assigned to " + paramTypes[i].getName() + " argument [" + i + "]");
                }
            }
        }

        protected final void initFail(String message) {
            if (!isOptional()) {
                MountiplexUtil.LOGGER.warning(message);
            }
        }

        protected void setOptional() {
            this._optional = true;
        }

        protected void setReadonly() {
            _readonly = true;
        }

        /**
         * Gets whether this Class element is optional, and could possibly not exist at runtime
         * 
         * @return True if optional, False if not
         */
        public boolean isOptional() {
            return this._optional;
        }

        /**
         * Gets whether this Class element is readonly, and only read access is allowed.
         * Setter methods will fail at runtime.
         * 
         * @return True if readonly, False if not
         */
        public boolean isReadonly() {
            return this._readonly;
        }
    }

    public static class AbstractField<T> extends TemplateElement<FieldDeclaration> {
        protected final FastField<T> field = new FastField<T>();

        @Override
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            for (FieldDeclaration fieldDec : dec.fields) {
                if (fieldDec.field != null && fieldDec.name.real().equals(name)) {
                    this.field.init(fieldDec.field);
                    return fieldDec;
                }
            }
            initFail("Field '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        @Override
        public void forceInitialization() {
            field.forceInitialization();
        }

        @Override
        protected void initElementName(String elementName) {
            super.initElementName(elementName);
            this.field.initUnavailable(elementName); // makes sure its logged correctly
        }

        @Override
        public boolean isAvailable() {
            return field.getField() != null;
        }

        /**
         * Turns this templated field into a reflection Field Accessor (legacy)
         * 
         * @return field accessor
         */
        public FieldAccessor<T> toFieldAccessor() {
            if (this.isAvailable()) {
                return new SafeField<T>(field);
            } else {
                // Returns a dummy field accessor that throws exceptions when used
                return new FieldAccessor<T>() {
                    @Override
                    public boolean isValid() {
                        return false;
                    }

                    private UnsupportedOperationException fail() {
                        return new UnsupportedOperationException("Field " + field.getDescription() + " is not available");
                    }

                    public T get(Object instance) { throw fail(); }
                    public boolean set(Object instance, T value) { throw fail(); }
                    public T transfer(Object from, Object to) { throw fail(); }

                    @Override
                    public <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
                        return new TranslatorFieldAccessor<K>(this, converterPair);
                    }

                    @Override
                    public FieldAccessor<T> ignoreInvalid(T defaultValue) {
                        return new IgnoredFieldAccessor<T>(defaultValue);
                    }
                };
            }
        }
    }

    public static class AbstractMethod<T> extends TemplateElement<MethodDeclaration> {
        protected final FastMethod<T> method = new FastMethod<T>();

        // throws an exception when the method is not found
        protected final void failNotFound() {
            if (!this.method.isAvailable()) {
                throw new RuntimeException("Method " + this.getElementName() + " not found");
            }
        }

        // throws an exception when arguments differ
        protected final void failInvalidArgs(Object[] arguments) {
            failInvalidArgs(method.getMethod().getParameterTypes(), arguments);
        }

        @Override
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            for (MethodDeclaration methodDec : dec.methods) {
                if ((methodDec.method != null || methodDec.body != null) && methodDec.name.real().equals(name)) {
                    this.method.init(methodDec);
                    return methodDec;
                }
            }
            initFail("Method '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        @Override
        public void forceInitialization() {
            method.forceInitialization();
            failNotFound();
        }

        @Override
        protected void initElementName(String elementName) {
            super.initElementName(elementName);
            method.initUnavailable(elementName); // makes sure its logged correctly
        }

        @Override
        public boolean isAvailable() {
            return method.isAvailable();
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
                throw new UnsupportedOperationException("Field converter for " + this.getElementName() + " was not found");
            }
        }

        @Override
        public void forceInitialization() {
            raw.forceInitialization();
            failNoConverter();
        }

        @Override
        protected void initElementName(String elementName) {
            super.initElementName(elementName);
            raw.initElementName(elementName);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            FieldDeclaration fDec = this.raw.init(dec, name);
            if (fDec != null) {
                Converter<?, T> conv_a = null;
                Converter<T, ?> conv_b = null;
                if (this.isReadonly()) {
                    conv_a = (Converter<?, T>) Conversion.find(fDec.type, fDec.type.cast);
                    conv_b = new DisabledConverter<T, Object>(fDec.type.cast, fDec.type, "Field " + name + " is readonly");
                } else {
                    conv_a = (Converter<?, T>) Conversion.find(fDec.type, fDec.type.cast);
                    conv_b = (Converter<T, ?>) Conversion.find(fDec.type.cast, fDec.type);
                }
                if (conv_a == null) {
                    initFail("Converter for field " + fDec.name.toString() + 
                            " not found: " + fDec.type.toString(true) + " -> " + fDec.type.cast.toString(true));
                    return null;
                } else if (conv_b == null) {
                    initFail("Converter for field " + fDec.name.toString() + 
                            " not found: " + fDec.type.cast.toString(true) + " -> " + fDec.type.toString(true));
                    return null;
                } else {
                    this.converter = DuplexConverter.pair(conv_a, conv_b);
                }
            }
            return fDec;
        }

        @Override
        public boolean isAvailable() {
            return raw.isAvailable() && converter != null;
        }

        @Override
        protected void setOptional() {
            super.setOptional();
            raw.setOptional();
        }

        /**
         * Turns this templated converted field into a reflection translated Field Accessor (legacy)
         * 
         * @return field accessor
         */
        public TranslatorFieldAccessor<T> toFieldAccessor() {
            DuplexConverter<?, T> converter;
            if (this.isAvailable()) {
                converter = this.converter;
            } else {
                // This is here so it doesn't error out in the constructor
                converter = DuplexConverter.createNull(TypeDeclaration.fromClass(Object.class));
            }
            return new TranslatorFieldAccessor<T>(this.raw.toFieldAccessor(), converter);
        }
    }

    public static abstract class AbstractParamsConverter<R extends TemplateElement<?>, T, D extends Declaration> extends TemplateElement<D> {
        public final R raw;
        protected Converter<?, T> resultConverter = null;
        protected Converter<?, ?>[] argConverters = null;
        protected boolean isConvertersInitialized = false;

        protected AbstractParamsConverter(R raw) {
            this.raw = raw;
        }

        protected final void failNoConverter() {
            if (!isConvertersInitialized) {
                throw new UnsupportedOperationException("Method converters for " + this.getElementName() + " could not be initialized");
            }
        }

        @Override
        public void forceInitialization() {
            raw.forceInitialization();
            failNoConverter();
        }

        @Override
        protected void initElementName(String elementName) {
            super.initElementName(elementName);
            raw.initElementName(elementName);
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
                } else if (this.resultConverter instanceof NullConverter) {
                    this.resultConverter = null;
                }
            }

            // Converters for the arguments of the method
            ParameterDeclaration[] params = parameters.parameters;
            this.argConverters = new Converter<?, ?>[params.length];
            boolean hasArgumentConversion = false;
            for (int i = 0; i < params.length; i++) {
                if (params[i].type.cast != null) {
                    this.argConverters[i] = Conversion.find(params[i].type.cast, params[i].type);
                    if (this.argConverters[i] == null) {
                        this.isConvertersInitialized = false;
                        MountiplexUtil.LOGGER.warning("Converter for " + name + 
                                " argument " + params[i].name.toString() + " not found: " + params[i].type.toString());
                    } else if (this.argConverters[i] instanceof NullConverter) {
                        this.argConverters[i] = null;
                    } else {
                        // It's ok
                        hasArgumentConversion = true;
                    }
                }
            }
            if (!hasArgumentConversion) {
                this.argConverters = null;
            }
        }

        @Override
        protected void setOptional() {
            super.setOptional();
            raw.setOptional();
        }

        @Override
        public boolean isAvailable() {
            return raw.isAvailable();
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
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            MethodDeclaration mDec = this.raw.init(dec, name);
            if (mDec != null) {
                initConverters("method " + mDec.name.toString(), mDec.returnType, mDec.parameters);
            }
            return mDec;
        }
    }

    public static final class Constructor<T> extends TemplateElement<ConstructorDeclaration> {
        protected final FastConstructor<T> constructor = new FastConstructor<T>();

        /**
         * Creates a new instance
         * 
         * @param arguments to pass along with the method
         * @return created instance
         */
        public T newInstanceVA(Object... arguments) {
            return this.constructor.newInstanceVA(arguments);
        }

        /**
         * Creates a new instance, with no method arguments.
         * 
         * @return created instance
         */
        public T newInstance() {
            return this.constructor.newInstance();
        }

        /**
         * Creates a new instance, with 1 method argument.
         * 
         * @param arg0 first argument
         * @return created instance
         */
        public T newInstance(Object arg0) {
            return this.constructor.newInstance(arg0);
        }

        /**
         * Creates a new instance, with 2 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @return created instance
         */
        public T newInstance(Object arg0, Object arg1) {
            return this.constructor.newInstance(arg0, arg1);
        }

        /**
         * Creates a new instance, with 3 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @return created instance
         */
        public T newInstance(Object arg0, Object arg1, Object arg2) {
            return this.constructor.newInstance(arg0, arg1, arg2);
        }

        /**
         * Creates a new instance, with 4 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @return created instance
         */
        public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
            return this.constructor.newInstance(arg0, arg1, arg2, arg3);
        }

        /**
         * Creates a new instance, with 5 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @param arg4 fifth argument
         * @return created instance
         */
        public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            return this.constructor.newInstance(arg0, arg1, arg2, arg3, arg4);
        }

        @Override
        public void forceInitialization() {
            this.constructor.forceInitialization();
        }

        @Override
        public boolean isAvailable() {
            return constructor.isAvailable();
        }

        // throws an exception when the method is not found
        protected final void failNotFound() {
            if (!this.constructor.isAvailable()) {
                throw new RuntimeException("Constructor not found");
            }
        }

        // throws an exception when arguments differ
        protected final void failInvalidArgs(Object[] arguments) {
            failInvalidArgs(constructor.getConstructor().getParameterTypes(), arguments);
        }

        @Override
        protected ConstructorDeclaration init(ClassDeclaration dec, String name) {
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            for (ConstructorDeclaration cDec : dec.constructors) {
                if (cDec.constructor != null && cDec.getName().equals(name)) {
                    try {
                        this.constructor.init(cDec);
                        return cDec;
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.warning("Method '" + name + "' in template for " + dec.type.typePath + " not accessible");
                        return null;
                    }
                }
            }
            initFail("Constructor '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }

        public static final class Converted<T> extends AbstractParamsConverter<Constructor<Object>, T, ConstructorDeclaration> {

            public Converted() {
                super(new Constructor<Object>());
            }

            @Override
            protected ConstructorDeclaration init(ClassDeclaration dec, String name) {
                if (dec == null) {
                    throw new IllegalArgumentException("ClassDeclaration is null");
                }
                ConstructorDeclaration cDec = this.raw.init(dec, name);
                if (cDec != null) {
                    initConverters("constructor " + cDec.parameters.toString(), cDec.type, cDec.parameters);
                }
                return cDec;
            }

            private final void verifyConverters(int argCount) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                }
                if (this.argConverters != null) {
                    if (this.argConverters.length != argCount) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                this.argConverters.length + " expected, but got " + argCount + ")");
                    }
                }
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arguments to pass along with the method
             * @return converted created instance
             */
            public final T newInstanceVA(Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                    return null; // never reached
                }

                Object[] convertedArgs = convertArgs(arguments);
                Object rawResult = this.raw.newInstanceVA(convertedArgs);
                return convertResult(rawResult);
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @return converted created instance
             */
            public final T newInstance() {
                return convertResult(this.raw.newInstance());
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @return converted created instance
             */
            public final T newInstance(Object arg0) {
                verifyConverters(1);
                if (this.argConverters == null) {
                    return convertResult(this.raw.newInstance(arg0));
                } else {
                    return convertResult(this.raw.newInstance(
                            convertArgument(0, arg0)));
                }
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @return converted created instance
             */
            public final T newInstance(Object arg0, Object arg1) {
                verifyConverters(2);
                if (this.argConverters == null) {
                    return convertResult(this.raw.newInstance(arg0, arg1));
                } else {
                    return convertResult(this.raw.newInstance(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1)));
                }
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @return converted created instance
             */
            public final T newInstance(Object arg0, Object arg1, Object arg2) {
                verifyConverters(3);
                if (this.argConverters == null) {
                    return convertResult(this.raw.newInstance(arg0, arg1, arg2));
                } else {
                    return convertResult(this.raw.newInstance(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2)));
                }
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @return converted created instance
             */
            public final T newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
                verifyConverters(4);
                if (this.argConverters == null) {
                    return convertResult(this.raw.newInstance(arg0, arg1, arg2, arg3));
                } else {
                    return convertResult(this.raw.newInstance(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3)));
                }
            }

            /**
             * Creates a new instance, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @param arg4 fifth argument
             * @return converted created instance
             */
            public final T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
                verifyConverters(5);
                if (this.argConverters == null) {
                    return convertResult(this.raw.newInstance(arg0, arg1, arg2, arg3, arg4));
                } else {
                    return convertResult(this.raw.newInstance(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3),
                            convertArgument(4, arg4)));
                }
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

        /**
         * Invokes this static method, with no method arguments.
         * 
         * @return return value, null for void methods
         */
        public T invoke() {
            return this.method.invoke(null);
        }

        /**
         * Invokes this static method, with 1 method argument.
         * 
         * @param arg0 first argument
         * @return return value, null for void methods
         */
        public T invoke(Object arg0) {
            return this.method.invoke(null, arg0);
        }

        /**
         * Invokes this static method, with 2 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @return return value, null for void methods
         */
        public T invoke(Object arg0, Object arg1) {
            return this.method.invoke(null, arg0, arg1);
        }

        /**
         * Invokes this static method, with 3 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @return return value, null for void methods
         */
        public T invoke(Object arg0, Object arg1, Object arg2) {
            return this.method.invoke(null, arg0, arg1, arg2);
        }

        /**
         * Invokes this static method, with 4 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @return return value, null for void methods
         */
        public T invoke(Object arg0, Object arg1, Object arg2, Object arg3) {
            return this.method.invoke(null, arg0, arg1, arg2, arg3);
        }

        /**
         * Invokes this static method, with 5 method arguments.
         * 
         * @param arg0 first argument
         * @param arg1 second argument
         * @param arg2 third argument
         * @param arg3 fourth argument
         * @param arg4 fifth argument
         * @return return value, null for void methods
         */
        public T invoke(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            return this.method.invoke(null, arg0, arg1, arg2, arg3, arg4);
        }
        
        public static final class Converted<T> extends AbstractMethodConverter<StaticMethod<Object>, T> {
 
            public Converted() {
                super(new StaticMethod<Object>());
            }

            private final void verifyConverters(int argCount) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                }
                if (this.argConverters != null) {
                    if (this.argConverters.length != argCount) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                this.argConverters.length + " expected, but got " + argCount + ")");
                    }
                }
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

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @return return value, null for void methods
             */
            public final T invoke() {
                return convertResult(this.raw.invoke());
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @return return value, null for void methods
             */
            public final T invoke(Object arg0) {
                verifyConverters(1);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(arg0));
                } else {
                    return convertResult(this.raw.invoke(
                            convertArgument(0, arg0)));
                }
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @return return value, null for void methods
             */
            public final T invoke(Object arg0, Object arg1) {
                verifyConverters(2);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(arg0, arg1));
                } else {
                    return convertResult(this.raw.invoke(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1)));
                }
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @return return value, null for void methods
             */
            public final T invoke(Object arg0, Object arg1, Object arg2) {
                verifyConverters(3);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(arg0, arg1, arg2));
                } else {
                    return convertResult(this.raw.invoke(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2)));
                }
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @return return value, null for void methods
             */
            public final T invoke(Object arg0, Object arg1, Object arg2, Object arg3) {
                verifyConverters(4);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(arg0, arg1, arg2, arg3));
                } else {
                    return convertResult(this.raw.invoke(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3)));
                }
            }

            /**
             * Invokes this static method, performing parameter
             * and return type conversion as required.
             * 
             * @param arg0 first argument
             * @param arg1 second argument
             * @param arg2 third argument
             * @param arg3 fourth argument
             * @param arg4 fifth argument
             * @return return value, null for void methods
             */
            public final T invoke(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
                verifyConverters(5);
                if (this.argConverters == null) {
                    return convertResult(this.raw.invoke(arg0, arg1, arg2, arg3, arg4));
                } else {
                    return convertResult(this.raw.invoke(
                            convertArgument(0, arg0),
                            convertArgument(1, arg1),
                            convertArgument(2, arg2),
                            convertArgument(3, arg3),
                            convertArgument(4, arg4)));
                }
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
                    if (this.argConverters.length != argCount) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                this.argConverters.length + " expected, but got " + argCount + ")");
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
        private String constantName = null;

        /**
         * Gets the current enumeration constant value, guaranteeing to never throw an exception.
         * This should be used when reading static fields during static initialization blocks.
         * 
         * @return static field value, null on failure
         */
        public final T getSafe() {
            if (!this._hasClass) {
                return null;
            }
            try{return get();}catch(RuntimeException ex){return failGetSafe(ex, null);}
        }

        /**
         * Gets the current enumeration constant value, throwing an exception if this for some reason fails.
         * 
         * @return enumeration constant value
         */
        public final T get() {
            if (constant == null) {
                if (constantName == null) {
                    throw new UnsupportedOperationException("Enumeration constant not initialized");
                } else {
                    throw new UnsupportedOperationException("Enumeration constant " + constantName + " is not available");
                }
            }
            return constant;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            if (dec == null) {
                throw new IllegalArgumentException("ClassDeclaration is null");
            }
            constantName = name;
            for (FieldDeclaration fDec : dec.fields) {
                if (fDec.isEnum && fDec.name.real().equals(name)) {
                    // Check if the class is initialized
                    if (dec.type.type == null) {
                        initFail("Enumeration constant " + name + " in class " +
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
                    initFail("Enumeration constant " + name + " missing in class " + dec.type);
                    return null;
                }
            }
            initFail("Failed to find enumeration field " + name + " in class " + dec.type);
            return null;
        }

        @Override
        public void forceInitialization() {
            get();
        }

        @Override
        public boolean isAvailable() {
            return constant != null;
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
                if (!this._hasClass) {
                    return null;
                }
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
            public void forceInitialization() {
                this.raw.forceInitialization();
                this.failNoConverter();
            }

            @Override
            protected void initElementName(String elementName) {
                super.initElementName(elementName);
                raw.initElementName(elementName);
            }

            @Override
            protected void initNoClass() {
                super.initNoClass();
                this.raw.initNoClass();
            }

            @Override
            @SuppressWarnings("unchecked")
            protected FieldDeclaration init(ClassDeclaration dec, String name) {
                if (dec == null) {
                    throw new IllegalArgumentException("ClassDeclaration is null");
                }
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

            @Override
            protected void setOptional() {
                super.setOptional();
                this.raw.setOptional();
            }

            @Override
            public boolean isAvailable() {
                return raw.isAvailable();
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
            if (!_hasClass) {
                return null;
            }
            try{return get();}catch(RuntimeException ex){return failGetSafe(ex, null);}
        }

        /**
         * Gets the current static field value.
         * 
         * @return static field value
         */
        public final T get() {
            return field.get(null);
        }

        /**
         * Sets the static field to a new value
         * 
         * @param value to set to
         */
        public final void set(T value) {
            field.set(null, value);
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
                if (!_hasClass) {
                    return null;
                }
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
                if (!_hasClass) return 0.0;
                try{return getDouble();}catch(RuntimeException ex){return failGetSafe(ex, 0.0);}
            }

            /** @see StaticField#get() */
            public final double getDouble() {
                return field.getDouble(null);
            }

            /** @see StaticField#set(value) */
            public final void setDouble(double value) {
                field.setDouble(null, value);
            }
        }

        public static final class Float extends StaticField<java.lang.Float> {
            /** @see StaticField#getSafe() */
            public final float getFloatSafe() {
                if (!_hasClass) return 0.0f;
                try{return getFloat();}catch(RuntimeException ex){return failGetSafe(ex, 0.0f);}
            }

            /** @see StaticField#get() */
            public final float getFloat() {
                return field.getFloat(null);
            }

            /** @see StaticField#set(value) */
            public final void setFloat(float value) {
                field.setFloat(null, value);
            }
        }

        public static final class Byte extends StaticField<java.lang.Byte> {
            /** @see StaticField#getSafe() */
            public final byte getByteSafe() {
                if (!_hasClass) return (byte) 0;
                try{return getByte();}catch(RuntimeException ex){return failGetSafe(ex, (byte) 0);}
            }

            /** @see StaticField#get() */
            public final byte getByte() {
                return field.getByte(null);
            }

            /** @see StaticField#set(value) */
            public final void setByte(byte value) {
                field.setByte(null, value);
            }
        }

        public static final class Short extends StaticField<java.lang.Short> {
            /** @see StaticField#getSafe() */
            public final short getShortSafe() {
                if (!_hasClass) return (short) 0;
                try{return getShort();}catch(RuntimeException ex){return failGetSafe(ex, (short) 0);}
            }

            /** @see StaticField#get() */
            public final short getShort() {
                return field.getShort(null);
            }

            /** @see StaticField#set(value) */
            public final void setShort(short value) {
                field.setShort(null, value);
            }
        }

        public static final class Integer extends StaticField<java.lang.Integer> {
            /** @see StaticField#getSafe() */
            public final int getIntegerSafe() {
                if (!_hasClass) return 0;
                try{return getInteger();}catch(RuntimeException ex){return failGetSafe(ex, 0);}
            }

            /** @see StaticField#get() */
            public final int getInteger() {
                return field.getInteger(null);
            }

            /** @see StaticField#set(value) */
            public final void setInteger(int value) {
                field.setInteger(null, value);
            }
        }

        public static final class Long extends StaticField<java.lang.Long> {
            /** @see StaticField#getSafe() */
            public final long getLongSafe() {
                if (!_hasClass) return 0L;
                try{return getLong();}catch(RuntimeException ex){return failGetSafe(ex, 0L);}
            }

            /** @see StaticField#get() */
            public final long getLong() {
                return field.getLong(null);
            }

            /** @see StaticField#set(value) */
            public final void setLong(long value) {
                field.setLong(null, value);
            }
        }

        public static final class Character extends StaticField<java.lang.Character> {
            /** @see StaticField#getSafe() */
            public final char getCharacterSafe() {
                if (!_hasClass) return '\0';
                try{return getCharacter();}catch(RuntimeException ex){return failGetSafe(ex, '\0');}
            }

            /** @see StaticField#get() */
            public final char getCharacter() {
                return field.getCharacter(null);
            }

            /** @see StaticField#set(value) */
            public final void setCharacter(char value) {
                field.setCharacter(null, value);
            }
        }

        public static final class Boolean extends StaticField<java.lang.Boolean> {
            /** @see StaticField#getSafe() */
            public final boolean getBooleanSafe() {
                if (!_hasClass) return false;
                try{return getBoolean();}catch(RuntimeException ex){return failGetSafe(ex, false);}
            }

            /** @see StaticField#get() */
            public final boolean getBoolean() {
                return field.getBoolean(null);
            }

            /** @see StaticField#set(value) */
            public final void setBoolean(boolean value) {
                field.setBoolean(null, value);
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
            return this.field.get(instance);
        }

        /**
         * Sets the field value for an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to set the field value for, <i>null</i> for static fields
         * @param value to set to
         */
        public final void set(Object instance, T value) {
            this.field.set(instance, value);
        }

        /**
         * Efficiently copies the field value from one instance to another.
         * 
         * @param instanceFrom to get the value
         * @param instanceTo to set the value
         */
        public void copy(Object instanceFrom, Object instanceTo) {
            this.field.copy(instanceFrom, instanceTo);
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
                return field.getDouble(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setDouble(Object instance, double value) {
                field.setDouble(instance, value);
            }
        }

        public static final class Float extends Field<java.lang.Float> {
            /** @see Field#get(instance) */
            public final float getFloat(Object instance) {
                return field.getFloat(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setFloat(Object instance, float value) {
                field.setFloat(instance, value);
            }
        }

        public static final class Byte extends Field<java.lang.Byte> {
            /** @see Field#get(instance) */
            public final byte getByte(Object instance) {
                return field.getByte(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setByte(Object instance, byte value) {
                field.setByte(instance, value);
            }
        }

        public static final class Short extends Field<java.lang.Short> {
            /** @see Field#get(instance) */
            public final short getShort(Object instance) {
                return field.getShort(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setShort(Object instance, short value) {
                field.setShort(instance, value);
            }
        }

        public static final class Integer extends Field<java.lang.Integer> {
            /** @see Field#get(instance) */
            public final int getInteger(Object instance) {
                return field.getInteger(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setInteger(Object instance, int value) {
                field.setInteger(instance, value);
            }
        }

        public static final class Long extends Field<java.lang.Long> {
            /** @see Field#get(instance) */
            public final long getLong(Object instance) {
                return field.getLong(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setLong(Object instance, long value) {
                field.setLong(instance, value);
            }
        }

        public static final class Character extends Field<java.lang.Character> {
            /** @see Field#get(instance) */
            public final char getCharacter(Object instance) {
                return field.getCharacter(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setCharacter(Object instance, char value) {
                field.setCharacter(instance, value);
            }
        }

        public static final class Boolean extends Field<java.lang.Boolean> {
            /** @see Field#get(instance) */
            public final boolean getBoolean(Object instance) {
                return field.getBoolean(instance);
            }

            /** @see Field#set(instance, value) */
            public final void setBoolean(Object instance, boolean value) {
                field.setBoolean(instance, value);
            }
        }
    }

    /**
     * Indicates a declaration statement is optional and not guaranteed to exist
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Optional {
    }

    /**
     * Indicates a declaration statement is readonly and only get-access is possible
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Readonly {
    }
}
