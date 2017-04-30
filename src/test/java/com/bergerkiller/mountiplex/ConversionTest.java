package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.annotations.ConverterMethod;
import com.bergerkiller.mountiplex.conversion2.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.types.CustomType;

public class ConversionTest {

    @Test
    public void testCustomType() {
        Conversion.registerConverters(ConversionTest.class);

        CustomType type = Conversion.find(String.class, CustomType.class).convert("test");
        assertNotNull(type);
        assertEquals(type.member, "test");
    }

    @Test
    public void testNumber() {
        // Casting numbers to other number types
        testConversion(12.5, int.class, 12);
        testConversion(20L, short.class, (short) 20);
        testConversion("123.0", int.class, 123);
        testConversion(20.0, long.class, 20L);

        // Parsing numbers from String
        testConversion("123", int.class, 123);
        testConversion("123", Integer.class, 123);
        testConversion("123", double.class, 123.0);
        testConversion("123.0", double.class, 123.0);
        testConversion("123", float.class, 123.0f);
        testConversion("123.0", float.class, 123.0f);
        testConversion("123", byte.class, (byte) 123);
        testConversion(false, String.class, "false");

        // Converting to a 'Number' should return the best fit type
        testConversion("123.0", Number.class, 123.0f);
        testConversion("123.02562", Number.class, 123.02562);
        testConversion("123", Number.class, (byte) 123);
        testConversion("12345", Number.class, (short) 12345);
        testConversion("12345678", Number.class, (int) 12345678);
        testConversion("123456789012345", Number.class, (long) 123456789012345L);

        // Verify invalid parsed numbers are either null, or their default value
        testConversion("INVALID", Double.class, null);
        testConversion("INVALID", double.class, 0.0);
        testConversion("INVALID", Integer.class, null);
        testConversion("INVALID", int.class, 0);

        // Verify numbers can still be parsed when text is around it
        testConversion("Text: 12", int.class, 12);
        testConversion("Awesome 15 66 33", int.class, 15);
        testConversion("Awesome 17 Test", int.class, 17);
    }

    @Test
    public void testEnum() {
        testConversion("SUNDAY", Day.class, Day.SUNDAY);
        testConversion("MONDAY", Day.class, Day.MONDAY);
        testConversion("MON", Day.class, Day.MONDAY);
        testConversion("TUESDAYNIGHT", Day.class, Day.TUESDAY);
        testConversion("WRONG", Day.class, null);
        testConversion(0, Day.class, Day.SUNDAY);
        testConversion(1, Day.class, Day.MONDAY);
        testConversion(5, Day.class, Day.FRIDAY);
        testConversion(-1, Day.class, null);
        testConversion(10, Day.class, null);
        testConversion((short) 2, Day.class, Day.TUESDAY); // should understand short -> int
    }

    @Test
    public void testList() {
        TypeDeclaration tStringList = new TypeDeclaration(ClassResolver.DEFAULT, "List<String>");
        TypeDeclaration tIntegerList = new TypeDeclaration(ClassResolver.DEFAULT, "List<Integer>");

        List<String> numberStrings = new ArrayList<String>();
        numberStrings.add("12");
        numberStrings.add("24");

        List<Integer> result = assertTypedConvert(tStringList, tIntegerList, numberStrings);
        assertEquals(result.size(), 2);
        assertEquals(result.get(0).intValue(), 12);
        assertEquals(result.get(1).intValue(), 24);
    }

    @Test
    public void testListToSet() {
        TypeDeclaration tStringList = TypeDeclaration.parse("List<String>");
        TypeDeclaration tStringSet = TypeDeclaration.parse("Set<String>");

        List<String> numberStrings = new ArrayList<String>();
        numberStrings.add("30");
        numberStrings.add("50");
        numberStrings.add("50");

        Set<String> result = assertTypedConvert(tStringList, tStringSet, numberStrings);
        assertEquals(result.size(), 2);
        assertTrue(result.contains("30"));
        assertTrue(result.contains("50"));
    }

    @Test
    public void testStringListToIntegerSet() {
        TypeDeclaration tStringList = TypeDeclaration.parse("List<String>");
        TypeDeclaration tIntegerSet = TypeDeclaration.parse("Set<Integer>");

        List<String> numberStrings = new ArrayList<String>();
        numberStrings.add("30");
        numberStrings.add("50");
        numberStrings.add("50");

        Set<Integer> result = assertTypedConvert(tStringList, tIntegerSet, numberStrings);
        assertEquals(result.size(), 2);
        assertTrue(result.contains(30));
        assertTrue(result.contains(50));
    }

    private static enum Day {
        SUNDAY, MONDAY, TUESDAY, WEDNESDAY,
        THURSDAY, FRIDAY, SATURDAY 
    }

    private static void testConversion(Object input, Class<?> toType, Object expectedResult) {
        InputConverter<?> converter = Conversion.find(toType);
        assertNotNull("Failed to find a converter to type " + toType.getName(), converter);
        if (!converter.canConvert(input.getClass())) {
            Conversion.debugTree(input.getClass(), toType);
            fail("Can't convert from " + input.getClass().getName() + " to " + toType.getName());
        }
        Object result = converter.convert(input, null);
        if (((result == null) != (expectedResult == null)) ||
                (result != null && !result.equals(expectedResult))) {
            Conversion.debugTree(input.getClass(), toType);
            fail("Expected " + expectedResult + ", but was " + result);
        }

        // Log successes too
        //Conversion.debugTree(input.getClass(), toType);
    }

    @ConverterMethod
    public static CustomType stringToCustom(String input) {
        return new CustomType(input);
    }

    @SuppressWarnings("unchecked")
    private static <T> T assertTypedConvert(TypeDeclaration input, TypeDeclaration output, Object value) {
        Converter<Object, Object> converter = Conversion.find(input, output);
        if (converter == null) {
            Conversion.debugTree(input, output);
            fail("Failed to find converter from " + input + " to " + output);
        }
        Object result = converter.convert(value);
        if (result == null) {
            Conversion.debugTree(input, output);
            fail("Failed to convert from " + input + " to " + output);
        }
        if (!output.isAssignableFrom(result)) {
            Conversion.debugTree(input, output);
            fail("Converter produced an output that is incorrect!: " + result.getClass().getName());
        }

        //Conversion.debugTree(input, output);
        return (T) result;
    }
}
