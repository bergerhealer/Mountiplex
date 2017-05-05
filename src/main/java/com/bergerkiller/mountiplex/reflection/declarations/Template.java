package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.type.DuplexConverter;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;

public class Template {

    public static class Class {

        protected void init(java.lang.Class<?> type, String classpath) {
            // Retrieve the Class Declaration belonging to this classpath
            java.lang.Class<?> classType = Resolver.loadClass(classpath, false);
            if (classType == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class " + classpath + " not found; Template '" +
                        getClass().getSimpleName() + " not initialized.");
                return;
            }

            ClassDeclaration dec = Resolver.resolveClassDeclaration(classType);
            if (dec == null) {
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Class Declaration for " + classType.getName() + " not found");
                return;
            }

            for (java.lang.reflect.Field templateFieldRef : type.getFields()) {
                String templateFieldName = templateFieldRef.getName();
                try {
                    Object templateField = templateFieldRef.get(this);
                    if (templateField instanceof TemplateElement) {
                        ((TemplateElement<?>) templateField).init(dec, templateFieldName);
                    }
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize template field " +
                        "'" + templateFieldName + "' in " + classpath, t);
                }
            }
        }
    }

    // provides a default 'init' method to use when initializing the Template
    // all declared field types must extend this type
    public static abstract class TemplateElement<T extends Declaration> {
        protected abstract T init(ClassDeclaration dec, String name);
    }

    public static class AbstractField extends TemplateElement<FieldDeclaration> {
        protected java.lang.reflect.Field field = null;

        @Override
        protected FieldDeclaration init(ClassDeclaration dec, String name) {
            for (FieldDeclaration fieldDec : dec.fields) {
                if (fieldDec.field != null && fieldDec.name.real().equals(name)) {
                    this.field = fieldDec.field;
                    return fieldDec;
                }
            }
            MountiplexUtil.LOGGER.warning("Field '" + name + "' not found in template for " + dec.type.typePath);
            return null;
        }
    }

    public static class AbstractMethod extends TemplateElement<MethodDeclaration> {
        protected java.lang.reflect.Method method = null;

        @Override
        protected MethodDeclaration init(ClassDeclaration dec, String name) {
            for (MethodDeclaration methodDec : dec.methods) {
                if (methodDec.method != null && methodDec.name.real().equals(name)) {
                    this.method = methodDec.method;
                    return methodDec;
                }
            }
            MountiplexUtil.LOGGER.warning("Method '" + name + "' not found in " + dec.type.typePath);
            return null;
        }
    }

    public static class Handle {
        protected Object instance = null;

    }

    public static class AbstractFieldConverter<F extends AbstractField, T> extends TemplateElement<FieldDeclaration> {
        public final F raw;
        protected DuplexConverter<?, T> converter = null;

        protected AbstractFieldConverter(F raw) {
            this.raw = raw;
        }

        protected final RuntimeException failConv(RuntimeException ex) {
            if (converter == null) {
                throw new UnsupportedOperationException("Field converter was not found");
            }
            return ex;
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
                throw new RuntimeException("WUH OH!");
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
                throw new RuntimeException("WUH OH!");
            }
        }
    }

    public static class StaticField<T> extends AbstractField {

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
                return (T) field.get(null);
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
                field.set(null, value);
            } catch (Throwable t) {
                throw failSet(t, value);
            }
        }

        protected final <V> V failGetSafe(Throwable t, V def) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get static field value", t);
            return def;
        }

        protected final RuntimeException failGet(Throwable t) {
            if (field == null) {
                throw new UnsupportedOperationException("Field was not resolved");
            }
            return new RuntimeException("Failed to get field", t);
        }

        protected final RuntimeException failSet(Throwable t, Object value) {
            if (field == null) {
                throw new UnsupportedOperationException("Field was not resolved");
            }
            java.lang.Class<?> valueType = field.getType();
            if (valueType.isPrimitive() && value == null) {
                throw new IllegalArgumentException("Failed to set field: primitive fields can not be assigned null");
            }
            if (value != null && valueType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Failed to set field: value can not be assigned to " + valueType.getName());
            }
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
                try {
                    return converter.convert(raw.get());
                } catch (RuntimeException t) {
                    throw failConv(t);
                }
            }

            /**
             * Converts the value to the correct built-in type and sets the static field value
             * 
             * @param value to convert and set the static field to
             */
            public final void set(T value) {
                try {
                    raw.set(converter.convertReverse(value));
                } catch (RuntimeException t) {
                    throw failConv(t);
                }
            }
        }

        /* ========================================================================================== */
        /* ================= Please don't look at the copy-pasted code down below =================== */
        /* ========================================================================================== */

        public static final class Double extends StaticField<Double> {
            /** @see StaticField#getSafe() */
            public final double getDoubleSafe() {
                try{return getDouble();}catch(Throwable t){return failGetSafe(t, 0.0);}
            }

            /** @see StaticField#get() */
            public final double getDouble() {
                try{return field.getDouble(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setDouble(double value) {
                try{field.setDouble(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Float extends StaticField<Float> {
            /** @see StaticField#getSafe() */
            public final float getFloatSafe() {
                try{return getFloat();}catch(Throwable t){return failGetSafe(t, 0.0f);}
            }

            /** @see StaticField#get() */
            public final float getFloat() {
                try{return field.getFloat(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setFloat(float value) {
                try{field.setFloat(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Byte extends StaticField<Byte> {
            /** @see StaticField#getSafe() */
            public final byte getByteSafe() {
                try{return getByte();}catch(Throwable t){return failGetSafe(t, (byte) 0);}
            }

            /** @see StaticField#get() */
            public final byte getByte() {
                try{return field.getByte(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setByte(byte value) {
                try{field.setByte(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Short extends StaticField<Short> {
            /** @see StaticField#getSafe() */
            public final short getShortSafe() {
                try{return getShort();}catch(Throwable t){return failGetSafe(t, (short) 0);}
            }

            /** @see StaticField#get() */
            public final short getShort() {
                try{return field.getShort(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setShort(short value) {
                try{field.setShort(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Integer extends StaticField<Integer> {
            /** @see StaticField#getSafe() */
            public final int getIntegerSafe() {
                try{return getInteger();}catch(Throwable t){return failGetSafe(t, 0);}
            }

            /** @see StaticField#get() */
            public final int getInteger() {
                try{return field.getInt(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setInteger(int value) {
                try{field.setInt(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Long extends StaticField<Long> {
            /** @see StaticField#getSafe() */
            public final long getLongSafe() {
                try{return getLong();}catch(Throwable t){return failGetSafe(t, 0L);}
            }

            /** @see StaticField#get() */
            public final long getLong() {
                try{return field.getLong(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setLong(long value) {
                try{field.setLong(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Character extends StaticField<Character> {
            /** @see StaticField#getSafe() */
            public final char getCharacterSafe() {
                try{return getCharacter();}catch(Throwable t){return failGetSafe(t, '\0');}
            }

            /** @see StaticField#get() */
            public final char getCharacter() {
                try{return field.getChar(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setCharacter(char value) {
                try{field.setChar(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }

        public static final class Boolean extends StaticField<Boolean> {
            /** @see StaticField#getSafe() */
            public final boolean getBooleanSafe() {
                try{return getBoolean();}catch(Throwable t){return failGetSafe(t, false);}
            }

            /** @see StaticField#get() */
            public final boolean getBoolean() {
                try{return field.getBoolean(null);}catch(Throwable t){throw failGet(t);}
            }

            /** @see StaticField#set(value) */
            public final void setBoolean(boolean value) {
                try{field.setBoolean(null, value);}catch(Throwable t){throw failSet(t,value);}
            }
        }
    }

    public static class Field<T> extends AbstractField {

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
                return (T) field.get(instance);
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
                field.set(instance, value);
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
                field.set(instanceTo, field.get(instanceFrom));
            } catch (Throwable t) {
                throw failCopy(t, instanceFrom, instanceTo);
            }
        }

        protected final RuntimeException failGet(Throwable t, Object instance) {
            if (field == null) {
                throw new UnsupportedOperationException("Field was not resolved");
            }
            if (instance == null) {
                throw new IllegalArgumentException("Failed to get field: instance is null");
            }
            java.lang.Class<?> type = field.getDeclaringClass();
            if (!type.isAssignableFrom(instance.getClass())) {
                throw new IllegalArgumentException("Failed to get field: instance is not an instance of " + type.getName());
            }
            return new RuntimeException("Failed to get field", t);
        }

        protected final RuntimeException failSet(Throwable t, Object instance, Object value) {
            if (field == null) {
                throw new UnsupportedOperationException("Field was not resolved");
            }
            if (instance == null) {
                throw new IllegalArgumentException("Failed to set field: instance is null");
            }
            java.lang.Class<?> type = field.getDeclaringClass();
            if (!type.isAssignableFrom(instance.getClass())) {
                throw new IllegalArgumentException("Failed to set field: instance is not an instance of " + type.getName());
            }
            java.lang.Class<?> valueType = field.getType();
            if (valueType.isPrimitive() && value == null) {
                throw new IllegalArgumentException("Failed to set field: primitive fields can not be assigned null");
            }
            if (value != null && valueType.isAssignableFrom(value.getClass())) {
                throw new IllegalArgumentException("Failed to set field: value can not be assigned to " + valueType.getName());
            }
            return new RuntimeException("Failed to set field", t);
        }

        protected final RuntimeException failCopy(Throwable t, Object instanceFrom, Object instanceTo) {
            if (instanceFrom == null) {
                throw new IllegalArgumentException("Failed to copy: instanceFrom is null");
            }
            if (instanceTo == null) {
                throw new IllegalArgumentException("Failed to copy: instanceTo is null");
            }
            java.lang.Class<?> type = field.getDeclaringClass();
            if (!type.isAssignableFrom(instanceFrom.getClass())) {
                throw new IllegalArgumentException("Failed to copy: instanceFrom is not an instance of " + type.getName());
            }
            if (!type.isAssignableFrom(instanceTo.getClass())) {
                throw new IllegalArgumentException("Failed to copy: instanceTo is not an instance of " + type.getName());
            }
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
                try {
                    return converter.convert(raw.get(instance));
                } catch (RuntimeException t) {
                    throw failConv(t);
                }
            }

            /**
             * Converts the value to the correct built-in type and sets the field value for an instance
             * 
             * @param instance to set the field for
             * @param value to convert and set the field to
             */
            public final void set(Object instance, T value) {
                try {
                    raw.set(instance, converter.convertReverse(value));
                } catch (RuntimeException t) {
                    throw failConv(t);
                }
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

        public static final class Double extends Field<Double> {
            /** @see Field#get(instance) */
            public final double getDouble(Object instance) {
                try{return field.getDouble(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setDouble(Object instance, double value) {
                try{field.setDouble(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setDouble(instanceTo, field.getDouble(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Float extends Field<Float> {
            /** @see Field#get(instance) */
            public final float getFloat(Object instance) {
                try{return field.getFloat(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setFloat(Object instance, float value) {
                try{field.setFloat(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setFloat(instanceTo, field.getFloat(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Byte extends Field<Byte> {
            /** @see Field#get(instance) */
            public final byte getByte(Object instance) {
                try{return field.getByte(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setByte(Object instance, byte value) {
                try{field.setByte(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setByte(instanceTo, field.getByte(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Short extends Field<Short> {
            /** @see Field#get(instance) */
            public final short getShirt(Object instance) {
                try{return field.getShort(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setShort(Object instance, short value) {
                try{field.setShort(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setShort(instanceTo, field.getShort(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Integer extends Field<Integer> {
            /** @see Field#get(instance) */
            public final int getInteger(Object instance) {
                try{return field.getInt(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setInteger(Object instance, int value) {
                try{field.setInt(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setInt(instanceTo, field.getInt(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Long extends Field<Long> {
            /** @see Field#get(instance) */
            public final long getLong(Object instance) {
                try{return field.getLong(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setLong(Object instance, long value) {
                try{field.setLong(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setLong(instanceTo, field.getLong(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Character extends Field<Character> {
            /** @see Field#get(instance) */
            public final char getCharacter(Object instance) {
                try{return field.getChar(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setCharacter(Object instance, char value) {
                try{field.setChar(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setChar(instanceTo, field.getChar(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Boolean extends Field<Boolean> {
            /** @see Field#get(instance) */
            public final boolean getBoolean(Object instance) {
                try{return field.getBoolean(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public final void setBoolean(Object instance, boolean value) {
                try{field.setBoolean(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public final void copy(Object instanceFrom, Object instanceTo) {
                try{field.setBoolean(instanceTo, field.getBoolean(instanceFrom));}catch(Throwable t){throw failCopy(t,instanceFrom,instanceTo);}
            }
        }
    }

}
