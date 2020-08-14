package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.Copier;
import com.bergerkiller.mountiplex.reflection.util.fast.Reader;
import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionAccessor;
import com.bergerkiller.mountiplex.reflection.util.fast.Writer;

/**
 * Efficiently manages reflection delegating calls to getter and setter methods
 * to runtime-generated accessors made specifically for the field type.
 * 
 * @param <T> type of fast field
 */
public final class FastField<T> implements Reader<T>, Writer<T>, Copier, LazyInitializedObject, IgnoresRemapping {
    private Reader<T> reader;
    private Writer<T> writer;
    private Copier copier;
    private java.lang.reflect.Field field;
    private String missingInfo = "!!UNKNOWN!!"; // stored info for when field is null

    public FastField() {
        this.init(null);
    }

    /**
     * Initializes the fast field using a Java Reflection Field.
     * To deinitialize this fast field, use null.
     * 
     * @param field to initialize to
     */
    public final void init(java.lang.reflect.Field field) {
        this.field = field;
        FastFieldInitProxy initProxy = new FastFieldInitProxy();
        this.reader = initProxy;
        this.writer = initProxy;
        this.copier = initProxy;
    }

    /**
     * Declares this field to be unavailable, providing a missing information String to later identify it
     * 
     * @param missingInfo to print when trying to access it
     */
    public final void initUnavailable(String missingInfo) {
        this.init(null);
        this.missingInfo = missingInfo;
    }

    /**
     * Checks whether this fast field is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (field == null) {
            throw new UnsupportedOperationException("Field " + missingInfo + " is not available");
        }
    }

    /**
     * Checks whether this field is available
     * 
     * @return True if the method is available
     */
    public final boolean isAvailable() {
        return this.field != null;
    }

    /**
     * Gets the backing Java Reflection Field for this Fast Field. If this fast field
     * is not initialized, this function returns <i>null</i>.
     * 
     * @return field
     */
    public final java.lang.reflect.Field getField() {
        return this.field;
    }

    /**
     * Gets the type of field that is represented by this Fast Field.
     * Returns <i>null</i> if this fast field is not initialized.
     * 
     * @return field type, or <i>null</i>
     */
    @SuppressWarnings("unchecked")
    public final Class<T> getType() {
        if (this.field == null) {
            return null;
        } else {
            return (Class<T>) this.field.getType();
        }
    }

    /**
     * Gets the name of this field.
     * Returns <i>"null"</i> if this fast field is not initialized.
     * 
     * @return field name
     */
    public final String getName() {
        if (this.field == null) {
            return "null";
        } else {
            return MPLType.getName(this.field);
        }
    }

    /**
     * Gets a debug description of this Fast Field.
     * 
     * @return debug description
     */
    public final String getDescription() {
        if (this.field == null) {
            return this.missingInfo;
        } else {
            return this.field.toString();
        }
    }
    
    /**
     * Gets whether this field is a static field.
     * Returns <i>false</i> if this fast field is not initialized.
     * 
     * @return True if static, False if not
     */
    public final boolean isStatic() {
        return this.field != null ? Modifier.isStatic(this.field.getModifiers()) : false;
    }

