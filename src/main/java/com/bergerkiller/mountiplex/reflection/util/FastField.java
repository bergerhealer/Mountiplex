package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.field.Copier;
import com.bergerkiller.mountiplex.reflection.util.field.Reader;
import com.bergerkiller.mountiplex.reflection.util.field.ReflectionAccessor;
import com.bergerkiller.mountiplex.reflection.util.field.Writer;

/**
 * Efficiently manages reflection delegating calls to getter and setter methods
 * to runtime-generated accessors made specifically for the field type.
 * 
 * @param <T> type of fast field
 */
public class FastField<T> implements Reader<T>, Writer<T>, Copier {
    public Reader<T> reader = this;
    public Writer<T> writer = this;
    public Copier copier = this;
    private java.lang.reflect.Field field;

    /**
     * Initializes the fast field using a Java Reflection Field.
     * To deinitialize this fast field, use null.
     * 
     * @param field to initialize to
     */
    public final void init(java.lang.reflect.Field field) {
        this.field = field;
        this.reader = this;
        this.writer = this;
        this.copier = this;
    }

    /**
     * Checks whether this fast field is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (field == null) {
            throw new UnsupportedOperationException("Field is not available");
        }
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
            return this.field.getName();
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

    private void makeAccessible() {
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to make field " + field.getName() + " accessible");
        }
    }

    private Reader<T> read() {
        if (reader == this) {
            checkInit();
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod)) {
                if (Modifier.isStatic(mod)) {
                    //TODO: Generated static accessor
                    reader = new ReflectionAccessor<T>(field);
                } else {
                    //TODO: Generated local accessor
                    reader = new ReflectionAccessor<T>(field);
                }
            } else {
                // Non-public fields require reflection
                makeAccessible();
                if (writer instanceof ReflectionAccessor) {
                    reader = (ReflectionAccessor<T>) writer;
                } else {
                    writer = new ReflectionAccessor<T>(field);
                }
            }
        }
        return reader;
    }

    private Writer<T> write() {
        if (writer == this) {
            checkInit();
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isFinal(mod)) {
                if (Modifier.isStatic(mod)) {
                    //TODO: Generated static accessor
                    writer = new ReflectionAccessor<T>(field);
                } else {
                    //TODO: Generated local accessor
                    writer = new ReflectionAccessor<T>(field);
                }
            } else {
                // Non-public or final fields require reflection
                makeAccessible();
                if (reader instanceof ReflectionAccessor) {
                    writer = (ReflectionAccessor<T>) reader;
                } else {
                    writer = new ReflectionAccessor<T>(field);
                }
            }
        }
        return writer;
    }

    private Copier copy() {
        if (copier == this) {
            checkInit();

            int mod = field.getModifiers();
            if (Modifier.isStatic(mod)) {
                throw new RuntimeException("Static fields can not be copied");
            }
            if (Modifier.isPublic(mod) && !Modifier.isFinal(mod)) {
                // TODO: Generated copy function to prevent needless function calls
                Class<?> t = field.getType();
                copier = (t==double.class)  ? new DoubleCopier(this):
                         (t==float.class)   ? new FloatCopier(this):
                         (t==byte.class)    ? new ByteCopier(this):
                         (t==short.class)   ? new ShortCopier(this):
                         (t==int.class)     ? new IntegerCopier(this):
                         (t==long.class)    ? new LongCopier(this):
                         (t==char.class)    ? new CharacterCopier(this):
                         (t==boolean.class) ? new BooleanCopier(this):
                                              new ObjectCopier(this);
            } else {
                // Use separate get and set calls using the reader and writer
                Class<?> t = field.getType();
                copier = (t==double.class)  ? new DoubleCopier(this):
                         (t==float.class)   ? new FloatCopier(this):
                         (t==byte.class)    ? new ByteCopier(this):
                         (t==short.class)   ? new ShortCopier(this):
                         (t==int.class)     ? new IntegerCopier(this):
                         (t==long.class)    ? new LongCopier(this):
                         (t==char.class)    ? new CharacterCopier(this):
                         (t==boolean.class) ? new BooleanCopier(this):
                                              new ObjectCopier(this);
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

    private static abstract class CopierBase implements Copier {
        protected final FastField<Object> owner;

        @SuppressWarnings("unchecked")
        public CopierBase(FastField<?> owner) {
            this.owner = (FastField<Object>) owner;
        }

        @Override
        public final java.lang.reflect.Field getCopyField() {
            return owner.writer.getWriteField();
        }

        @Override
        public final void checkCanCopy() {
            owner.reader.checkCanRead();
            owner.writer.checkCanWrite();
        }
    }

    private static final class ObjectCopier extends CopierBase {
        public ObjectCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.set(b,owner.reader.get(a));}
    }

    private static final class DoubleCopier extends CopierBase {
        public DoubleCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setDouble(b,owner.reader.getDouble(a));}
    }

    private static final class FloatCopier extends CopierBase {
        public FloatCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setFloat(b,owner.reader.getFloat(a));}
    }

    private static final class ByteCopier extends CopierBase {
        public ByteCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setByte(b,owner.reader.getByte(a));}
    }

    private static final class ShortCopier extends CopierBase {
        public ShortCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setShort(b,owner.reader.getShort(a));}
    }

    private static final class IntegerCopier extends CopierBase {
        public IntegerCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setInteger(b,owner.reader.getInteger(a));}
    }

    private static final class LongCopier extends CopierBase {
        public LongCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setLong(b,owner.reader.getLong(a));}
    }

    private static final class CharacterCopier extends CopierBase {
        public CharacterCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setCharacter(b,owner.reader.getCharacter(a));}
    }

    private static final class BooleanCopier extends CopierBase {
        public BooleanCopier(FastField<?> owner){super(owner);}
        public void copy(Object a,Object b){owner.writer.setBoolean(b,owner.reader.getBoolean(a));}
    }

}
