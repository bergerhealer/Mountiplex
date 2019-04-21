package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Tries it very best to disable the final modifier of a field so that final static
 * fields can be modified at runtime.
 */
public class DisableFinalModifierHelper {
    private static final RemoveFinalModifierMethod REMOVE_FINAL_MODIFIER_METHOD;

    static {
        RemoveFinalModifierMethod method = null;
        if (method == null) {
            try {
                method = new RemoveFinalModifierUsingReflection();
            } catch (Throwable t) {
                // t.printStackTrace();
            }
        }
        if (method == null) {
            try {
                method = new RemoveFinalModifierUsingUnsafe();
            } catch (Throwable t) {
                // t.printStackTrace();
            }
        }
        if (method == null) {
            method = new RemoveFinalModifierUnsupported();
        }
        REMOVE_FINAL_MODIFIER_METHOD = method;
    }

    public static void removeFinalModifier(java.lang.reflect.Field field) throws IllegalAccessException {
        REMOVE_FINAL_MODIFIER_METHOD.remove(field);
    }

    // Required for Java 12. (Ab)uses Unsafe to get around the permission manager to do this.
    private static class RemoveFinalModifierUsingUnsafe implements RemoveFinalModifierMethod {
        private final Object unsafe;
        private final java.lang.reflect.Method _setFieldAccessorMethod;

        public RemoveFinalModifierUsingUnsafe() throws Throwable {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field f = unsafeType.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            this.unsafe = f.get(null);

            java.lang.reflect.Method acquire = java.lang.reflect.Field.class.getDeclaredMethod("acquireFieldAccessor", boolean.class);
            this._setFieldAccessorMethod = java.lang.reflect.Field.class.getDeclaredMethod("setFieldAccessor",
                    acquire.getReturnType(), boolean.class);
        }

        @Override
        @SuppressWarnings("restriction")
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

            // Switch modifier field to without final
            final long field_offset = 24L;
            sun.misc.Unsafe u = (sun.misc.Unsafe) unsafe;
            int old_value = u.getInt(field, field_offset);
            if (old_value != field.getModifiers()) {
                throw new IllegalAccessException("Expected old modifier field value is incorrect");
            }
            int new_value = old_value & ~Modifier.FINAL;
            if (old_value != new_value) {
                u.putInt(field, field_offset, new_value);
                if (field.getModifiers() != new_value) {
                    u.putInt(field, field_offset, old_value);
                    throw new IllegalAccessException("Expected new modifier field value is incorrect");
                }
            }
        }
    }

    // Works fine on most versions of Java, up until they disabled retrieving the modifiers field
    private static class RemoveFinalModifierUsingReflection implements RemoveFinalModifierMethod {
        private final java.lang.reflect.Field _modifiersField;
        private final java.lang.reflect.Method _setFieldAccessorMethod;

        public RemoveFinalModifierUsingReflection() throws Throwable {
            this._modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            java.lang.reflect.Method acquire = java.lang.reflect.Field.class.getDeclaredMethod("acquireFieldAccessor", boolean.class);
            this._setFieldAccessorMethod = java.lang.reflect.Field.class.getDeclaredMethod("setFieldAccessor",
                    acquire.getReturnType(), boolean.class);
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
    }

    // This old code doesn't work on Java 12 because the 'slot' (and annotations) field is not accessible
    // I could find no way to retrieve this value without using Unsafe
    /*
    // Required for Java 12
    private static class RemoveFinalModifierUsingConstructorHack implements RemoveFinalModifierMethod {
        //private final Object reflectionFactory;
        //private final java.lang.reflect.Method newFieldAccessorMethod;
        private final java.lang.reflect.Method acquireFieldAccessorMethod;
        private final java.lang.reflect.Method setFieldAccessorMethod;
        private final java.lang.reflect.Method getGenericSignatureMethod;
        //private final java.lang.reflect.Field slotField;
        private final java.lang.reflect.Constructor<?> fieldConstr;

        public RemoveFinalModifierUsingConstructorHack() {
            //Object ref_o = null;
            java.lang.reflect.Method acqfa_m = null,
                                     setfa_m = null,
                                     getsig_m = null;
            java.lang.reflect.Field slt_f = null;
            java.lang.reflect.Constructor<?> fld_c = null;
            try {
                // Retrieve ReflectionFactory instance
 
                //Field refFactoryField = AccessibleObject.class.getDeclaredField("reflectionFactory");
               // refFactoryField.setAccessible(true);
                //ref_o = refFactoryField.get(null);
               // refFactoryField.setAccessible(false);

                // Obtain the newFieldAccessor method
                //newfa_m = ref_o.getClass().getDeclaredMethod("newFieldAccessor", 
                //        java.lang.reflect.Field.class, boolean.class);
                //newfa_m.setAccessible(true);

                // Obtain the Field method
                acqfa_m = java.lang.reflect.Field.class.getDeclaredMethod("acquireFieldAccessor",
                        boolean.class);
                acqfa_m.setAccessible(true);

                // Obtain the Field setFieldAccessor method
                setfa_m = java.lang.reflect.Field.class.getDeclaredMethod("setFieldAccessor",
                        acqfa_m.getReturnType(), boolean.class);
                setfa_m.setAccessible(true);

                // Obtain the Field getGenericSignature method
                getsig_m = java.lang.reflect.Field.class.getDeclaredMethod("getGenericSignature");
                getsig_m.setAccessible(true);

                // A bunch of fields and the main Field constructor we need
                //slt_f = java.lang.reflect.Field.class.getDeclaredField("slot");
                fld_c = java.lang.reflect.Field.class.getDeclaredConstructor(
                        Class.class, String.class, Class.class,
                        int.class, int.class, String.class, byte[].class);
                fld_c.setAccessible(true);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            this.acquireFieldAccessorMethod = acqfa_m;
            this.setFieldAccessorMethod = setfa_m;
            this.getGenericSignatureMethod = getsig_m;
            //this.slotField = slt_f;
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
                        0,
                        this.getGenericSignatureMethod.invoke(field),
                        new byte[0]);

                //System.out.println("SLOT " + this.slotField.get(field));

                // Acquire field accessor using the custom modified Field
                Object fieldAccessor = this.acquireFieldAccessorMethod.invoke(fieldWithChangedModifiers, true);
                //UnsafeStaticObjectFieldAccessorImpl
                System.out.println("CLAAAS: " + fieldAccessor.getClass());

                // Apply the field accessor to the input field, which will now use it instead
                this.setFieldAccessorMethod.invoke(field, fieldAccessor, true);
            } catch (Throwable t) {
                throw new IllegalAccessException("Failed to disable final modifier");
            }
        }
    }
    */
    
    private static class RemoveFinalModifierUnsupported implements RemoveFinalModifierMethod {
        @Override
        public void remove(Field field) throws IllegalAccessException {
            throw new IllegalAccessException("Changing the value of final fields is not supported on this JVM");
        }
    }

    private static interface RemoveFinalModifierMethod {
        void remove(java.lang.reflect.Field field) throws IllegalAccessException;
    }
}
