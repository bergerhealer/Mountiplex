package com.bergerkiller.mountiplex.reflection.resolver;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers what package paths refer to packages. Is used to eliminate
 * unneeded attempts that treat already-known package paths as class names
 * during class name resolving.<br>
 * <br>
 * Also prevents situations where a class with the same name as a package is
 * added on the class path corrupting lookups, so that all classes below this
 * package becomes invisible to Javassist.<br>
 * <br>
 * This class is multi-thread safe.
 */
public final class PackageNameCache {
    private final Set<String> _defaultPackages = new HashSet<>();
    private final Set<String> _packages = new HashSet<>();

    /**
     * Adds a default package name, including all child packages, to this cache.
     * Unlike {@link #addPackage(String)}, these packages persist through {@link #reset()}.
     *
     * @param name Default package name
     * @return this
     */
    public PackageNameCache addDefaultPackage(String name) {
        synchronized (_packages) {
            addPackageToSet(_defaultPackages, name);
            addPackageToSet(_packages, name);
        }
        return this;
    }

    /**
     * Resets (clears) all known packages to this cache, except the defaults
     * added through {@link #addDefaultPackage(String)}
     */
    public void reset() {
        synchronized (_packages) {
            _packages.clear();
            _packages.addAll(_defaultPackages);
        }
    }

    /**
     * Checks if the specified package name is a known package to this cache
     *
     * @param name
     * @return True if it is a known package name
     */
    public boolean isPackage(String name) {
        synchronized (_packages) {
            return _packages.contains(name);
        }
    }

    /**
     * Adds a package path to this cache, including all child packages
     *
     * @param name
     */
    public void addPackage(String name) {
        synchronized (_packages) {
            addPackageToSet(_packages, name);
        }
    }

    /**
     * Decodes the package of a class name and adds it. This will take the
     * 'my.app.name' part of 'my.app.name.Main$SubClass' and add it.
     *
     * @param className
     */
    public void addPackageOfClassName(String className) {
        int nameStart = className.lastIndexOf('.');
        if (nameStart != -1) {
            addPackage(className.substring(0, nameStart));
        }
    }

    /**
     * Verifies whether a given class name can exist, given the known
     * package names. If the class name tries to refer to part of a
     * known package path as a Class, rejects it.<br>
     * <br>
     * For example, if package 'my.app.name' is known, and class name
     * 'my.app.name' or 'my.app$name' is being loaded, will return false.
     * But class name 'my.app.name.Main' is permitted, and returns true.
     *
     * @param className
     * @return True if the class name can be valid, given the packages
     *         known to this cache.
     */
    public boolean canExist(String className) {
        String asPackage = className;
        int subClassStart = className.indexOf('$');
        if (subClassStart != -1) {
            asPackage = className.substring(0, subClassStart);
        }
        synchronized (_packages) {
            return !_packages.contains(asPackage);
        }
    }

    private static void addPackageToSet(Set<String> set, String packageName) {
        int lastDot;
        while (set.add(packageName) && (lastDot = packageName.lastIndexOf('.')) != -1) {
            packageName = packageName.substring(0, lastDot);
        }
    }
}
