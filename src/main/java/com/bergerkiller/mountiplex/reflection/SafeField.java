package com.bergerkiller.mountiplex.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Wraps around the java.lang.reflect.Field class to provide an error-free
 * alternative<br>
 * Exceptions are logged, isValid can be used to check if the Field is actually
 * working
 *
 * @param <T> type of the Field
 */
public class SafeField<T> implements FieldAccessor<T> {
    private final FastField<T> field;

    public SafeField(FastField<T> field) {
        if (field == null) {
            throw new IllegalArgumentException("Backing field can not be null");
        }
        this.field = field;
    }

    public SafeField(Field field) {
        this.field = new FastField<T>();
        this.field.init(field);
    }

    public SafeField(String fieldPath, Class<?> fieldType) {
        this.field = new FastField<T>();
        if (fieldPath == null || fieldPath.isEmpty() || !fieldPath.contains(".")) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Field path contains no class: " + fieldPath);
            return;
        }
        try {
            String className = MountiplexUtil.getLastBefore(fieldPath, ".");
            String fieldName = fieldPath.substring(className.length() + 1);
            Class<?> type = Resolver.loadClass(className, false);
            load(type, fieldName, fieldType);
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to load field '" + fieldPath + "'", t);
        }
    }

    public SafeField(Object value, String name, Class<?> fieldType) {
        this.field = new FastField<T>();
        load(value == null ? null : value.getClass(), name, fieldType);
    }

    public SafeField(Class<?> source, String name, Class<?> fieldType) {
        this.field = new FastField<T>();
        load(source, name, fieldType);
    }

    private void load(Class<?> source, String name, Class<?> fieldType) {
        if (source == null) {
            MountiplexUtil.LOGGER.log(Level.WARNING, "Can not load field '" + name + "' because the class is null!", new Exception());
            return;
        }
        // try to find the field
        String fixedName = Resolver.resolveFieldName(source, name);
        String dispName = name.equals(fixedName) ? name : (name + ":" + fixedName);
        this.field.init(findRaw(source, fixedName));
        if (this.field.getField() == null) {
            if (fieldType == null) {
                this.field.initUnavailable("[???] " + source.getSimpleName() + "::" + name + "[???]");
            } else {
                this.field.initUnavailable(fieldType.getSimpleName() + " " + source.getSimpleName() + "::" + name + "[???]");
            }
        } else {
            if (fieldType == null) {
                return; // no check
            }

            Class<?> realType = this.field.getType();
            if (realType.equals(fieldType)) {
                return; // All good!
            }
            if (BoxedType.tryBoxType(realType).equals(BoxedType.tryBoxType(fieldType))) {
                return; // The input type is only wrong because of boxing/unboxing, we ignore this
            }

            MountiplexUtil.LOGGER.log(Level.WARNING, "Field '" + name + "'" +
                    " in class " + MPLType.getName(source) +
                    " is of type " + realType.getSimpleName() +
                    " while we expect type " + fieldType.getSimpleName());
            this.field.initUnavailable(fieldType.getSimpleName() + "[?] " + source.getSimpleName() + "::" + name);
        }
        MountiplexUtil.LOGGER.warning("Field '" + dispName + "' could not be found in class " + MPLType.getName(source));
    }

    @Override
    public boolean isValid() {
        return this.field.getField() != null;
    }

    /**
     * Gets whether this Field is a static Field
     *
     * @return True if static, False if not
     */
    public boolean isStatic() {
        return this.field.isStatic();
    }

    @Override
    public T transfer(Object from, Object to) {
        if (this.field.getField() == null) {
            return null;
        }
        T old = get(to);
        set(to, get(from));
        return old;
    }

    @Override
    public T get(Object object) {
        try {
            return this.field.get(object);
        } catch (RuntimeException ex) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to get field " + this.field.getDescription(), ex);
            return null;
        }
    }

    @Override
    public boolean set(Object object, T value) {
        try {
            this.field.set(object, value);
            return true;
        } catch (RuntimeException ex) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to set field " + this.field.getDescription(), ex);
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(20);
        java.lang.reflect.Field f = this.field.getField();
        if (f == null) {
            return "null";
        }
        final int mod = f.getModifiers();
        if (Modifier.isPublic(mod)) {
            text.append("public ");
        } else if (Modifier.isPrivate(mod)) {
            text.append("private ");
        } else if (Modifier.isProtected(mod)) {
            text.append("protected ");
        }
        if (Modifier.isStatic(mod)) {
            text.append("static ");
        }
        String fieldTypeName = MPLType.getName(f.getType());
        String fieldName = MPLType.getName(f);
        return text.append(fieldTypeName).append(" ").append(fieldName).toString();
    }

    /**
     * Gets the name of this field as declared in the Class
     *
     * @return Field name
     */
    public String getName() {
        return field.getName();
    }

    /**
     * Gets the Class type of this field as declared inthe Class
     *
     * @return Field type
     */
    public Class<?> getType() {
        return field.getType();
    }

    /**
     * Obtains the underlying Fast Field instance that is used to safely access the field
     * 
     * @return fast field
     */
    public FastField<T> getFastField() {
        return this.field;
    }

    @Override
    public <K> TranslatorFieldAccessor<K> translate(DuplexConverter<?, K> converterPair) {
        return new TranslatorFieldAccessor<K>(this, converterPair);
    }

    @Override
    public FieldAccessor<T> ignoreInvalid(T defaultValue) {
        if (this.isValid()) {
            return this;
        } else {
            return new IgnoredFieldAccessor<T>(defaultValue);
        }
    }

    /**
     * Tries to set a Field for a certain Object
     *
     * @param source to set a Field for
     * @param fieldname to set
     * @param value to set to
     */
    @SuppressWarnings("unchecked")
	public static <T> void set(Object source, String fieldname, T value) {
    	if (value == null) {
    		new SafeField<T>(source, fieldname, null).set(source, value);
    	} else {
    		new SafeField<T>(source, fieldname, (Class<T>) value.getClass()).set(source, value);
    	}
    }

    /**
     * Tries to set a static Field in a certain Class
     *
     * @param clazz to set the static field in
     * @param fieldname of the static field
     * @param value to set to
     */
    @SuppressWarnings("unchecked")
	public static <T> void setStatic(Class<?> clazz, String fieldname, T value) {
    	if (value == null) {
    		new SafeField<T>(clazz, fieldname, null).set(null, value);
    	} else {
    		new SafeField<T>(clazz, fieldname, (Class<T>) value.getClass()).set(null, value);
    	}
    }

    /**
     * Tries to get a Field from a certain Object
     *
     * @param source to get the Field from
     * @param fieldname to get
     * @return The Field value, or null if not possible
     */
    public static <T> T get(Object source, String fieldname, Class<T> fieldType) {
        return new SafeField<T>(source, fieldname, fieldType).get(source);
    }

    /**
     * Tries to get a static Field from a class
     *
     * @param clazz to get the field value for
     * @param fieldname of the field value
     * @return The Field value, or null if not possible
     */
    public static <T> T get(Class<?> clazz, String fieldname, Class<T> fieldType) {
        return new SafeField<T>(clazz, fieldname, fieldType).get(null);
    }

    /**
     * Creates a new SafeField instance pointing to the field found in the given
     * class type
     *
     * @param type - class type to find the field in
     * @param fieldname - field name
     * @return new SafeField
     */
    public static <T> SafeField<T> create(Class<?> type, String fieldname, Class<T> fieldType) {
        return new SafeField<T>(type, fieldname, fieldType);
    }

    /**
     * Creates a new SafeField instance pointing to the field found in the given
     * class type. Exposes to the outside world using a translator.
     *
     * @param type - class type to find the field in
     * @param name - field name
     * @param converterPair - used to convert between exposed and stored types
     * @return new TranslatorFieldAccessor backed by a SafeField
     */
    public static <T> TranslatorFieldAccessor<T> create(Class<?> type, String name, DuplexConverter<?, T> converterPair) {
        return create(type, name, converterPair.output.type).translate(converterPair);
    }

    /**
     * Checks whether a certain field is available in a Class
     *
     * @param type of Class
     * @param name of the field
     * @return True if available, False if not
     */
    public static boolean contains(Class<?> type, String name, Class<?> fieldType) {
    	Field f = findRaw(type, Resolver.resolveFieldName(type, name));
    	return f != null && (fieldType == null || fieldType.equals(f.getType()));
    }

    /**
     * Tries to recursively find a field in a Class
     *
     * @param type of Class
     * @param fieldName of the field
     * @return the Field, or null if not found
     */
    private static Field findRaw(Class<?> type, String fieldName) {
        Class<?> tmp = type;
        // Try to find the field in the current and all Super Classes
        while (tmp != null) {
            try {
                return MPLType.getDeclaredField(tmp, fieldName);
            } catch (NoSuchFieldException ex) {
                tmp = tmp.getSuperclass();
            }
        }
        // Interfaces don't contain fields, so nothing found at this point
        return null;
    }

    /**
     * Creates a safe field that is backed by nothing, indicating a field that could not be found
     * 
     * @param missingInfo information to go with the missing field
     * @return null field
     */
    public static <T> SafeField<T> createNull(String missingInfo) {
        FastField<T> ff = new FastField<T>();
        ff.initUnavailable(missingInfo);
        return new SafeField<T>(ff);
    }

    /**
     * Creates a safe field that is backed by nothing, indicating a field that could not be found.<br>
     * <b>Deprecated: </b>recommended is using {@link #createNull(missingInfo)} instead
     * 
     * @return null field
     */
    @Deprecated
    public static <T> SafeField<T> createNull() {
        return new SafeField<T>((Field) null);
    }
}
