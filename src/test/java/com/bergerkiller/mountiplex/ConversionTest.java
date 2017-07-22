package com.bergerkiller.mountiplex;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.annotations.ConverterMethod;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.types.AnnotatedConverters;
import com.bergerkiller.mountiplex.types.CustomListType;
import com.bergerkiller.mountiplex.types.CustomSetType;
import com.bergerkiller.mountiplex.types.CustomType;
import com.bergerkiller.mountiplex.types.TestObject;
import com.bergerkiller.mountiplex.types.UniqueType;
import com.bergerkiller.mountiplex.types.UniqueTypeExtension1;
import com.bergerkiller.mountiplex.types.UniqueTypeWrap;

public class ConversionTest {

    static {
        Conversion.registerConverters(ConversionTest.class);
    }

    @Test
    public void testCustomType() {
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
    public void testBooleanNumber() {
        testConversion(1, boolean.class, true);
        testConversion(0, boolean.class, false);
        testConversion((byte) 1, boolean.class, true);
        testConversion((byte) 0, boolean.class, false);
        testConversion(12, boolean.class, true);
        testConversion((short) 24, boolean.class, true);
        testConversion((short) 0, boolean.class, false);
        testConversion(false, int.class, 0);
        testConversion(true, int.class, 1);
        testConversion(false, byte.class, (byte) 0);
        testConversion(true, byte.class, (byte) 1);
        testConversion(false, short.class, (short) 0);
        testConversion(true, short.class, (short) 1);
    }

    @Test
    public void testNumberDefault() {
        Converter<String, Integer> conv = Conversion.find(String.class, int.class);
        assertNotNull(conv);
        assertEquals(12, conv.convert("helloworld", 12).intValue());
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
        TypeDeclaration tStringList = TypeDeclaration.parse("List<String>");
        TypeDeclaration tIntegerList = TypeDeclaration.parse("List<Integer>");

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
        assertCollectionSame(result, "30", "50");
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
        assertCollectionSame(result, 30, 50);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testArrayToList() {
        String[] valuesArray = new String[] {"12", "55", "534", "-6633", "633"};
        ArrayList<Integer> valuesList = new ArrayList<Integer>(Arrays.asList(12, 55, 534, -6633, 633));

        TypeDeclaration t_arr = TypeDeclaration.parse("String[]");
        TypeDeclaration t_lst = TypeDeclaration.parse("List<Integer>");

        // Verify both can be duplex converted to one another
        assertFindConverter(t_arr, t_lst);
        assertFindConverter(t_lst, t_arr);
        DuplexConverter<Object, Object> conv = Conversion.findDuplex(t_arr, t_lst);
        assertNotNull(conv);

        List<Integer> valuesListResult = (List<Integer>) conv.convert(valuesArray);
        String[] valuesArrayResult = (String[]) conv.convertReverse(valuesList);
        assertNotNull(valuesListResult);
        assertNotNull(valuesArrayResult);

        assertCollectionSame(valuesListResult, (Object[]) valuesList.toArray(new Integer[0]));
        assertCollectionSame(Arrays.asList(valuesArrayResult), (Object[]) valuesArray);
    }
    
    @Test
    public void testMapValues() {
        TypeDeclaration tIntegerStringMap = TypeDeclaration.parse("Map<Integer, String>");
        TypeDeclaration tIntegerIntegerMap = TypeDeclaration.parse("Map<Integer, Integer>");

        Map<Integer, String> testMap = new HashMap<Integer, String>();
        testMap.put(12, "45");
        testMap.put(13, "55");
        testMap.put(0, "66");

        Map<Integer, Integer> result = assertTypedConvert(tIntegerStringMap, tIntegerIntegerMap, testMap);
        assertEquals(3, result.size());
        assertTrue(result.containsKey(12));
        assertTrue(result.containsKey(13));
        assertTrue(result.containsKey(0));
        assertFalse(result.containsKey(55));
        assertTrue(result.containsValue(45));
        assertTrue(result.containsValue(55));
        assertTrue(result.containsValue(66));
        assertFalse(result.containsValue(12));
        assertEquals(45, result.get(12).intValue());
        assertEquals(55, result.get(13).intValue());
        assertEquals(66, result.get(0).intValue());
        assertCollectionSame(result.keySet(), 0, 12, 13);
        assertCollectionSame(result.values(), 45, 55, 66);
    }

    @Test
    public void testMapKeys() {
        TypeDeclaration tStringStringMap = TypeDeclaration.parse("Map<String, String>");
        TypeDeclaration tIntegerStringMap = TypeDeclaration.parse("Map<Integer, String>");

        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("44", "55");
        testMap.put("60", "62");
        testMap.put("80", "39");

        Map<Integer, String> result = assertTypedConvert(tStringStringMap, tIntegerStringMap, testMap);
        assertEquals(3, result.size());
        assertTrue(result.containsKey(44));
        assertTrue(result.containsKey(60));
        assertTrue(result.containsKey(80));
        assertFalse(result.containsKey(90));
        assertTrue(result.containsValue("55"));
        assertTrue(result.containsValue("62"));
        assertTrue(result.containsValue("39"));
        assertFalse(result.containsValue("44"));
        assertEquals(result.get(44), "55");
        assertEquals(result.get(60), "62");
        assertEquals(result.get(80), "39");
        assertCollectionSame(result.keySet(), 44, 60, 80);
        assertCollectionSame(result.values(), "55", "62", "39");
    }

    @Test
    public void testAnnotated() {
        Conversion.registerConverters(AnnotatedConverters.class);

        Converter<UniqueTypeWrap, UniqueTypeExtension1> converter = Conversion.find(UniqueTypeWrap.class, UniqueTypeExtension1.class);
        assertNotNull(converter);
        UniqueTypeWrap wrap = new UniqueTypeWrap(new UniqueTypeExtension1());
        Object result = converter.convert(wrap);
        assertNotNull(result);
        assertEquals(result.getClass(), UniqueTypeExtension1.class);
    }

    @Test
    public void testObjectCast() {
        TestObject testObject = new TestObject();

        Converter<Object, TestObject> toTestObject = Conversion.find(Object.class, TestObject.class);
        Converter<TestObject, Object> toObject = Conversion.find(TestObject.class, Object.class);

        assertNotNull(toTestObject);
        assertNotNull(toObject);

        Object asTestObject = toTestObject.convert(testObject);
        Object asObject = toObject.convert(testObject);
        assertNotNull(asTestObject);
        assertNotNull(asObject);
        assertEquals(testObject, asTestObject);
        assertEquals(testObject, asObject);

        UniqueType someType = new UniqueType();
        assertNull(toTestObject.convert(someType));
        assertNull(toObject.convert(someType));
    }

    @Test
    public void testListFail() {
        TypeDeclaration listA = TypeDeclaration.parse("List<String>");
        TypeDeclaration listB = TypeDeclaration.parse("List<com.bergerkiller.mountiplex.types.TestObject>");
        Converter<?, ?> convAB = Conversion.find(listA, listB);
        Converter<?, ?> convBA = Conversion.find(listB, listA);

        //TODO: Fix this! Array Conversion is used because it provides an InputConverter to List
        // The actual type conversion checking for the element type is not performed by the conversion lookup
        // Kinda bad!
        if (convAB != null) {
            //fail("Found a converter AB that should not exist: " + convAB);
        }
        if (convBA != null) {
            //fail("Found a converter BA that should not exist: " + convBA);
        }
    }

    @Test
    public void testCustomList() {
        // We have an overload for CustomSetType<T> -> List<T>
        // It should use our own converter instead of the builtin list converter
        // This tests the correct type preference logic of Converter Providers
        TypeDeclaration setInput = TypeDeclaration.createGeneric(CustomSetType.class, String.class);
        TypeDeclaration listOutput = TypeDeclaration.createGeneric(List.class, String.class);
        Converter<Object, Object> conv = Conversion.find(setInput, listOutput);
        assertNotNull(conv);
        Object result = conv.convert(new CustomSetType<String>());
        assertNotNull(result);
        // Conversion.debugTree(setInput, listOutput);
        assertEquals(CustomListType.class, result.getClass());
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
    public static <T> List<T> customTypeToSet(CustomSetType<T> input) {
        return new CustomListType<T>(input);
    }

    @ConverterMethod
    public static CustomType stringToCustom(String input) {
        return new CustomType(input);
    }

    @SuppressWarnings("unchecked")
    private static <T> T assertTypedConvert(TypeDeclaration input, TypeDeclaration output, Object value) {
        Converter<Object, Object> converter = assertFindConverter(input, output);
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

    private static Converter<Object, Object> assertFindConverter(TypeDeclaration input, TypeDeclaration output) {
        Converter<Object, Object> converter = Conversion.find(input, output);
        if (converter == null) {
            Conversion.debugTree(input, output);
            fail("Failed to find converter from " + input + " to " + output);
        }
        return converter;
    }

    private static void assertCollectionSame(Collection<?> collection, Object... values) {
        assertEquals(values.length, collection.size());
        for (Object value : values) {
            if (!collection.contains(value)) {
                System.out.println("COLLECTION TYPE: " + collection.getClass().getName());
                for (Object v : values) {
                    System.out.println("VALUES: " + v + " " + v.getClass());
                }
                for (Object c : collection) {
                    System.out.println("COLLECTION: " + c + " " + c.getClass());
                }
            }
            assertTrue(collection.contains(value));
        }
        for (Object value : collection) {
            assertTrue(Arrays.asList(values).contains(value));
        }
        assertTrue(collection.containsAll(Arrays.asList(values)));
        assertTrue(Arrays.asList(values).containsAll(collection));
        ArrayList<Object> items = new ArrayList<Object>(collection);
        for (Object value : values) {
            assertTrue(items.remove(value));
        }
        assertEquals(0, items.size());
    }
}
