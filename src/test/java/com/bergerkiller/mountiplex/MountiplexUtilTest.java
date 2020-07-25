package com.bergerkiller.mountiplex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MountiplexUtilTest {

    @Test
    public void testGetArrayType() {
        assertEquals(long.class, MountiplexUtil.getArrayType(long.class, 0));
        assertEquals(String.class, MountiplexUtil.getArrayType(String.class, 0));
        assertEquals(long[].class, MountiplexUtil.getArrayType(long[].class, 0));
        assertEquals(String[].class, MountiplexUtil.getArrayType(String[].class, 0));
        assertEquals(long[].class, MountiplexUtil.getArrayType(long.class, 1));
        assertEquals(String[].class, MountiplexUtil.getArrayType(String.class, 1));
        assertEquals(long[][].class, MountiplexUtil.getArrayType(long.class, 2));
        assertEquals(String[][].class, MountiplexUtil.getArrayType(String.class, 2));
        assertEquals(long[][].class, MountiplexUtil.getArrayType(long[].class, 1));
        assertEquals(String[][].class, MountiplexUtil.getArrayType(String[].class, 1));
        assertEquals(long[][][][].class, MountiplexUtil.getArrayType(long[][].class, 2));
        assertEquals(String[][][][].class, MountiplexUtil.getArrayType(String[][].class, 2));
    }
}
