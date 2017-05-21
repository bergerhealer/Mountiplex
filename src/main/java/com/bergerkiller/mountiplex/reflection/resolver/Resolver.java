package com.bergerkiller.mountiplex.reflection.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.BoxedType;
import com.bergerkiller.mountiplex.reflection.util.StaticInitHelper;

/**
 * Resolves class, field and method names into Class, Fields and Methods.
 * Resolvers can be added to allow for class/field/method name translation.
 */
public class Resolver {
    private static final ArrayList<ClassPathResolver> classPathResolvers = new ArrayList<ClassPathResolver>();
    private static final ArrayList<FieldNameResolver> fieldNameResolvers = new ArrayList<FieldNameResolver>();
    private static final ArrayList<MethodNameResolver> methodNameResolvers = new ArrayList<MethodNameResolver>();
    private static final ArrayList<ClassDeclarationResolver> classDeclarationResolvers = new ArrayList<ClassDeclarationResolver>();
    private static final HashMap<String, ClassMeta> classCache = new HashMap<String, ClassMeta>();

    /**
     * Attempts to load a class by path. If the class can not be loaded, null is returned instead.
     * 
     * @param path of the class to load
     * @param initialize whether to call static initializers on the loaded class
     * @return The loaded class, or null if the class could not be loaded
     */
    public static Class<?> loadClass(String path, boolean initialize) {
        synchronized (classCache) {
            ClassMeta meta = classCache.get(path);
            if (meta == null) {
                // Not yet initialized. Find the class!
                Class<?> type = loadClassImpl(path, initialize);
                meta = new ClassMeta(path, type, initialize);
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
        classDeclarationResolvers.add(resolver);
    }

    public static void registerClassResolver(ClassPathResolver resolver) {
        classPathResolvers.add(resolver);
        classCache.clear();
    }

    public static void registerMethodResolver(MethodNameResolver resolver) {
        methodNameResolvers.add(resolver);
        classCache.clear();
    }

    public static void registerFieldResolver(FieldNameResolver resolver) {
        fieldNameResolvers.add(resolver);
        classCache.clear();
    }

    public static String resolveClassPath(String classPath) {
        for (ClassPathResolver resolver : classPathResolvers) {
            classPath = resolver.resolveClassPath(classPath);
        }
        return classPath;
    }

    public static String resolveFieldName(Class<?> declaredClass, String fieldName) {
        for (FieldNameResolver resolver : fieldNameResolvers) {
            fieldName = resolver.resolveFieldName(declaredClass, fieldName);
        }
        return fieldName;
    }

    public static String resolveMethodName(Class<?> declaredClass, String methodName, Class<?>[] parameterTypes) {
        for (MethodNameResolver resolver : methodNameResolvers) {
            methodName = resolver.resolveMethodName(declaredClass, methodName, parameterTypes);
        }
        return methodName;
    }

    public static ClassDeclaration resolveClassDeclaration(Class<?> classType) {
        for (ClassDeclarationResolver resolver : classDeclarationResolvers) {
            ClassDeclaration dec = resolver.resolveClassDeclaration(classType);
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
        resolver.addClassImports(method.getDeclaringClass());
        return new MethodDeclaration(resolver, method);
    }

    private static MethodDeclaration resolveMethodInType(TypeDeclaration type,
            java.lang.reflect.Method method) {
        ClassDeclaration cDec = resolveClassDeclaration(type.type);
        return (cDec == null) ? null : cDec.findMethod(method);
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
        resolver.addClassImports(type);
        MethodDeclaration mDec = new MethodDeclaration(resolver, declaration);
        return findMethod(type, mDec);
    }

    /**
     * Attempts to find the resolved Method Declaration from a given declaration.
     * First template declarations are queried. If that fails, the class itself is inspected.
     * 
     * @param type class to start looking for the method
     * @param declaration to find
     * @return found method declaration, <i>null</i> on failure
     */
    public static MethodDeclaration findMethod(Class<?> type, MethodDeclaration declaration) {
        if (!declaration.isResolved()) {
            return null;
        }

        TypeDeclaration typeDec = TypeDeclaration.fromClass(type);
        MethodDeclaration result;

        // First attempt to find it in the templates we have defined
        // Also check for interfaces or base classes that may have the declaration we need
        result = findInTemplates(typeDec, declaration);
        if (result != null) {
            return result;
        }
        for (TypeDeclaration superType : typeDec.getSuperTypes()) {
            result = findInTemplates(superType, declaration);
            if (result != null) {
                return result;
            }
        }

        // No templates. Try looking through the class itself
        result = findInClass(typeDec, declaration);
        if (result != null) {
            return result;
        }
        for (TypeDeclaration superType : typeDec.getSuperTypes()) {
            result = findInClass(superType, declaration);
            if (result != null) {
                return result;
            }
        }

        // Not found
        return null;
    }

    private static MethodDeclaration findInTemplates(TypeDeclaration type, MethodDeclaration declaration) {
        ClassDeclaration cDec = resolveClassDeclaration(type.type);
        return (cDec == null) ? null : cDec.findMethod(declaration);
    }

    private static MethodDeclaration findInClass(TypeDeclaration type, MethodDeclaration declaration) {
        ClassDeclaration cDec = new ClassDeclaration(ClassResolver.DEFAULT, type.type);
        return cDec.findMethod(declaration);
    }

    private static class ClassMeta {
        //public final String path;
        public Class<?> type;
        public boolean loaded;

        public ClassMeta(String path, Class<?> type, boolean loaded) {
            //this.path = path;
            this.type = type;
            this.loaded = loaded;
        }

    }
}
