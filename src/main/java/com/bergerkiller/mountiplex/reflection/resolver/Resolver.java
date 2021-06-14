package com.bergerkiller.mountiplex.reflection.resolver;

import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.StringBuffer;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;

/**
 * Resolves class, field and method names into Class, Fields and Methods.
 * Resolvers can be added to allow for class/field/method name translation.
 */
public class Resolver {
    private static Resolver resolver = new Resolver();
    private ClassDeclarationResolver classDeclarationResolverChain = NoOpResolver.INSTANCE;
    private ClassPathResolver classPathResolverChain = NoOpResolver.INSTANCE;
    private CompiledFieldNameResolver compiledFieldNameResolverChain = NoOpResolver.INSTANCE;
    private CompiledMethodNameResolver compiledMethodNameResolverChain = NoOpResolver.INSTANCE;
    private FieldNameResolver fieldNameResolverChain = NoOpResolver.INSTANCE;
    private FieldAliasResolver fieldAliasResolverChain = NoOpResolver.INSTANCE;
    private MethodNameResolver methodNameResolverChain = NoOpResolver.INSTANCE;
    private boolean enableClassLoaderRemapping = false;
    private final HashMap<String, ClassMeta> classCache = new HashMap<String, ClassMeta>();
    private final HashMap<Class<?>, ClassMeta> classTypeCache = new HashMap<Class<?>, ClassMeta>();

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                resolver = new Resolver();
            }
        });
    }

    /**
     * Gets whether the class loader loading this library's classes is allowed
     * to remap generated code and class definitions.
     *
     * @return True if the class loader is allowed to remap
     */
    public static boolean isClassLoaderRemappingEnabled() {
        return resolver.enableClassLoaderRemapping;
    }

    /**
     * Sets whether the class loader loading this library's classes is allowed
     * to remap generated code and class definitions.
     *
     * @param enabled Whether this is enabled
     */
    public static void setClassLoaderRemappingEnabled(boolean enabled) {
        resolver.enableClassLoaderRemapping = enabled;
        MPLType.setRemappingEnabled(enabled);
    }

    /**
     * Gets whether a field is public and can be accessed from outside code.
     * This requires the field to have a 'public' modifier, and that the class in
     * which it is defined is also public.
     * 
     * @param field
     * @return True if the field is public
     */
    public static boolean isPublic(java.lang.reflect.Field field) {
        return Modifier.isPublic(field.getModifiers()) && isPublic(field.getDeclaringClass());
    }

    /**
     * Gets whether a method is public and can be accessed from outside code.
     * This requires the method to have a 'public' modifier, and that the class in
     * which it is defined is also public.
     * 
     * @param method
     * @return True if the method is public
     */
    public static boolean isPublic(java.lang.reflect.Method method) {
        return Modifier.isPublic(method.getModifiers()) && isPublic(method.getDeclaringClass());
    }

    /**
     * Gets whether a constructor is public and can be accessed from outside code.
     * This requires the constructor to have a 'public' modifier, and that the class in
     * which it is defined is also public.
     * 
     * @param constructor
     * @return True if the constructor is public
     */
    public static boolean isPublic(java.lang.reflect.Constructor<?> constructor) {
        return Modifier.isPublic(constructor.getModifiers()) && isPublic(constructor.getDeclaringClass());
    }

    /**
     * Gets whether a Class a publicly accessible and will not cause illegal access errors when used.
     * 
     * @param type
     * @return True if public
     */
    public static boolean isPublic(Class<?> type) {
        return getMeta(type).isPublic;
    }

    /**
     * Retrieves the metadata for a particular class type
     * 
     * @param type to get the metadata for
     * @return class metadata
     */
    public static ClassMeta getMeta(Class<?> type) {
        HashMap<Class<?>, ClassMeta> classTypeCache = resolver.classTypeCache;
        synchronized (classTypeCache) {
            ClassMeta meta = classTypeCache.get(type);
            if (meta == null) {
                meta = new ClassMeta(type, false);
                classTypeCache.put(type, meta);
            }
            return meta;
        }
    }

    /**
     * Looks up a previously loaded class name by path.
     * The type stored here must have the exact same class name.
     *
     * @param name Class name to find the loaded Class type of
     * @return Class type matching this name
     * @throws ClassNotFoundException If class by this name could not be found
     */
    public static Class<?> getClassByExactName(String name) throws ClassNotFoundException {
        HashMap<String, ClassMeta> classCache = resolver.classCache;
        synchronized (classCache) {
            ClassMeta meta = classCache.get(name);
            if (meta != null && meta.type != null && MPLType.getName(meta.type).equals(name)) {
                return meta.type;
            }
        }

        return MPLType.getClassByName(name);
    }

    /**
     * Attempts to load a class by path. If the class can not be loaded, null is returned instead.
     * Uses the class loader that loaded this library.
     * 
     * @param path of the class to load
     * @param initialize whether to call static initializers on the loaded class
     * @return The loaded class, or null if the class could not be loaded
     */
    public static Class<?> loadClass(String path, boolean initialize) {
        return loadClass(path, initialize, Resolver.class.getClassLoader());
    }

    /**
     * Attempts to load a class by path. If the class can not be loaded, null is returned instead.
     * 
     * @param path of the class to load
     * @param initialize whether to call static initializers on the loaded class
     * @param loader the preferred ClassLoader to use to find the class, if not already loaded
     * @return The loaded class, or null if the class could not be loaded
     */
    public static Class<?> loadClass(String path, boolean initialize, ClassLoader loader) {
        HashMap<String, ClassMeta> classCache = resolver.classCache;
        synchronized (classCache) {
            ClassMeta meta = classCache.get(path);
            if (meta == null) {
                Class<?> type = loadClassImpl(path, initialize, loader);
                if (type == null) {
                    meta = new ClassMeta(null, initialize);
                } else {
                    meta = getMeta(type);
                    meta.loaded = initialize;
                }
                classCache.put(path, meta);
            }

            // Load the class if required
            if (!meta.loaded && initialize) {
                if (meta.type != null) {
                    initializeClass(meta.type);
                }
                meta.loaded = true;
            }

            return meta.type;
        }
    }

    private static Class<?> loadClassImpl(String path, boolean initialize, ClassLoader loader) {
        // There is a strange glitch where the class loader fails to find primitive types
        // This is a workaround so that types like 'double' and 'void' are properly resolved
        Class<?> prim = BoxedType.getUnboxedType(path);
        if (prim != null) {
            return prim;
        }

        // Handle arrays here
        if (path.endsWith("[]")) {
            Class<?> componentType = loadClass(path.substring(0, path.length() - 2), initialize);
            if (componentType == null) {
                return null;
            } else {
                return java.lang.reflect.Array.newInstance(componentType, 0).getClass();
            }
        }

        /* ===================== */
        String alterPath = resolveClassPath(path);
        try {
            return MPLType.getClassByName(alterPath, initialize, loader);
        } catch (ExceptionInInitializerError e) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize class '" + alterPath + "':", e.getCause());
            return null;
        } catch (ClassNotFoundException e) {
            // This handles paths like these:
            //   net.minecraft.server.DataWatcher.Item
            // Which should be translated to:
            //   net.minecraft.server.DataWatcher$Item
            int last_dot = path.lastIndexOf('.');
            if (last_dot != -1) {
                int dot_before_last = path.lastIndexOf('.', last_dot-1);
                if (Character.isUpperCase(path.charAt(dot_before_last+1))) {
                    String path_new = path.substring(0, last_dot) + "$" + path.substring(last_dot+1);
                    return loadClass(path_new, initialize);
                }
            }
            return null;
        }
    }

    /**
     * Fully loads a Class, calling static initializers and initializing field values.
     * Classes are only ever initialized once.
     * 
     * @param classType to initialize
     */
    public static void initializeClass(Class<?> classType) {
        String className = MPLType.getName(classType);
        try {
            MPLType.getClassByName(className, true, classType.getClassLoader());
        } catch (ExceptionInInitializerError e) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize class '" + className + "':", e.getCause());
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void registerClassDeclarationResolver(ClassDeclarationResolver resolver) {
        Resolver.resolver.classDeclarationResolverChain = ChainResolver.chain(
                Resolver.resolver.classDeclarationResolverChain, resolver);
    }

    public static void registerClassResolver(ClassPathResolver resolver) {
        Resolver.resolver.classPathResolverChain = ChainResolver.chain(
                Resolver.resolver.classPathResolverChain, resolver);
        Resolver.resolver.classCache.clear();
    }

    public static void registerCompiledFieldResolver(CompiledFieldNameResolver resolver) {
        Resolver.resolver.compiledFieldNameResolverChain = ChainResolver.chain(
                Resolver.resolver.compiledFieldNameResolverChain, resolver);
    }

    public static void registerCompiledMethodResolver(CompiledMethodNameResolver resolver) {
        Resolver.resolver.compiledMethodNameResolverChain = ChainResolver.chain(
                Resolver.resolver.compiledMethodNameResolverChain, resolver);
    }

    public static void registerFieldResolver(FieldNameResolver resolver) {
        Resolver.resolver.fieldNameResolverChain = ChainResolver.chain(
                Resolver.resolver.fieldNameResolverChain, resolver);
    }

    public static void registerFieldAliasResolver(FieldAliasResolver resolver) {
        Resolver.resolver.fieldAliasResolverChain = ChainResolver.chain(
                Resolver.resolver.fieldAliasResolverChain, resolver);
    }

    public static void registerMethodResolver(MethodNameResolver resolver) {
        Resolver.resolver.methodNameResolverChain = ChainResolver.chain(
                Resolver.resolver.methodNameResolverChain, resolver);
    }

    /**
     * Discovers the Class Declaration registered for a Class by class path.
     * If the Class does not exist, null is returned also.
     * 
     * @param classPath of the Class
     * @return Class Declaration, null if not found
     */
    public static ClassDeclaration resolveClassDeclaration(String classPath) {
        Class<?> classType = loadClass(classPath, false);
        return (classType == null) ? null : resolveClassDeclaration(classPath, classType);
    }

    /**
     * Discovers the Class Declaration registered for a Class Type
     * 
     * @param classType
     * @return Class Declaration, null if not found
     */
    public static ClassDeclaration resolveClassDeclaration(Class<?> classType) {
        return resolveClassDeclaration(MPLType.getName(classType), classType);
    }

    /**
     * Discovers the Class Declaration registered for a Class, specifying both
     * the path that loads the class, and the loaded Class Type itself.
     * 
     * @param classPath of classType
     * @param classType to resolve
     * @return Class Declaration, null if not found
     */
    public static ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
        return Resolver.resolver.classDeclarationResolverChain.resolveClassDeclaration(classPath, classType);
    }

    /**
     * Resolves the environment variables that apply while resolving a class declaration.
     * This is used when parsing generated template declarations.
     * 
     * @param classPath of classType
     * @param classType to resolve
     * @return map of variables that apply for this Class
     */
    public static Map<String, String> resolveClassVariables(String classPath, Class<?> classType) {
        Map<String, String> variables = new HashMap<String, String>();
        Resolver.resolver.classDeclarationResolverChain.resolveClassVariables(classPath, classType, variables);
        return variables;
    }

    public static String resolveClassPath(String classPath) {
        return Resolver.resolver.classPathResolverChain.resolveClassPath(classPath);
    }

    public static boolean canLoadClassPath(String classPath) {
        return Resolver.resolver.classPathResolverChain.canLoadClassPath(classPath);
    }

    public static String resolveFieldName(Class<?> declaringClass, String fieldName) {
        return Resolver.resolver.fieldNameResolverChain.resolveFieldName(declaringClass, fieldName);
    }

    public static String resolveFieldAlias(java.lang.reflect.Field field, String name) {
        return Resolver.resolver.fieldAliasResolverChain.resolveFieldAlias(field, name);
    }

    public static String resolveMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
        return Resolver.resolver.methodNameResolverChain.resolveMethodName(declaringClass, methodName, parameterTypes);
    }

    public static String resolveCompiledFieldName(Class<?> declaringClass, String fieldName) {
        return Resolver.resolver.compiledFieldNameResolverChain.resolveCompiledFieldName(declaringClass, fieldName);
    }

    public static String resolveCompiledMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
        return Resolver.resolver.compiledMethodNameResolverChain.resolveCompiledMethodName(declaringClass, methodName, parameterTypes);
    }

    /**
     * Checks all class declarations that have been loaded in to see what the alias
     * name for a method is. If none are found, then a default declaration is returned
     * without an explicit alias set.
     * 
     * @param method to find
     * @return method declaration for the method with the alias name, if found
     */
    public static MethodDeclaration resolveMethodAlias(java.lang.reflect.Method method) {
        TypeDeclaration type = TypeDeclaration.fromClass(method.getDeclaringClass());

        // First attempt resolving it, which will provide metadata information such as aliases

        // Not found. Simply return a new Method Declaration from the method.
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(method.getDeclaringClass());
        MethodDeclaration result = new MethodDeclaration(resolver, method);

        // Figure out the alias that is set
        String alias = resolveMethodAliasInType(type, method);
        if (alias != null) {
            return result.setAlias(alias);
        }
        for (TypeDeclaration superType : type.getSuperTypes()) {
            alias = resolveMethodAliasInType(superType, method);
            if (alias != null) {
                return result.setAlias(alias);
            }
        }

        // Not found
        return result;
    }

    private static String resolveMethodAliasInType(TypeDeclaration type,
            java.lang.reflect.Method method)
    {
        ClassDeclaration cDec = resolveClassDeclaration(type.type);
        return (cDec == null) ? null : cDec.resolveMethodAlias(method);
    }

    /**
     * Attempts to find the resolved Field Declaration from a given declaration.
     * 
     * @param type class to start looking for the field
     * @param declaration to parse and look for
     * @return found field declaration, <i>null</i> on failure
     */
    public static FieldDeclaration findField(Class<?> type, String declaration) {
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(type);
        resolver.setLogErrors(true);
        FieldDeclaration fDec = new FieldDeclaration(resolver, StringBuffer.of(declaration));
        return fDec.discover();
    }

    /**
     * Attempts to find the resolved Method Declaration from a given declaration.
     * First template declarations are queried. If that fails, the class itself is inspected.
     * 
     * @param type class to start looking for the method
     * @param declaration to parse and look for
     * @return found method declaration, <i>null</i> on failure
     */
    public static MethodDeclaration findMethod(Class<?> type, String declaration) {
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(type);
        resolver.setLogErrors(true);
        MethodDeclaration mDec = new MethodDeclaration(resolver, declaration);
        return mDec.discover();
    }

    /**
     * Attempts to find the resolved Method Declaration from a given declaration.
     * First template declarations are queried. If that fails, the class itself is inspected.
     * 
     * @param declaration to find
     * @return found method declaration, <i>null</i> on failure
     */
    public static MethodDeclaration findMethod(MethodDeclaration declaration) {
        return declaration.discover();
    }

    public static final class ClassMeta {
        public final Class<?> type;
        protected boolean loaded;
        public final TypeDeclaration typeDec;
        public final TypeDeclaration[] interfaces;
        public final TypeDeclaration superType;
        public final boolean isPublic;

        public ClassMeta(Class<?> type, boolean loaded) {
            this.type = type;
            this.loaded = loaded;
            this.isPublic = isPublicClass(type);
            if (type != null) {
                this.typeDec = new TypeDeclaration(ClassResolver.DEFAULT, type);
                this.superType = findSuperType(this.typeDec);
                this.interfaces = findInterfaces(this.typeDec);
            } else {
                this.typeDec = TypeDeclaration.INVALID;
                this.superType = TypeDeclaration.OBJECT;
                this.interfaces = new TypeDeclaration[0];
            }
        }

        private static boolean isPublicClass(Class<?> type) {
            if (type == null) return true;
            if (!Modifier.isPublic(type.getModifiers())) return false;
            return isPublicClass(type.getDeclaringClass());
        }

        /// some bad class was found and we have to figure out what type variable is used <>
        /// we can do this by inspecting the base interface/class methods and expecting generic type from that
        private static TypeDeclaration fixResolveGenericTypes(TypeDeclaration type, TypeDeclaration base) {
            //TODO: Somehow implement this? It's a little tricky. Probably requires ASM Class Reader.
            return base;
        }

        private static TypeDeclaration findSuperType(TypeDeclaration type) {
            try {
                java.lang.reflect.Type s = type.type.getGenericSuperclass();
                return (s == null) ? null : TypeDeclaration.fromType(s);
            } catch (MalformedParameterizedTypeException | GenericSignatureFormatError ex) {
                Class<?> s = type.type.getSuperclass();
                return (s == null) ? null : fixResolveGenericTypes(type, TypeDeclaration.fromClass(s));
            }
        }

        private static TypeDeclaration[] findInterfaces(TypeDeclaration type) {
            try {
                java.lang.reflect.Type[] interfaces = type.type.getGenericInterfaces();
                TypeDeclaration[] result = new TypeDeclaration[interfaces.length];
                for (int i = 0; i < result.length; i++) {
                    result[i] = TypeDeclaration.fromType(interfaces[i]);
                }
                return result;
            } catch (MalformedParameterizedTypeException | GenericSignatureFormatError ex) {
                Class<?>[] interfaces = type.type.getInterfaces();
                TypeDeclaration[] result = new TypeDeclaration[interfaces.length];
                for (int i = 0; i < result.length; i++) {
                    result[i] = fixResolveGenericTypes(type, TypeDeclaration.fromClass(interfaces[i]));
                }
                return result;
            }
        }
    }
}
