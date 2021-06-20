package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionAccessor;
import com.bergerkiller.mountiplex.types.StaticFieldTestObject;

/**
 * Checks that generated field accessors for static final fields
 * work properly.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FieldAccessorTest {

    @Test
    public void testSetStaticFinalByteField() {
        final ReflectionAccessor<Byte> accessor = createAccessor("field_byte");
        accessor.setByte(null, (byte) 2);
        assertEquals((byte) 2, accessor.getByte(null));
        accessor.set(null, Byte.valueOf((byte) 3));
        assertEquals(Byte.valueOf((byte) 3), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Byte.valueOf((byte) 3), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalShortField() {
        final ReflectionAccessor<Short> accessor = createAccessor("field_short");
        accessor.setShort(null, (short) 2);
        assertEquals((short) 2, accessor.getShort(null));
        accessor.set(null, Short.valueOf((short) 3));
        assertEquals(Short.valueOf((short) 3), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Short.valueOf((short) 3), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalIntField() {
        final ReflectionAccessor<Integer> accessor = createAccessor("field_int");
        accessor.setInteger(null, 2);
        assertEquals(2, accessor.getInteger(null));
        accessor.set(null, Integer.valueOf(3));
        assertEquals(Integer.valueOf(3), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Integer.valueOf(3), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalLongField() {
        final ReflectionAccessor<Long> accessor = createAccessor("field_long");
        accessor.setLong(null, 2);
        assertEquals(2, accessor.getLong(null));
        accessor.set(null, Long.valueOf(3));
        assertEquals(Long.valueOf(3), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Long.valueOf(3), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalFloatField() {
        final ReflectionAccessor<Float> accessor = createAccessor("field_float");
        accessor.setFloat(null, 2.0f);
        assertEquals(2.0f, accessor.getFloat(null), 0.0f);
        accessor.set(null, Float.valueOf(3.0f));
        assertEquals(Float.valueOf(3.0f), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Float.valueOf(3.0f), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalDoubleField() {
        final ReflectionAccessor<Double> accessor = createAccessor("field_double");
        accessor.setDouble(null, 2.0);
        assertEquals(2.0, accessor.getDouble(null), 0.0);
        accessor.set(null, Double.valueOf(3.0));
        assertEquals(Double.valueOf(3.0), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Double.valueOf(3.0), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalCharField() {
        final ReflectionAccessor<Character> accessor = createAccessor("field_char");
        accessor.setCharacter(null, '2');
        assertEquals('2', accessor.getCharacter(null));
        accessor.set(null, Character.valueOf('3'));
        assertEquals(Character.valueOf('3'), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Character.valueOf('3'), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalBooleanField() {
        final ReflectionAccessor<Boolean> accessor = createAccessor("field_boolean");
        accessor.setBoolean(null, true);
        assertEquals(true, accessor.getBoolean(null));
        accessor.set(null, Boolean.valueOf(false));
        assertEquals(Boolean.valueOf(false), accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, "text"));

        // Make sure it didn't set anything anyway
        assertEquals(Boolean.valueOf(false), accessor.get(null));
    }

    @Test
    public void testSetStaticFinalStringField() {
        final ReflectionAccessor<String> accessor = createAccessor("field_string");
        accessor.set(null, "value1");
        assertEquals("value1", accessor.get(null));
        accessor.set(null, null);
        assertEquals(null, accessor.get(null));
        accessor.set(null, "value2");
        assertEquals("value2", accessor.get(null));

        // Check a bunch of random stuff throws properly, and doesn't do "unknown behavior"
        checkThrowsIllegalArgument(() -> accessor.setByte(null, (byte) 0));
        checkThrowsIllegalArgument(() -> accessor.setShort(null, (short) 0));
        checkThrowsIllegalArgument(() -> accessor.setInteger(null, 0));
        checkThrowsIllegalArgument(() -> accessor.setLong(null, 0L));
        checkThrowsIllegalArgument(() -> accessor.setFloat(null, 0.0f));
        checkThrowsIllegalArgument(() -> accessor.setDouble(null, 0.0));
        checkThrowsIllegalArgument(() -> accessor.setBoolean(null, false));
        checkThrowsIllegalArgument(() -> accessor.setCharacter(null, '0'));
        checkThrowsIllegalArgument(() -> ((ReflectionAccessor) accessor).set(null, new Object()));

        // Make sure it didn't set anything anyway
        assertEquals("value2", accessor.get(null));
    }

    // Note: as part of testing we expect that doing a set before the class is initialized,
    // initializes the class. That is why we do a set before we do a get, and we ignore
    // the initial values. If the following assert shows the initial value, then we know
    // it likely did not call the static initializer...
    private static <T> ReflectionAccessor<T> createAccessor(String name) {
        Field field;
        try {
            field = StaticFieldTestObject.class.getDeclaredField(name);
            field.setAccessible(true); // done by underlying setter in e.g. FastField
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to find field " + name);
        }
        return ReflectionAccessor.create(field);
    }

    // Checks that calling the method throws an appropriate IllegalArgumentException
    private static void checkThrowsIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            fail("No IllegalArgumentException was thrown!");
        } catch (IllegalArgumentException ex) { /* good */ }
    }
}
