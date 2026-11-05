package com.bergerkiller.mountiplex;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.type.DuplexConverter;
import com.bergerkiller.mountiplex.conversion.util.ConvertingList;

public class ConvertingListTest {

    @Test
    public void testWritesAreConvertedBack() {
        DuplexConverter<Integer, String> converter = Conversion.findDuplex(Integer.class, String.class);
        List<Integer> base = new ArrayList<Integer>(Arrays.asList(1, 2, 3));
        List<String> view = new ConvertingList<String>(base, converter);

        view.add("4");
        view.set(0, "10");
        view.add(1, "99");

        assertEquals(Arrays.asList(10, 99, 2, 3, 4), base);
        assertEquals("99", view.get(1));
    }
}
