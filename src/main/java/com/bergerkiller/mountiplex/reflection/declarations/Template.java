package com.bergerkiller.mountiplex.reflection.declarations;

import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
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

            for (java.lang.reflect.Field templateField : type.getFields()) {
                java.lang.Class<?> templateFieldType = templateField.getType();
                String templateFieldName = templateField.getName();
                try {
                    if (Constructor.class.isAssignableFrom(templateFieldType)) {
                        /*
                        Constructor constructor = (Constructor) templateField.get(this);
                        for (ConstructorDeclaration constructorDec : dec.constructors) {
                            
                        }
                        */
                        //TODO!
                    } else if (Method.class.isAssignableFrom(templateFieldType)) {
                        Method<?> method = (Method<?>) templateField.get(this);
                        for (MethodDeclaration methodDec : dec.methods) {
                            if (methodDec.method != null && methodDec.name.real().equals(templateFieldName)) {
                                method.method = methodDec.method;
                                break;
                            }
                        }
                    } else if (Field.class.isAssignableFrom(templateFieldType)) {
                        Field<?> field = (Field<?>) templateField.get(this);
                        for (FieldDeclaration fieldDec : dec.fields) {
                            if (fieldDec.field != null && fieldDec.name.real().equals(templateFieldName)) {
                                field.field = fieldDec.field;
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize template field " +
                        "'" + templateFieldName + "' in " + classpath, t);
                }
            }
        }
    }

    public static class Handle {
        protected Object instance = null;

    }

    public static class Constructor {
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

    public static class Method<T> {
        protected java.lang.reflect.Method method = null;

        /**
         * Invokes this method on the instance specified. Use a <i>null</i> instance
         * for invoking static methods.
         * 
         * @param instance to invoke the method on, <i>null</i> for static methods
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

    public static class Field<T> {
        protected java.lang.reflect.Field field = null;

        /**
         * Gets the field value from an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to get the field value for, <i>null</i> for static fields
         * @return field value
         */
        @SuppressWarnings("unchecked")
        public T get(Object instance) {
            try {
                return (T) field.get(instance);
            } catch (Throwable t) {
                throw failGet(t, instance);
            }
        }

        protected RuntimeException failGet(Throwable t, Object instance) {
            return new RuntimeException("WOOP!");
        }

        protected RuntimeException failSet(Throwable t, Object instance, Object value) {
            return new RuntimeException("WEEP!");
        }

        protected RuntimeException failTransfer(Throwable t, Object instanceFrom, Object instanceTo) {
            return new RuntimeException("WAAP!");
        }

        /**
         * Sets the field value for an instance where the field is declared. Static fields
         * should be accessed by using a <i>null</i> instance.
         * 
         * @param instance to set the field value for, <i>null</i> for static fields
         * @param value to set to
         */
        public void set(Object instance, T value) {
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
                throw failTransfer(t, instanceFrom, instanceTo);
            }
        }

        /* ========================================================================================== */
        /* ================= Please don't look at the copy-pasted code down below =================== */
        /* ========================================================================================== */

        public static final class Double extends Field<Double> {
            /** @see Field#get(instance) */
            public double getDouble(Object instance) {
                try{return field.getDouble(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setDouble(Object instance, double value) {
                try{field.setDouble(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setDouble(instanceTo, field.getDouble(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Float extends Field<Float> {
            /** @see Field#get(instance) */
            public float getFloat(Object instance) {
                try{return field.getFloat(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setFloat(Object instance, float value) {
                try{field.setFloat(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setFloat(instanceTo, field.getFloat(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Byte extends Field<Byte> {
            /** @see Field#get(instance) */
            public byte getByte(Object instance) {
                try{return field.getByte(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setByte(Object instance, byte value) {
                try{field.setByte(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setByte(instanceTo, field.getByte(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Short extends Field<Short> {
            /** @see Field#get(instance) */
            public short getShirt(Object instance) {
                try{return field.getShort(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setShort(Object instance, short value) {
                try{field.setShort(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setShort(instanceTo, field.getShort(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Integer extends Field<Integer> {
            /** @see Field#get(instance) */
            public int getInt(Object instance) {
                try{return field.getInt(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setInt(Object instance, int value) {
                try{field.setInt(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setInt(instanceTo, field.getInt(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Long extends Field<Long> {
            /** @see Field#get(instance) */
            public long getLong(Object instance) {
                try{return field.getLong(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setLong(Object instance, long value) {
                try{field.setLong(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setLong(instanceTo, field.getLong(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Character extends Field<Character> {
            /** @see Field#get(instance) */
            public char getChar(Object instance) {
                try{return field.getChar(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setChar(Object instance, char value) {
                try{field.setChar(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setChar(instanceTo, field.getChar(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }

        public static final class Boolean extends Field<Boolean> {
            /** @see Field#get(instance) */
            public boolean getBoolean(Object instance) {
                try{return field.getBoolean(instance);}catch(Throwable t){throw failGet(t,instance);}
            }

            /** @see Field#set(instance, value) */
            public void setBoolean(Object instance, boolean value) {
                try{field.setBoolean(instance, value);}catch(Throwable t){throw failSet(t,instance,value);}
            }

            @Override
            public void copy(Object instanceFrom, Object instanceTo) {
                try{field.setBoolean(instanceTo, field.getBoolean(instanceFrom));}catch(Throwable t){throw failTransfer(t,instanceFrom,instanceTo);}
            }
        }
    }

}
