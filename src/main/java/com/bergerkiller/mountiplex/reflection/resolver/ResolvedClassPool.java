package com.bergerkiller.mountiplex.reflection.resolver;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.mountiplex.reflection.util.GeneratorClassLoader;
import com.bergerkiller.mountiplex.reflection.util.asm.ClassBytecodeLoader;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

/**
 * A Javassist ClassPool implementation that remaps class names using
 * the main {@link Resolver}. The resolved class pool is cached and re-used.
 * Use the static create function to retrieve one, and when done using, close it.
 */
public final class ResolvedClassPool extends ClassPool implements Closeable {
    private static final List<ResolvedClassPool> CACHED_CLASS_POOLS = new ArrayList<ResolvedClassPool>();
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

        classname = resolveClassPath(classname);

        try {
            ignoreRemapper = true;
            return super.get(classname);
        } finally {
            ignoreRemapper = false;
        }
    }

    @Override
    public URL find(String classname) {
        return super.find(resolveClassPath(classname));
    }

    @Override
    protected CtClass createCtClass(String classname, boolean useCache) {
        if (ignoreRemapper) {
            return super.createCtClass(classname, useCache);
        }

        // accept "[L<class name>;" as a class name. 
        if (classname.charAt(0) == '[')
            classname = Descriptor.toClassName(classname);

        // Resolve, make sure to undo array [] in class name
        boolean isArray = classname.endsWith("[]");
        if (isArray) {
            classname = classname.substring(0, classname.length() - 2);
        }
        classname = resolveClassPath(classname);
        if (isArray) {
            classname += "[]";
        }

        // Don't double-resolve inside find()
        try {
            ignoreRemapper = true;
            return super.createCtClass(classname, useCache);
        } finally {
            ignoreRemapper = false;
        }
    }

    private final String resolveClassPath(String classname) {
        if (classname == null || ignoreRemapper) {
            return classname;
        } else if (classname.startsWith(MPLMemberResolver.IGNORE_PREFIX)) {
            return classname.substring(MPLMemberResolver.IGNORE_PREFIX.length());
        } else {
            return Resolver.resolveClassPath(classname);
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
