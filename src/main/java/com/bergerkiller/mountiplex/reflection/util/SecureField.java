package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

public final class SecureField {
    private java.lang.reflect.Field field;
    private boolean canGet, canSet;

    public SecureField() {
        this.field = null;
        this.canGet = false;
        this.canSet = false;
    }

    /**
     * Initializes what field is stored in this Secure Field
     * 
     * @param field to set to
     */
    public final void init(java.lang.reflect.Field field) {
        this.field = field;
        this.canGet = false;
        this.canSet = false;
    }

    /**
     * De-initializes this field
     */
    public final void deinit() {
        this.field = null;
        this.canGet = false;
        this.canSet = false;
    }

    /**
     * Checks whether this field has been initialized
     * 
     * @return true if initialized
     */
    public final boolean isInit() {
        return this.field != null;
    }

    /**
     * Gets the type of field
     * 
     * @return field type
     */
    public Class<?> getType() {
        return this.field.getType();
    }

    /**
     * Gets the field, not doing any accessibility checks
     * 
     * @return field
     */
    public final java.lang.reflect.Field get() {
        return this.field;
    }

    /**
     * Makes sure the field can be read, making it accessible if required.
     * Returns the field.
     * 
     * @return field
     */
    public final java.lang.reflect.Field read() {
        if (this.field == null) {
            throw new RuntimeException("Field is not initialized");
        }
        if (!this.canGet) {
            if (!field.isAccessible() && !Modifier.isPublic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to make field " + MPLType.getName(field) + " accessible");
                }
            }
            this.canGet = true;
        }
        return this.field;
    }

    /**
     * Makes sure the field can be written, making it accessible if required.
     * Returns the field.
     * 
     * @return field
     */
    public final java.lang.reflect.Field write() {
        if (this.field == null) {
            throw new RuntimeException("Field is not initialized");
        }
        if (!this.canSet) {
            if (!field.isAccessible()) {
                try {
                    field.setAccessible(true);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to make field " + MPLType.getName(field) + " accessible");
                }
            }
            this.canGet = true;
            this.canSet = true;
        }
        return this.field;
    }

    /**
     * Checks whether the field is properly initialized
     */
    public final void checkInit() {
        if (this.field == null) {
            throw new RuntimeException("Field is not initialized");
        }
    }

    /**
     * Checks whether a value can be gotten from this field in its current state
     */
    public final void checkGet() {
        checkInit();
    }

    /**
     * Checks whether a value can be set in this Field.
     * Throws an exception when it can not.
     * 
     * @param value to set to
     */
    public final void checkSet(Object value) {
        checkInit();
        java.lang.Class<?> valueType = this.field.getType();
        if (valueType.isPrimitive() && value == null) {
            throw new IllegalArgumentException("Field primitive type " + valueType.getName() + " can not be assigned null");
        }
        if (value != null && valueType.isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException("value type " + MPLType.getName(value.getClass()) +
                    " can not be assigned to field type " + MPLType.getName(valueType));
        }
    }

    /**
     * Checks whether an instance is valid for this field.
     * Throws an exception when it is not.
     * 
     * @param instance to check
     */
    public final void checkInstance(Object instance) {
        if (this.field == null) {
            return;
        }
        if (Modifier.isStatic(this.field.getModifiers())) {
            if (instance != null) {
                throw new IllegalArgumentException("Field is static, no instance should be used");
            }
        } else {
            if (instance == null) {
                throw new IllegalArgumentException("Instance is null");
            }
            java.lang.Class<?> type = this.field.getDeclaringClass();
            if (!type.isAssignableFrom(instance.getClass())) {
                throw new IllegalArgumentException("Failed to get field: instance is not an instance of " + MPLType.getName(type));
            }
        }
    }
}
