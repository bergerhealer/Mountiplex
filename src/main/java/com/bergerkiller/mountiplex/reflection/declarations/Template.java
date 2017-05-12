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
import com.bergerkiller.mountiplex.reflection.util.SecureField;

public class Template {

    public static class Class {
        private boolean successful = true;

        protected void init(java.lang.Class<?> type, String classpath) {
            // Retrieve the Class Declaration belonging to this classpath
            java.lang.Class<?> classType = Resolver.loadClass(classpath, false);
            if (classType == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class " + classpath + " not found; Template '" +
                        getClass().getSimpleName() + " not initialized.");
                successful = false;
                return;
            }

            ClassDeclaration dec = Resolver.resolveClassDeclaration(classType);
            if (dec == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class Declaration for " + classType.getName() + " not found");
                successful = false;
                return;
            }

            for (java.lang.reflect.Field templateFieldRef : type.getFields()) {
                String templateFieldName = templateFieldRef.getName();
                try {
                    Object templateField = templateFieldRef.get(this);
                    if (templateField instanceof TemplateElement) {
                        Object result = ((TemplateElement<?>) templateField).init(dec, templateFieldName);
                        if (result == null) {
                            successful = false;
                        }
                    }
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize template field " +
                        "'" + templateFieldName + "' in " + classpath, t);
                    successful = false;
                }
            }
        }

        /**
         * Gets whether this entire Class Template has been initialized without any errors
         * 
         * @return True if loaded successfully, False if errors had occurred
         */
        public boolean isSuccessfullyLoaded() {
            return successful;
        }
    }

    /**
     * Base class for objects that refer to a hidden type
     */
    public static class Handle {
        protected Object instance = null;

        public void setInstance(Object instance) {
            this.instance = instance;
        }
    }

    // provides a default 'init' method to use when initializing the Template
    // all declared class element types must extend this type
    public static abstract class TemplateElement<T extends Declaration> {
        protected abstract T init(ClassDeclaration dec, String name);
    }

    public static class AbstractField<T> extends TemplateElement<FieldDeclaration> {
        protected final SecureField field = new SecureField();

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

    public static class AbstractMethod extends TemplateElement<MethodDeclaration> {
        protected java.lang.reflect.Method method = null;

        // throws an exception when the method is not found
        protected final void failNotFound() {
            if (this.method == null) {
                throw new RuntimeException("Method not found");
            }
        }

        // throws an exception when arguments differ
        protected final void failInvalidArgs(Object[] arguments) {
            
        }

        @Override
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            for (MethodDeclaration methodDec : dec.methods) {
                if (methodDec.method != null && methodDec.name.real().equals(name)) {
                    try {
                        methodDec.method.setAccessible(true);
                        this.method = methodDec.method;
                        return methodDec;
                    } catch (Throwable t) {
                        MountiplexUtil.LOGGER.warning("Method '" + name + "' in template for " + dec.type.typePath + " not accessible");
                        return null;
                    }
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
        public <T> MethodAccessor<T> toMethodAccessor() {
            return new SafeMethod<T>(this.method);
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

    public static class AbstractMethodConverter<M extends AbstractMethod, T> extends TemplateElement<MethodDeclaration> {
        public final M raw;
        protected Converter<?, T> resultConverter = null;
        protected Converter<?, ?>[] argConverters = null;
        protected boolean isConvertersInitialized = false;

        protected AbstractMethodConverter(M raw) {
            this.raw = raw;
        }

        protected final void failNoConverter() {
            if (!isConvertersInitialized) {
                throw new UnsupportedOperationException("Method converters could not be initialized");
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            MethodDeclaration mDec = this.raw.init(dec, name);
            if (mDec != null) {
                this.isConvertersInitialized = true;
                this.resultConverter = null;
                this.argConverters = null;

                // Initialize the converter for the return value
                if (mDec.returnType.cast != null) {
                    this.resultConverter = (Converter<?, T>) Conversion.find(mDec.returnType, mDec.returnType.cast);
                    if (this.resultConverter == null) {
                        this.isConvertersInitialized = false;
                        MountiplexUtil.LOGGER.warning("Converter for method " + mDec.name.toString() + 
                                " return type not found: " + mDec.returnType.toString());
                    }
                }

                // Converters for the arguments of the method
                ParameterDeclaration[] params = mDec.parameters.parameters;
                this.argConverters = new Converter<?, ?>[params.length];
                boolean hasArgumentConversion = false;
                for (int i = 0; i < params.length; i++) {
                    if (params[i].type.cast != null) {
                        hasArgumentConversion = true;
                        this.argConverters[i] = Conversion.find(params[i].type.cast, params[i].type);
                        if (this.argConverters[i] == null) {
                            this.isConvertersInitialized = false;
                            MountiplexUtil.LOGGER.warning("Converter for method " + mDec.name.toString() + 
                                    " argument " + params[i].name.toString() + " not found: " + params[i].type.toString());
                        }
                    }
                }
                if (!hasArgumentConversion) {
                    this.argConverters = null;
                }
            }
            return mDec;
        }
    }

    public static final class Constructor {
        protected java.lang.reflect.Constructor<?> constructor = null;

        /**
         * Constructs a new Instance of the class using this constructor.
         * 
         * @param args for the constructor
         * @return the constructed class instance
         */
        public Object newInstance(Object... args) {
            try {
                return constructor.newInstance(args);
            } catch (Throwable t) {
                throw new RuntimeException("WELP");
            }
        }
    }

    public static final class StaticMethod<T> extends AbstractMethod {

        /**
         * Invokes this static method
         * 
         * @param arguments to pass along with the method
         * @return return value, null for void methods
         */
        @SuppressWarnings("unchecked")
        public T invoke(Object... arguments) {
            try {
                return (T) this.method.invoke(null, arguments);
            } catch (Throwable t) {
                this.failNotFound();
                this.failInvalidArgs(arguments);
                throw new RuntimeException("Failed to invoke static method", t);
            }
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
            @SuppressWarnings("unchecked")
            public final T invoke(Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                    return null; // never reached
                }

                Object rawResult;
                if (this.argConverters != null) {
                    // Verify correct number of arguments
                    if (this.argConverters.length != (arguments.length)) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                (this.argConverters.length - 1) + " expected, but got " + arguments.length + ")");
                    }

                    // Got to convert the parameters
                    Object[] convertedArgs = arguments.clone();
                    for (int i = 0; i < convertedArgs.length; i++) {
                        if (this.argConverters[i] != null) {
                            convertedArgs[i] = this.argConverters[i].convert(convertedArgs[i]);
                        }
                    }
                    rawResult = this.raw.invoke(convertedArgs);
                } else {
                    // Only result is converted
                    rawResult = this.raw.invoke(arguments);
                }

                if (this.resultConverter != null) {
                    return this.resultConverter.convert(rawResult);
                } else {
                    return (T) rawResult;
                }
            }
        }
    }

    public static final class Method<T> extends AbstractMethod {

        /**
         * Invokes this method on the instance specified.
         * 
         * @param instance to invoke the method on
         * @param arguments to pass along with the method
         * @return return value, null for void methods
         */
        @SuppressWarnings("unchecked")
        public T invoke(Object instance, Object... arguments) {
            try {
                return (T) this.method.invoke(instance, arguments);
            } catch (Throwable t) {
                this.failNotFound();
                if (instance == null) {
                    throw new IllegalArgumentException("Instance is null");
                }
                this.failInvalidArgs(arguments);
                throw new RuntimeException("Failed to invoke method", t);
            }
        }

        public static final class Converted<T> extends AbstractMethodConverter<Method<Object>, T> {
 
            public Converted() {
                super(new Method<Object>());
            }

            /**
             * Invokes this method on the instance specified, performing parameter
             * and return type conversion as required.
             * 
             * @param instance to invoke the method on
             * @param arguments to pass along with the method
             * @return return value, null for void methods
             */
            @SuppressWarnings("unchecked")
            public final T invoke(Object instance, Object... arguments) {
                if (!this.isConvertersInitialized) {
                    this.raw.failNotFound();
                    this.failNoConverter();
                    return null; // never reached
                }

                Object rawResult;
                if (this.argConverters != null) {
                    // Verify correct number of arguments
                    if (this.argConverters.length != (arguments.length)) {
                        throw new IllegalArgumentException("Invalid number of arguments (" +
                                (this.argConverters.length - 1) + " expected, but got " + arguments.length + ")");
                    }

                    // Got to convert the parameters
                    Object[] convertedArgs = arguments.clone();
                    for (int i = 0; i < convertedArgs.length; i++) {
                        if (this.argConverters[i] != null) {
                            convertedArgs[i] = this.argConverters[i].convert(convertedArgs[i]);
                        }
                    }
                    rawResult = this.raw.invoke(instance, convertedArgs);
                } else {
                    // Only result is converted
                    rawResult = this.raw.invoke(instance, arguments);
                }

                if (this.resultConverter != null) {
                    return this.resultConverter.convert(rawResult);
                } else {
                    return (T) rawResult;
                }
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
            try{return get();}catch(Throwable t){return failGetSafe(t, null);}
        }

        /**
         * Gets the current static field value.
         * 
         * @return static field value
         */
        @SuppressWarnings("unchecked")
        public final T get() {
            try {
                return (T) field.read().get(null);
            } catch (Throwable t) {
                throw failGet(t);
            }
        }

        /**
         * Sets the static field to a new value
         * 
         * @param value to set to
         */
        public final void set(T value) {
            try {
                field.write().set(null, value);
            } catch (Throwable t) {
                throw failSet(t, value);
            }
        }

        protected final <V> V failGetSafe(Throwable t, V def) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get static field value", t);
            return def;
        }

        protected final RuntimeException failGet(Throwable t) {
            this.field.checkGet();
            return new RuntimeException("Failed to get field", t);
        }

        protected final RuntimeException failSet(Throwable t, Object value) {
            this.field.checkSet(value);
            return new RuntimeException("Failed to set field", t);
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
                try {return get();}catch(Throwable t){return raw.failGetSafe(t, null);}
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
                try{return getDouble();}catch(Throwable t){return failGetSafe(t, 0.0);}
            }

            /** @see StaticField#get() */
            public final double getDouble() {
                try{return field.read().getDouble(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setDouble(double value) {
                try{field.write().setDouble(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Float extends StaticField<java.lang.Float> {
            /** @see StaticField#getSafe() */
            public final float getFloatSafe() {
                try{return getFloat();}catch(Throwable t){return failGetSafe(t, 0.0f);}
            }

            /** @see StaticField#get() */
            public final float getFloat() {
                try{return field.read().getFloat(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setFloat(float value) {
                try{field.write().setFloat(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Byte extends StaticField<java.lang.Byte> {
            /** @see StaticField#getSafe() */
            public final byte getByteSafe() {
                try{return getByte();}catch(Throwable t){return failGetSafe(t, (byte) 0);}
            }

            /** @see StaticField#get() */
            public final byte getByte() {
                try{return field.read().getByte(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setByte(byte value) {
                try{field.write().setByte(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Short extends StaticField<java.lang.Short> {
            /** @see StaticField#getSafe() */
            public final short getShortSafe() {
                try{return getShort();}catch(Throwable t){return failGetSafe(t, (short) 0);}
            }

            /** @see StaticField#get() */
            public final short getShort() {
                try{return field.read().getShort(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setShort(short value) {
                try{field.write().setShort(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Integer extends StaticField<java.lang.Integer> {
            /** @see StaticField#getSafe() */
            public final int getIntegerSafe() {
                try{return getInteger();}catch(Throwable t){return failGetSafe(t, 0);}
            }

            /** @see StaticField#get() */
            public final int getInteger() {
                try{return field.read().getInt(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setInteger(int value) {
                try{field.write().setInt(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Long extends StaticField<java.lang.Long> {
            /** @see StaticField#getSafe() */
            public final long getLongSafe() {
                try{return getLong();}catch(Throwable t){return failGetSafe(t, 0L);}
            }

            /** @see StaticField#get() */
            public final long getLong() {
                try{return field.read().getLong(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setLong(long value) {
                try{field.write().setLong(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Character extends StaticField<java.lang.Character> {
            /** @see StaticField#getSafe() */
            public final char getCharacterSafe() {
                try{return getCharacter();}catch(Throwable t){return failGetSafe(t, '\0');}
            }

            /** @see StaticField#get() */
            public final char getCharacter() {
                try{return field.read().getChar(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setCharacter(char value) {
                try{field.write().setChar(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Boolean extends StaticField<java.lang.Boolean> {
            /** @see StaticField#getSafe() */
            public final boolean getBooleanSafe() {
                try{return getBoolean();}catch(Throwable t){return failGetSafe(t, false);}
            }

            /** @see StaticField#get() */
            public final boolean getBoolean() {
                try{return field.read().getBoolean(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setBoolean(boolean value) {
                try{field.write().setBoolean(null, value);}catch(Throwable t){throw failSet(t,value);}
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
        @SuppressWarnings("unchecked")
        public final T get(Object instance) {
            try {
                return (T) this.field.read().get(instance);
            } catch (Throwable t) {
                throw failGet(t, instance);
            }
        }

        /**
         * Sets the field value for an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to set the field value for, <i>null</i> for static fields
         * @param value to set to
         */
        public final void set(Object instance, T value) {
            try {
                this.field.write().set(instance, value);
            } catch (Throwable t) {
                throw failSet(t, instance, value);
            }
        }

        /**
         * Efficiently copies the field value from one instance to another.
         * 
         * @param instanceFrom to get the value
         * @param instanceTo to set the value
         */
        public void copy(Object instanceFrom, Object instanceTo) {
            try {
                this.field.write().set(instanceTo, this.field.read().get(instanceFrom));
            } catch (Throwable t) {
                throw failCopy(t, instanceFrom, instanceTo);
            }
        }

        protected final RuntimeException failGet(Throwable t, Object instance) {
            this.field.checkGet();
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
                try{return field.read().getDouble(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setDouble(Object instance, double value) {
                try{field.write().setDouble(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setDouble(instanceTo, field.read().getDouble(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Float extends Field<java.lang.Float> {
            /** @see Field#get(instance) */
            public final float getFloat(Object instance) {
                try{return field.read().getFloat(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setFloat(Object instance, float value) {
                try{field.write().setFloat(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setFloat(instanceTo, field.read().getFloat(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Byte extends Field<java.lang.Byte> {
            /** @see Field#get(instance) */
            public final byte getByte(Object instance) {
                try{return field.read().getByte(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setByte(Object instance, byte value) {
                try{field.write().setByte(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setByte(instanceTo, field.read().getByte(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Short extends Field<java.lang.Short> {
            /** @see Field#get(instance) */
            public final short getShirt(Object instance) {
                try{return field.read().getShort(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setShort(Object instance, short value) {
                try{field.write().setShort(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setShort(instanceTo, field.read().getShort(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Integer extends Field<java.lang.Integer> {
            /** @see Field#get(instance) */
            public final int getInteger(Object instance) {
                try{return field.read().getInt(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setInteger(Object instance, int value) {
                try{field.write().setInt(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setInt(instanceTo, field.read().getInt(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Long extends Field<java.lang.Long> {
            /** @see Field#get(instance) */
            public final long getLong(Object instance) {
                try{return field.read().getLong(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setLong(Object instance, long value) {
                try{field.write().setLong(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setLong(instanceTo, field.read().getLong(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Character extends Field<java.lang.Character> {
            /** @see Field#get(instance) */
            public final char getCharacter(Object instance) {
                try{return field.read().getChar(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setCharacter(Object instance, char value) {
                try{field.write().setChar(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setChar(instanceTo, field.read().getChar(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Boolean extends Field<java.lang.Boolean> {
            /** @see Field#get(instance) */
            public final boolean getBoolean(Object instance) {
                try{return field.read().getBoolean(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setBoolean(Object instance, boolean value) {
                try{field.write().setBoolean(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.write().setBoolean(instanceTo, field.read().getBoolean(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }
    }

}
