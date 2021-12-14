package com.bergerkiller.mountiplex;
import static org.junit.Assert.*;

import org.junit.Test;

import com.bergerkiller.mountiplex.reflection.resolver.PackageNameCache;

public class PackageNameCacheTest {

    @Test
    public void testAddPackages() {
        PackageNameCache cache = new PackageNameCache();
        cache.addPackage("hello.world");
        assertTrue(cache.isPackage("hello.world"));
        assertTrue(cache.isPackage("hello"));
        assertFalse(cache.isPackage("other.world"));
    }

    @Test
    public void testDefaultPackages() {
        PackageNameCache cache = new PackageNameCache();
        cache.addDefaultPackage("hello.world");
        cache.addPackage("other.package");
        assertTrue(cache.isPackage("hello.world"));
        assertTrue(cache.isPackage("hello"));
        assertTrue(cache.isPackage("other.package"));
        assertTrue(cache.isPackage("other"));
        cache.reset();
        assertTrue(cache.isPackage("hello.world"));
        assertTrue(cache.isPackage("hello"));
        assertFalse(cache.isPackage("other.package"));
        assertFalse(cache.isPackage("other"));
    }

    @Test
    public void testCanExist() {
        PackageNameCache cache = new PackageNameCache();
        cache.addPackage("hello.world");
        assertTrue(cache.canExist("hello.world.Main"));
        assertTrue(cache.canExist("hello.world.Main$SubClass"));
        assertFalse(cache.canExist("hello.world"));
        assertFalse(cache.canExist("hello.world$Main"));
        assertFalse(cache.canExist("hello$world$Main"));
        assertFalse(cache.canExist("hello$Main"));
    }

    @Test
    public void testAddPackageOfClassName() {
        PackageNameCache cache = new PackageNameCache();
        cache.addPackageOfClassName("hello.world.Main");
        cache.addPackageOfClassName("other.package.Cool$SubClass");
        assertTrue(cache.isPackage("hello.world"));
        assertTrue(cache.isPackage("hello"));
        assertTrue(cache.isPackage("other.package"));
        assertTrue(cache.isPackage("other"));
        assertFalse(cache.isPackage("hello.world.Main"));
        assertFalse(cache.isPackage("other.package.Cool"));
        assertFalse(cache.isPackage("other.package.Cool$SubClass"));
    }
}
