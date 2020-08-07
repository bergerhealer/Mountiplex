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

    @Test
    public void testPackagePathFromClassPath() {
        assertEquals("com.somepackage", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.SomeClass"));
        assertEquals("com.somepackage", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.lowercaseclass"));
        assertEquals("com.somepackage", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.SomeClass.SomeSubClass"));
        assertEquals("com.somepackage", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.SomeClass$SomeSubClass"));
        assertEquals("com.somepackage", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.lowerclass$SomeSubClass"));
        assertEquals("com.somepackage.v1_13_2_R1", MountiplexUtil.getPackagePathFromClassPath("com.somepackage.v1_13_2_R1.SomeClass"));
    }
}
