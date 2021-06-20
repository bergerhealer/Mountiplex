package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Constructor;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLGeneratorClassLoaderBuilder;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedConstructor;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedMethodInvoker;

/**
 * ClassLoader used to generate new classes at runtime in various areas
 * of the library. Class loaders are re-used for the different base class
 * loaders in use. The generator is written in a way that class name
 * resolving works properly.
 */
public abstract class GeneratorClassLoader extends ClassLoader {
    private static final Map<String, Class<?>> staticClasses = new HashMap<String, Class<?>>();
    private static WeakHashMap<ClassLoader, GeneratorClassLoader> loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>();
    private static final Constructor<? extends GeneratorClassLoader> implementationFactory;
    private static final GeneratorNotSupportedException implementationTypeFailure;

    private static void registerStaticClass(Class<?> type) {
        staticClasses.put(type.getName(), type);
    }

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                loaders = new WeakHashMap<ClassLoader, GeneratorClassLoader>(0);
            }
        });

        // Initialize the environment while avoiding class initialization problems
        {
            Class<? extends GeneratorClassLoader> theImplementationType = null;
            Constructor<? extends GeneratorClassLoader> theImplementationFactory = null;
            GeneratorNotSupportedException theImplementationTypeFailure = null;
            try {
                theImplementationType = MPLGeneratorClassLoaderBuilder.buildImplementation();
                theImplementationFactory = (Constructor<? extends GeneratorClassLoader>) theImplementationType.getConstructor(ClassLoader.class);
            } catch (Throwable t) {
                theImplementationTypeFailure = new GeneratorNotSupportedException(t);
            }

            implementationFactory = theImplementationFactory;
            implementationTypeFailure = theImplementationTypeFailure;
        }

        // These classes are often used to generate method bodies at runtime.
        // However, a Class Loader might be specified that has no access to these
        // class types. We register these special types to avoid that problem.
        registerStaticClass(GeneratedMethodInvoker.class);
        registerStaticClass(GeneratedCodeInvoker.class);
        registerStaticClass(GeneratedConstructor.class);
    }

    /**
     * Gets the class loader used to generate classes.
     * This method is thread-safe.
     * 
     * @param baseClassLoader The base class loader used by the caller
     * @return generator class loader
     */
    public static GeneratorClassLoader get(ClassLoader baseClassLoader) {
        synchronized (loaders) {
            return loaders.computeIfAbsent(baseClassLoader, GeneratorClassLoader::create);
        }
    }

    /**
     * Queries all GeneratorClassLoader instances to find a generated class by name.
     *
     * @param name Name of the class
     * @return generated class by this name
     */
    public static Class<?> findGeneratedClass(String name) {
        synchronized (loaders) {
            for (GeneratorClassLoader loader : loaders.values()) {
                Class<?> loaded = loader.findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
            }
        }
        return null;
    }

    private static GeneratorClassLoader create(ClassLoader base) {
        if (base instanceof GeneratorClassLoader) {
            return (GeneratorClassLoader) base;
        } else if (implementationFactory != null) {
            try {
                return implementationFactory.newInstance(base);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }
        } else {
            throw implementationTypeFailure;
        }
    }

    protected GeneratorClassLoader(ClassLoader base) {
        super(base);
    }

    private Class<?> superFindClass(String name) throws LoaderClosedException {
        Class<?> loaded = this.findLoadedClass(name);
        return (loaded != null) ? loaded : tryFindClass(this.getParent(), name);
    }

    private static Class<?> tryFindClass(ClassLoader loader, String name) throws LoaderClosedException {
        try {
            return MPLType.getClassByName(name, false, loader);
        } catch (ClassNotFoundException ex) {
            return null;
            //URLClassLoader
        } catch (IllegalStateException ex) {
            if ("zip file closed".equals(ex.getMessage()) && loader instanceof URLClassLoader) {
                throw new LoaderClosedException();
            }
            throw ex;
        }
    }

    /*
     * We have to avoid asking class loaders for certain class types, because
     * it causes problems. For example, the generated invoker classes are provided
     * by this library, but a class loader might be used with no access to it.
     */
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> foundInStaticClasses = staticClasses.get(name);
        if (foundInStaticClasses != null) {
            return foundInStaticClasses;
        } else {
            return super.loadClass(name, resolve);
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        // Ask self
        Class<?> loaded = super.findLoadedClass(name);
        if (loaded != null) {
            Thread.dumpStack();
            return loaded;
        }

        // Ask the other GeneratorClassLoaders what this Class is
        // This fixes a problem that it cannot find classes generated by other base classloaders
        synchronized (loaders) {
            // Ask class loader of mountiplex first, as this is the most likely case
            // For example, when accessing mountiplex types extended at runtime
            // If we find the library is unloaded, wipe the GeneratorClassLoader to avoid trouble
            GeneratorClassLoader mountiplexLoader = loaders.get(GeneratorClassLoader.class.getClassLoader());
            if (mountiplexLoader != this) {
                try {
                    if (mountiplexLoader != null) {
                        if ((loaded = mountiplexLoader.superFindClass(name)) != null) {
                            return loaded;
                        }
                    } else {
                        if ((loaded = tryFindClass(GeneratorClassLoader.class.getClassLoader(), name)) != null) {
                            return loaded;
                        }
                    }
                } catch (LoaderClosedException ex) {
                    loaders.remove(ExtendedClassWriter.class.getClassLoader());
                }
            }

            // Try all other loaders we have used to generate classes
            Iterator<GeneratorClassLoader> loaderIter = loaders.values().iterator();
            while (loaderIter.hasNext()) {
                GeneratorClassLoader otherLoader = loaderIter.next();
                if (otherLoader == mountiplexLoader || otherLoader == this) {
                    continue;
                }

                try {
                    if ((loaded = otherLoader.superFindClass(name)) != null) {
                        return loaded;
                    }
                } catch (LoaderClosedException ex) {
                    // ClassLoader no longer loaded, stop querying it
                    loaderIter.remove();
                }
            }
        }

        // Failed to find it
        throw new ClassNotFoundException(name);
    }

    /**
     * Defines a new class name using the bytecode specified
     * 
     * @param name Name of the class to generate
     * @param b Bytecode for the Class
     * @param protectionDomain Protection Domain, null if unspecified
     * @return defined class
     */
    public Class<?> createClassFromBytecode(String name, byte[] b, ProtectionDomain protectionDomain) {
        return createClassFromBytecode(name, b, protectionDomain, true);
    }

    /**
     * Defines a new class name using the bytecode specified
     * 
     * @param name Name of the class to generate
     * @param b Bytecode for the Class
     * @param protectionDomain Protection Domain, null if unspecified
     * @param allowRemapping Whether to allow a class remapper to alter the code
     * @return defined class
     */
    public Class<?> createClassFromBytecode(String name, byte[] b, ProtectionDomain protectionDomain, boolean allowRemapping) {
        if (allowRemapping && Resolver.isClassLoaderRemappingEnabled()) {
            return super.defineClass(name, b, 0, b.length, protectionDomain);
        } else {
            return defineClassFromBytecode(name, b, protectionDomain);
        }

        /*
        try {
            return (Class<?>) defineClassMethod.invoke(this, name, b, Integer.valueOf(0), Integer.valueOf(b.length), protectionDomain);
        } catch (Throwable t) {
            throw MountiplexUtil.uncheckedRethrow(t);
        }
        */
    }

    /**
     * Implemented using generated code at runtime to define a class without remapping
     *
     * @param name Name of the class to generate
     * @param b Bytecode for the Class
     * @param protectionDomain Protection Domain, null if unspecified
     * @return defined class
     */
    protected abstract Class<?> defineClassFromBytecode(String name, byte[] b, ProtectionDomain protectionDomain);

    /**
     * Exception thrown when an attempt is made to load a Class from
     * a ClassLoader that has been closed. The caller should clean up
     * and avoid using the ClassLoader a second time.
     */
    public static class LoaderClosedException extends Exception {
        private static final long serialVersionUID = -2465209759941212720L;

        public LoaderClosedException() {
            super("This ClassLoader is closed");
        }
    }

    /**
     * Exception thrown when during the initialization of the GeneratorClassLoader
     * an unrecoverable problem occurred, and generating classes is not supported.
     */
    public static class GeneratorNotSupportedException extends RuntimeException {
        private static final long serialVersionUID = -362584700480972819L;

        public GeneratorNotSupportedException(Throwable reason) {
            super("Generating classes is not supported on this JDK", reason);
        }
    }
}
