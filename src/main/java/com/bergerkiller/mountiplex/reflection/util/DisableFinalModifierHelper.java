package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Tries it very best to disable the final modifier of a field so that final static
 * fields can be modified at runtime.
 */
public class DisableFinalModifierHelper {
    private static final RemoveFinalModifierMethod REMOVE_FINAL_MODIFIER_METHOD;

    static {
        RemoveFinalModifierMethod method = new RemoveFinalModifierUsingReflection();
        if (!method.valid()) {
            method = new RemoveFinalModifierUsingConstructorHack();
        }
        if (!method.valid()) {
            method = new RemoveFinalModifierUnsupported();
        }
        REMOVE_FINAL_MODIFIER_METHOD = method;
    }

    public static void removeFinalModifier(java.lang.reflect.Field field) throws IllegalAccessException {
        REMOVE_FINAL_MODIFIER_METHOD.remove(field);
    }

    // Required for Java 12
    private static class RemoveFinalModifierUsingConstructorHack implements RemoveFinalModifierMethod {
        private final Object reflectionFactory;
        private final java.lang.reflect.Method newFieldAccessorMethod;
        private final java.lang.reflect.Method setFieldAccessorMethod;
        private final java.lang.reflect.Field signatureField;
        private final java.lang.reflect.Field annotationsField;
        private final java.lang.reflect.Field slotField;
        private final java.lang.reflect.Constructor<?> fieldConstr;

        public RemoveFinalModifierUsingConstructorHack() {
            Object ref_o = null;
            java.lang.reflect.Method newfa_m = null,
                                     setfa_m = null;
            java.lang.reflect.Field sig_f = null,
                                    ann_f = null,
                                    slt_f = null;
            java.lang.reflect.Constructor<?> fld_c = null;
            try {
                // Retrieve ReflectionFactory instance
                Field refFactoryField = AccessibleObject.class.getDeclaredField("reflectionFactory");
                refFactoryField.setAccessible(true);
                ref_o = refFactoryField.get(null);
                refFactoryField.setAccessible(false);

                // Obtain the newFieldAccessor method
                newfa_m = ref_o.getClass().getDeclaredMethod("newFieldAccessor", 
                        java.lang.reflect.Field.class, boolean.class);
                newfa_m.setAccessible(true);

                // Obtain the Field setFieldAccessor method
                setfa_m = java.lang.reflect.Field.class.getDeclaredMethod("setFieldAccessor",
                        Class.forName("sun.reflect.FieldAccessor"), boolean.class);
                setfa_m.setAccessible(true);

                // A bunch of fields and the main Field constructor we need
                sig_f = java.lang.reflect.Field.class.getDeclaredField("signature");
                ann_f = java.lang.reflect.Field.class.getDeclaredField("annotations");
                slt_f = java.lang.reflect.Field.class.getDeclaredField("slot");
                fld_c = java.lang.reflect.Field.class.getDeclaredConstructor(
                        Class.class, String.class, Class.class,
                        int.class, int.class, String.class, byte[].class);
                sig_f.setAccessible(true);
                ann_f.setAccessible(true);
                slt_f.setAccessible(true);
                fld_c.setAccessible(true);
            } catch (Throwable t) {
                // t.printStackTrace();
            }
            this.reflectionFactory = ref_o;
            this.newFieldAccessorMethod = newfa_m;
            this.setFieldAccessorMethod = setfa_m;
            this.signatureField = sig_f;
            this.annotationsField = ann_f;
            this.slotField = slt_f;
            this.fieldConstr = fld_c;
        }

        @Override
        public void remove(Field field) throws IllegalAccessException {
            if ((field.getModifiers() & Modifier.FINAL) == 0) {
                return;
            }
            try {
                // Create a copy of the Field object with FINAL removed from modifiers
                Field fieldWithChangedModifiers = (Field) this.fieldConstr.newInstance(
                        field.getDeclaringClass(),
                        field.getName(),
                        field.getType(),
                        field.getModifiers() & ~Modifier.FINAL,
                        this.slotField.get(field),
                        this.signatureField.get(field),
                        this.annotationsField.get(field));
                
                // Use this field object to create a field accessor
                Object fieldAccessor = this.newFieldAccessorMethod.invoke(this.reflectionFactory,
                        fieldWithChangedModifiers, true);

                // Apply the field accessor to the input field, which will now use it instead
                this.setFieldAccessorMethod.invoke(field, fieldAccessor, true);
            } catch (Throwable t) {
                throw new IllegalAccessException("Failed to disable final modifier");
            }
        }

        @Override
        public boolean valid() {
            return this.reflectionFactory != null &&
                   this.newFieldAccessorMethod != null &&
                   this.setFieldAccessorMethod != null &&
                   this.signatureField != null &&
                   this.slotField != null &&
                   this.annotationsField != null &&
                   this.fieldConstr != null;
        }
    }

    // Works fine on most versions of Java, up until they disabled retrieving the modifiers field
    private static class RemoveFinalModifierUsingReflection implements RemoveFinalModifierMethod {
        private final java.lang.reflect.Field _modifiersField;
        private final java.lang.reflect.Method _setFieldAccessorMethod;

        public RemoveFinalModifierUsingReflection() {
            java.lang.reflect.Field mod_f = null;
            java.lang.reflect.Method setfa_m = null;
            try {
                mod_f = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                setfa_m = java.lang.reflect.Field.class.getDeclaredMethod("setFieldAccessor",
                        Class.forName("sun.reflect.FieldAccessor"), boolean.class);
            } catch (Throwable t) {}        
            this._modifiersField = mod_f;
            this._setFieldAccessorMethod = setfa_m;
        }

        @Override
        public void remove(Field field) throws IllegalAccessException {
            // Set cached FieldAccessor object to null to force a re-initialization of it
            // This way, if someone else used reflection to get/set before, it won't fail
            try {
                this._setFieldAccessorMethod.setAccessible(true);
                this._setFieldAccessorMethod.invoke(field, null, true);
                this._setFieldAccessorMethod.setAccessible(false);
            } catch (Throwable t) {
                throw new IllegalAccessException("Failed to clean up previous field accessor");
            }

            // Set the actual modifiers field
            this._modifiersField.setAccessible(true);
            this._modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            this._modifiersField.setAccessible(false);
        }

        @Override
        public boolean valid() {
            return this._modifiersField != null && this._setFieldAccessorMethod != null;
        }
    }

    private static class RemoveFinalModifierUnsupported implements RemoveFinalModifierMethod {
        @Override
        public void remove(Field field) throws IllegalAccessException {
            throw new IllegalAccessException("Changing the value of final fields is not supported on this JVM");
        }

        @Override
        public boolean valid() {
            return false;
        }
    }

    private static interface RemoveFinalModifierMethod {
        void remove(java.lang.reflect.Field field) throws IllegalAccessException;
        boolean valid();
    }
}
