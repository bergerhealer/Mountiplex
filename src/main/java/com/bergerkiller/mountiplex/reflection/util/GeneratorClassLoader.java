package com.bergerkiller.mountiplex.reflection.util;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLGeneratorClassLoaderBuilder;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedConstructor;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedInvoker;

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
                MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize generator class builder", t);
            }

            implementationFactory = theImplementationFactory;
            implementationTypeFailure = theImplementationTypeFailure;
        }

        // These classes are often used to generate method bodies at runtime.
        // However, a Class Loader might be specified that has no access to these
        // class types. We register these special types to avoid that problem.
        registerStaticClass(GeneratedInvoker.class);
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
        GeneratorClassLoader loader;
        if ((loader = loaders.get(baseClassLoader)) != null) {
            return loader;
        }

        synchronized (GeneratorClassLoader.class) {
            if ((loader = loaders.get(baseClassLoader)) != null) {
                return loader;
            }

            loader = GeneratorClassLoader.create(baseClassLoader);
            WeakHashMap<ClassLoader, GeneratorClassLoader> newLoaders = new WeakHashMap<>(loaders);
            newLoaders.put(baseClassLoader, loader);
            loaders = newLoaders;
            return loader;
        }
    }

    private static void remove(ClassLoader baseClassLoader) {
        synchronized (GeneratorClassLoader.class) {
            if (loaders.containsKey(baseClassLoader)) {
                WeakHashMap<ClassLoader, GeneratorClassLoader> newLoaders = new WeakHashMap<>(loaders);
                newLoaders.remove(baseClassLoader);
                loaders = newLoaders;
            }
        }
    }

    /**
     * Queries all GeneratorClassLoader instances to find a generated class by name.
     *
     * @param name Name of the class
     * @return generated class by this name
     */
    public static Class<?> findGeneratedClass(String name) {
        for (GeneratorClassLoader loader : loaders.values()) {
            Class<?> loaded = loader.findLoadedClass(name);
            if (loaded != null) {
                return loaded;
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

    private Class<?> superFindClass(String name) throws MPLType.LoaderClosedException {
        Class<?> loaded = this.findLoadedClass(name);
        return (loaded != null) ? loaded : tryFindClass(this.getParent(), name);
    }

    private static Class<?> tryFindClass(ClassLoader loader, String name) throws MPLType.LoaderClosedException {
        try {
            return MPLType.getClassByName(name, false, loader);
        } catch (MPLType.LoaderClosedException ex) {
            throw ex; // Do throw this one - we want it
        } catch (ClassNotFoundException ex) {
            return null;
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
            try {
                return super.loadClass(name, resolve);
            } catch (IllegalStateException is_ex) {
                if ("zip file closed".equals(is_ex.getMessage())) {
                    throw new MPLType.LoaderClosedException();
                }
                throw new ClassNotFoundException("Failed to load class " + name, is_ex);
            }
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> loaded;

        // Check whether this particular class was deferred to be generated
        // This will generate it, and this generating will link it with the
        // GeneratorClassLoader related to it.
        if ((loaded = ExtendedClassWriter.Deferred.load(name)) != null) {
            return loaded;
        }

        // Ask the other GeneratorClassLoaders what this Class is
        // This fixes a problem that it cannot find classes generated by other base classloaders

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
            } catch (MPLType.LoaderClosedException ex) {
                GeneratorClassLoader.remove(ExtendedClassWriter.class.getClassLoader());
            }
        }

        // Try all other loaders we have used to generate classes
        for (GeneratorClassLoader otherLoader : loaders.values()) {
            if (otherLoader == mountiplexLoader || otherLoader == this) {
                continue;
            }

            try {
                if ((loaded = otherLoader.superFindClass(name)) != null) {
                    return loaded;
                }
            } catch (MPLType.LoaderClosedException ex) {
                // ClassLoader no longer loaded, stop querying it
                GeneratorClassLoader.remove(otherLoader.getParent());
            }
        }

        // Failed to find it
        throw new ClassNotFoundException(name);
    }

    // make public
    @Override
    public Package definePackage(String name, String specTitle,
            String specVersion, String specVendor,
            String implTitle, String implVersion,
            String implVendor, URL sealBase)
    {
        return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }

    // make public
    @Override
    @Deprecated
    public Package getPackage(String name) {
        return super.getPackage(name);
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
