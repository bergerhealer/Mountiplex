package com.bergerkiller.mountiplex.reflection.resolver;

import java.lang.reflect.GenericSignatureFormatError;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.FieldDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

/**
 * Resolves class, field and method names into Class, Fields and Methods.
 * Resolvers can be added to allow for class/field/method name translation.
 */
public class Resolver {
    private static Resolver resolver = new Resolver();
    private final ArrayList<ClassPathResolver> classPathResolvers = new ArrayList<ClassPathResolver>();
    private final ArrayList<FieldNameResolver> fieldNameResolvers = new ArrayList<FieldNameResolver>();
    private final ArrayList<MethodNameResolver> methodNameResolvers = new ArrayList<MethodNameResolver>();
    private final ArrayList<ClassDeclarationResolver> classDeclarationResolvers = new ArrayList<ClassDeclarationResolver>();
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
     * Attempts to load a class by path. If the class can not be loaded, null is returned instead.
     * 
     * @param path of the class to load
     * @param initialize whether to call static initializers on the loaded class
     * @return The loaded class, or null if the class could not be loaded
     */
    public static Class<?> loadClass(String path, boolean initialize) {
        HashMap<String, ClassMeta> classCache = resolver.classCache;
        synchronized (classCache) {
            ClassMeta meta = classCache.get(path);
            if (meta == null) {
                Class<?> type = loadClassImpl(path, initialize);
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

    private static Class<?> loadClassImpl(String path, boolean initialize) {
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
            if (initialize) {
                Class<?> type = Class.forName(alterPath);
                StaticInitHelper.initType(type);
                return type;
            } else {
                return Class.forName(alterPath, false, MountiplexUtil.class.getClassLoader());
            }
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
        try {
            Class.forName(classType.getName());
            StaticInitHelper.initType(classType);
        } catch (ExceptionInInitializerError e) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize class '" + classType.getName() + "':", e.getCause());
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void registerClassDeclarationResolver(ClassDeclarationResolver resolver) {
        Resolver.resolver.classDeclarationResolvers.add(resolver);
    }

    public static void registerClassResolver(ClassPathResolver resolver) {
        Resolver.resolver.classPathResolvers.add(resolver);
        Resolver.resolver.classCache.clear();
    }

    public static void registerMethodResolver(MethodNameResolver resolver) {
        Resolver.resolver.methodNameResolvers.add(resolver);
        Resolver.resolver.classCache.clear();
    }

    public static void registerFieldResolver(FieldNameResolver resolver) {
        Resolver.resolver.fieldNameResolvers.add(resolver);
        Resolver.resolver.classCache.clear();
    }

    public static String resolveClassPath(String classPath) {
        for (ClassPathResolver resolver : Resolver.resolver.classPathResolvers) {
            classPath = resolver.resolveClassPath(classPath);
        }
        return classPath;
    }

    public static String resolveFieldName(Class<?> declaredClass, String fieldName) {
        for (FieldNameResolver resolver : Resolver.resolver.fieldNameResolvers) {
            fieldName = resolver.resolveFieldName(declaredClass, fieldName);
        }
        return fieldName;
    }

    public static String resolveMethodName(Class<?> declaredClass, String methodName, Class<?>[] parameterTypes) {
        for (MethodNameResolver resolver : Resolver.resolver.methodNameResolvers) {
            methodName = resolver.resolveMethodName(declaredClass, methodName, parameterTypes);
        }
        return methodName;
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
        return resolveClassDeclaration(classType.getName(), classType);
    }

    /**
     * Discovers the Class Declaration registered for a Class, specifying both
     * the path that loads the class, and the loaded Class Type itself.
     * 
     * @param classPath
     * @param classType
     * @return Class Declaration, null if not found
     */
    public static ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
        for (ClassDeclarationResolver resolver : Resolver.resolver.classDeclarationResolvers) {
            ClassDeclaration dec = resolver.resolveClassDeclaration(classPath, classType);
            if (dec != null) {
                return dec;
            }
        }
        return null;
    }

    /**
     * Attempts to find the method in the template class declarations available.
     * If not found, a new one is created purely for this Method, lacking metadata.
     * 
     * @param method to find
     * @return method declaration matching it
     */
    public static MethodDeclaration resolveMethod(java.lang.reflect.Method method) {
        TypeDeclaration type = TypeDeclaration.fromClass(method.getDeclaringClass());
        MethodDeclaration result;

        // First attempt resolving it, which will provide metadata information such as aliases
        result = resolveMethodInType(type, method);
        if (result != null) {
            return result;
        }
        for (TypeDeclaration superType : type.getSuperTypes()) {
            result = resolveMethodInType(superType, method);
            if (result != null) {
                return result;
            }
        }

        // Not found. Simply return a new Method Declaration from the method.
        ClassResolver resolver = new ClassResolver();
        resolver.setDeclaredClass(method.getDeclaringClass());
        return new MethodDeclaration(resolver, method);
    }

    private static MethodDeclaration resolveMethodInType(TypeDeclaration type,
            java.lang.reflect.Method method) {
        ClassDeclaration cDec = resolveClassDeclaration(type.type);
        return (cDec == null) ? null : cDec.findMethod(method);
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
        FieldDeclaration fDec = new FieldDeclaration(resolver, declaration);
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
