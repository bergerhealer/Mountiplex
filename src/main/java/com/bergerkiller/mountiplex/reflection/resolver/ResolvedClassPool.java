package com.bergerkiller.mountiplex.reflection.resolver;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;
import com.bergerkiller.mountiplex.reflection.util.asm.ClassBytecodeLoader;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.compiler.MemberResolver;

/**
 * A Javassist ClassPool implementation that remaps class names using
 * the main {@link Resolver}. The resolved class pool is cached and re-used.
 * Use the static create function to retrieve one, and when done using, close it.
 */
public final class ResolvedClassPool extends ClassPool implements Closeable {
    private static final List<ResolvedClassPool> CACHED_CLASS_POOLS = new ArrayList<ResolvedClassPool>();
    private static final Map<ClassPool, Reference<Map<String,String>>> globalInvalidNamesMap = findInvalidNamesMap();
    private boolean ignoreRemapper = false;

    /**
     * Creates a resolved class pool, or retrieves one from cache.
     * When done using the pool, call {@link #close()} on it to return
     * it to the cache. It is recommended to use try-with-resources for this.
     *
     * @return resolved class pool
     */
    public static ResolvedClassPool create() {
        synchronized (CACHED_CLASS_POOLS) {
            if (!CACHED_CLASS_POOLS.isEmpty()) {
                return CACHED_CLASS_POOLS.remove(CACHED_CLASS_POOLS.size()-1);
            }
        }

        return new ResolvedClassPool();
    }

    @Override
    public void close() {
        clearImportedPackages();
        resetInvalidNames(this);
        synchronized (CACHED_CLASS_POOLS) {
            CACHED_CLASS_POOLS.add(this);
        }
    }

    private ResolvedClassPool() {
        super();
        appendClassPath(ClassBytecodeLoader.CLASSPATH);
    }

    @Override
    public Class<?> toClass(CtClass ct, Class<?> neighbor, ClassLoader loader, ProtectionDomain domain) throws CannotCompileException {
        // If the ClassLoader used is the GeneratorClassLoader, we can call defineClass
        // on it directly. This avoids an illegal access warning from being printed, and
        // also prevents a remapping classloader from interfering.
        //
        // Only do this when neighbor==null. The neighbor is used to define new classes
        // neighbouring other classes. This has some special logic we don't care about.
        if (loader instanceof GeneratorClassLoader && neighbor == null) {
            GeneratorClassLoader generator = (GeneratorClassLoader) loader;
            try {
                return generator.createClassFromBytecode(ct.getName(), ct.toBytecode(), domain);
            } catch (IOException e) {
                throw new CannotCompileException(e);
            }
        } else {
            return super.toClass(ct, neighbor, loader, domain);
        }
    }

    /**
     * Retrieves a class by name from this class pool without asking resolvers
     * to translate the class name. This can be important if the input name was
     * already resolved prior.
     *
     * @param classname
     * @return found class, or null if not found
     * @throws NotFoundException
     */
    public CtClass getWithoutResolving(String classname) throws NotFoundException {
        boolean old = ignoreRemapper;
        try {
            ignoreRemapper = true;
            return super.get(classname);
        } finally {
            ignoreRemapper = old;
        }
    }

    /**
     * Ignores the resolver remapping while the IgnoreToken returned by this
     * method remains unclosed. Use with try-with-resources to disable
     * remapping for a code block
     *
     * @return Token to restore the original ignoring state
     */
    public IgnoreToken ignoreResolver() {
        boolean current = this.ignoreRemapper;
        this.ignoreRemapper = true;
        return new IgnoreToken(current);
    }