    private void makeAccessible(boolean write) {
        // Setting a static final field requires hacking away the final modifier in the field
        // Otherwise an illegalAccessException occurs when attempting to set it
        int mod = field.getModifiers();
        if (write && Modifier.isFinal(mod) && Modifier.isStatic(mod)) {
            try {
                ReflectionUtil.removeFinalModifier(field);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException("Failed to make final field " + MPLType.getName(field) + " accessible: " + ex.getMessage());
            }
        }

        // Basic tier takes care of most cases
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to make field " + MPLType.getName(field) + " accessible");
        }
    }

    @Override
    public void forceInitialization() {
        reader.checkCanRead();
    }

    @Override
    public final void checkCanCopy() {
        checkCanWrite();
        checkCanRead();
    }

    @Override
    public final void checkCanWrite() {
        writer.checkCanWrite();
    }

    @Override
    public final void checkCanRead() {
        reader.checkCanRead();
    }

    // Reader calls that initialize the reader and forward the call
    public T get(Object o){return reader.get(o);}
    public double getDouble(Object o){return reader.getDouble(o);}
    public float getFloat(Object o){return reader.getFloat(o);}
    public byte getByte(Object o){return reader.getByte(o);}
    public short getShort(Object o){return reader.getShort(o);}
    public int getInteger(Object o){return reader.getInteger(o);}
    public long getLong(Object o){return reader.getLong(o);}
    public char getCharacter(Object o){return reader.getCharacter(o);}
    public boolean getBoolean(Object o){return reader.getBoolean(o);}
    public java.lang.reflect.Field getReadField(){return reader.getReadField();}

    // Writer calls that initialize the writer and forward the call
    public void set(Object o, T v){writer.set(o, v);}
    public void setDouble(Object o, double v){writer.setDouble(o, v); }
    public void setFloat(Object o, float v){writer.setFloat(o, v); }
    public void setByte(Object o, byte v){writer.setByte(o, v); }
    public void setShort(Object o, short v){writer.setShort(o, v); }
    public void setInteger(Object o, int v){writer.setInteger(o, v); }
    public void setLong(Object o, long v){writer.setLong(o, v); }
    public void setCharacter(Object o, char v){writer.setCharacter(o, v); }
    public void setBoolean(Object o, boolean v){writer.setBoolean(o, v); }
    public java.lang.reflect.Field getWriteField(){return writer.getWriteField();}

    // Copier calls that initialize the copier and forward the call
    public void copy(Object a, Object b){copier.copy(a, b);}
    public Field getCopyField(){return copier.getCopyField();}

    // This object is used at the first call to initialize the field
    private final class FastFieldInitProxy implements Reader<T>, Writer<T>, Copier {
        @SuppressWarnings("unchecked")
        private ReflectionAccessor<T> access() {
            if (writer instanceof ReflectionAccessor) {
                return (ReflectionAccessor<T>) writer;
            } else if (reader instanceof ReflectionAccessor) {
                return (ReflectionAccessor<T>) reader;
            } else if (copier instanceof ReflectionAccessor) {
                return (ReflectionAccessor<T>) copier;
            } else {
                return ReflectionAccessor.create(field);
            }
        }

        private Reader<T> read() {
            if (reader == this) {
                synchronized (FastField.this) {
                    if (reader == this) {
                        checkInit();
                        if (!Resolver.isPublic(field)) {
                            makeAccessible(false);
                        }
                        reader = access();
                    }
                }
            }
            return reader;
        }

        private Writer<T> write() {
            if (writer == this) {
                synchronized (FastField.this) {
                    if (writer == this) {
                        checkInit();
                        int mod = field.getModifiers();
                        if (!Resolver.isPublic(field) || Modifier.isFinal(mod)) {
                            makeAccessible(true);
                        }
                        writer = access();
                    }
                }
            }
            return writer;
        }

        private Copier copy() {
            if (copier == this) {
                synchronized (FastField.this) {
                    if (copier == this) {
                        checkInit();

                        int mod = field.getModifiers();
                        if (Modifier.isStatic(mod)) {
                            throw new RuntimeException("Static fields can not be copied");
                        }

                        if (!Resolver.isPublic(field) || Modifier.isFinal(mod)) {
                            makeAccessible(true);
                        }

                        copier = access();
                    }
                }
            }
            return copier;
        }

        @Override
        public final void checkCanCopy() {
            checkCanWrite();
            checkCanRead();
        }

        @Override
        public final void checkCanWrite() {
            write().checkCanWrite();
        }

        @Override
        public final void checkCanRead() {
            read().checkCanRead();
        }

        // Reader calls that initialize the reader and forward the call
        public T get(Object o){return read().get(o);}
        public double getDouble(Object o){return read().getDouble(o);}
        public float getFloat(Object o){return read().getFloat(o);}
        public byte getByte(Object o){return read().getByte(o);}
        public short getShort(Object o){return read().getShort(o);}
        public int getInteger(Object o){return read().getInteger(o);}
        public long getLong(Object o){return read().getLong(o);}
        public char getCharacter(Object o){return read().getCharacter(o);}
        public boolean getBoolean(Object o){return read().getBoolean(o);}
        public java.lang.reflect.Field getReadField(){return read().getReadField();}

        // Writer calls that initialize the writer and forward the call
        public void set(Object o, T v){write().set(o, v);}
        public void setDouble(Object o, double v){write().setDouble(o, v); }
        public void setFloat(Object o, float v){write().setFloat(o, v); }
        public void setByte(Object o, byte v){write().setByte(o, v); }
        public void setShort(Object o, short v){write().setShort(o, v); }
        public void setInteger(Object o, int v){write().setInteger(o, v); }
        public void setLong(Object o, long v){write().setLong(o, v); }
        public void setCharacter(Object o, char v){write().setCharacter(o, v); }
        public void setBoolean(Object o, boolean v){write().setBoolean(o, v); }
        public java.lang.reflect.Field getWriteField(){return write().getWriteField();}

        // Copier calls that initialize the copier and forward the call
        public void copy(Object a, Object b){copy().copy(a, b);}
        public Field getCopyField(){return copy().getCopyField();}
    }
}