    @Override
    public CtClass get(String classname) throws NotFoundException {
        if (ignoreRemapper) {
            return super.get(classname);
        }

        // This happens, apparently
        if (classname == null) {
            return null;
        }

        // Resolve it. If it returns null, name is invalid
        String resolvedName = resolveClassPath(classname);
        if (resolvedName == null) {
            throw new NotFoundException(classname);
        }

        // First try cache, no need to add package to cache if found
        CtClass fromCache = this.getCached(resolvedName);
        if (fromCache != null) {
            return fromCache;
        }

        // Look it up (slow way), if found it will be added to the cache
        // Also add name of class to package name discovery
        try {
            ignoreRemapper = true;
            CtClass found = super.get0(resolvedName, false);
            if (found == null) {
                throw new NotFoundException(classname);
            }
            Resolver.getPackageNameCache().addPackageOfClassName(classname);
            return found;
        } finally {
            ignoreRemapper = false;
        }
    }

    @Override
    public URL find(String classname) {
        if (ignoreRemapper) {
            return super.find(classname);
        }

        //TODO: This happens?
        if (classname == null) {
            return null;
        }

        String resolvedName = resolveClassPath(classname);
        if (resolvedName == null) {
            return null;
        }

        URL url = super.find(resolvedName);
        if (url == null) {
            return null;
        }

        Resolver.getPackageNameCache().addPackage(classname);
        return url;
    }

    @Override
    protected CtClass createCtClass(final String classname, boolean useCache) {
        if (ignoreRemapper) {
            return super.createCtClass(classname, useCache);
        }

        // accept "[L<class name>;" as a class name.
        String resolvedName = classname;
        if (resolvedName.charAt(0) == '[')
            resolvedName = Descriptor.toClassName(classname);

        // Resolve, make sure to undo array [] in class name
        int numArrayDims = 0;
        while (resolvedName.endsWith("[]")) {
            resolvedName = resolvedName.substring(0, resolvedName.length() - 2);
            numArrayDims++;
        }

        // Resolve it
        resolvedName = resolveClassPath(resolvedName);
        if (resolvedName == null) {
            return null; // Invalid class name
        }
        while (numArrayDims-- > 0) {
            resolvedName += "[]";
        }

        // Don't double-resolve inside find()
        try {
            ignoreRemapper = true;
            CtClass found = super.createCtClass(resolvedName, useCache);
            if (found != null) {
                Resolver.getPackageNameCache().addPackageOfClassName(classname);
            }
            return found;
        } finally {
            ignoreRemapper = false;
        }
    }

    private final String resolveClassPath(String classname) {
        if (classname.startsWith(MPLMemberResolver.IGNORE_PREFIX)) {
            String cleanedName = classname.substring(MPLMemberResolver.IGNORE_PREFIX.length());
            if (!Resolver.getPackageNameCache().canExist(cleanedName)) {
                return null;
            }
            return cleanedName;
        } else {
            if (!Resolver.getPackageNameCache().canExist(classname)) {
                return null;
            }
            return Resolver.resolveClassPath(classname);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ClassPool, Reference<Map<String,String>>> findInvalidNamesMap() {
        try {
            java.lang.reflect.Field invalidNamesMapField = MemberResolver.class.getDeclaredField("invalidNamesMap");
            invalidNamesMapField.setAccessible(true);
            Object map = invalidNamesMapField.get(null);
            invalidNamesMapField.setAccessible(false);
            return (Map<ClassPool, Reference<Map<String,String>>>) map;
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
    }

    private static void resetInvalidNames(ClassPool pool) {
        synchronized (MemberResolver.class) {
            globalInvalidNamesMap.computeIfPresent(pool, (p, curr) -> {
                Map<String, String> invalidNames = curr.get();
                if (invalidNames == null || invalidNames.isEmpty()) {
                    return curr;
                } else {
                    return new WeakReference<Map<String,String>>(new Hashtable<String,String>());
                }
            });
        }
    }

    /**
     * Used with try-with-resources to temporarily ignore resolving,
     * and automatically re-enable the previous state
     */
    public final class IgnoreToken implements AutoCloseable {
        private final boolean ignoreResolver;

        private IgnoreToken(boolean ignoreResolver) {
            this.ignoreResolver = ignoreResolver;
        }

        @Override
        public void close() {
            ResolvedClassPool.this.ignoreRemapper = this.ignoreResolver;
        }
    }
}
