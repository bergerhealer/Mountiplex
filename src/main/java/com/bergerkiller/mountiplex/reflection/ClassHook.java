package com.bergerkiller.mountiplex.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedCodeInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedHook;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

public class ClassHook<T extends ClassHook<?>> extends ClassInterceptor {
    private static Map<Class<?>, HookMethodList> hookMethodMap = new HashMap<Class<?>, HookMethodList>();
    public T base;
    private final HookMethodList methods;

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                hookMethodMap = new HashMap<Class<?>, HookMethodList>(0);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public ClassHook() {
        this.methods = loadMethodList(getClass());
        this.base = (T) new BaseClassInterceptor(this.methods).hook(this);
    }

    @Override
    protected final Invoker<?> getCallback(Method method) {
        throw new UnsupportedOperationException("Should not be called");
    }

    @Override
    protected Invoker<?> getCallback(Class<?> hookedType, Method method) {
        TypeDeclaration method_type = TypeDeclaration.fromClass(method.getDeclaringClass());
        MethodDeclaration methodDec = Resolver.resolveMethodAlias(TypeDeclaration.fromClass(hookedType), method);

        // Create class-level resolver, which adds all the imports declared at class level
        ClassResolver classLevelResolver = methodDec.getResolver();
        classLevelResolver.setClassLoader(methods.hookClassLoader);
        if (methods.classImports.length > 0 || methods.classPackage != null) {
            classLevelResolver = classLevelResolver.clone();
            if (methods.classPackage != null) {
                classLevelResolver.setPackage(methods.classPackage, false);
            }
            classLevelResolver.addImports(Arrays.asList(methods.classImports));
        }

        for (HookMethodEntry entry : methods.entries) {
            // Create resolver to decode the method, keep hook-level imports in mind
            // When method doesn't declare any imports/packages, reuse the class-level resolver
            ClassResolver resolver = classLevelResolver;
            if (entry.hookImports.length > 0 || entry.hookPackage != null) {
                resolver = resolver.clone();
                if (entry.hookPackage != null) {
                    resolver.setPackage(entry.hookPackage, false);
                }
                resolver.addImports(Arrays.asList(entry.hookImports));
            }

            // Check if signature matches with method
            MethodDeclaration m = (new MethodDeclaration(resolver, entry.declaration)).resolveName();
            if (m.isValid() && m.isResolved() && m.match(methodDec)) {
                entry.setMethod(method_type, method);
                //System.out.println("[" + method.getDeclaringClass().getSimpleName() + "] " +
                //        "Hooked " + methodDec.toString() + " to " + m.toString());
                return entry;
            }
        }
        return null;
    }

    @Override
    protected void onClassGenerated(Class<?> hookedType) {
        super.onClassGenerated(hookedType);

        // Verify that all non-optional hooked methods are found in the Class
        // Those missing will be logged for debugging
        TypeDeclaration ht = TypeDeclaration.fromClass(hookedType);
        for (HookMethodEntry method : methods.entries) {
            if (!method.optional && !method.foundMethod(ht)) {
                Class<?> baseType = hookedType;
                if (EnhancedObject.class.isAssignableFrom(hookedType)) {
                    baseType = hookedType.getSuperclass();
                    if (baseType.equals(Object.class) && hookedType.getInterfaces().length > 1) {
                        for (Class<?> interfaceType : hookedType.getInterfaces()) {
                            if (interfaceType != EnhancedObject.class) {
                                baseType = interfaceType;
                                break;
                            }
                        }
                    }
                }
                MountiplexUtil.LOGGER.warning("Hooked method " + method.toString() +
                        " was not found in " + MPLType.getName(baseType));
            }
        }
    }

    /**
     * Declares a single hook method overriding the method
     * matching the signature defined in {@link #value()}.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookMethod {
        String value();
        boolean optional() default false;
    }

    /**
     * Defines the main package in which this hook is expected
     * to operate. Imports are resolved using this package
     * with top priority. Only one package can be active
     * at one time.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookPackage {
        String value();
    }

    /**
     * Adds an import rule for resolving {@link HookMethod}
     * signatures. Can be added to individual methods, or hook
     * classes to add them to all methods declared inside.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(HookImportList.class)
    public @interface HookImport {
        String value();
    }

    /**
     * List of HookImport
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookImportList {
        HookImport[] value();
    }

    /**
     * Declares what Class Declaration Resolver to use as a source
     * for variables when evaluating method conditionals.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookLoadVariables {
        String value();
    }

    /**
     * Declares that for the method to be hooked, the conditional
     * in the text body must evaluate true
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookMethodCondition {
        String value();
    }

    private static HookMethodList loadMethodList(Class<?> hookClass) {
        if (!ClassHook.class.isAssignableFrom(hookClass)) {
            return new HookMethodList();
        }

        HookMethodList list = hookMethodMap.get(hookClass);
        if (list == null) {
            // Create new hook list with these imports
            list = new HookMethodList(hookClass);

            // Find all methods with a @HookMethod annotation
            for (Method method : hookClass.getDeclaredMethods()) {
                HookMethod hm = method.getAnnotation(HookMethod.class);
                if (hm != null) {
                    HookMethodEntry entry = new HookMethodEntry(list,
                            method, hm.value(), hm.optional());
                    if (entry.enabled) {
                        list.entries.add(entry);
                    }
                }
            }

            // Handle superclasses recursively
            list.entries.addAll(loadMethodList(hookClass.getSuperclass()).entries);
            hookMethodMap.put(hookClass, list);
        }
        return list;
    }

    private static class HookMethodList {
        public final ClassLoader hookClassLoader;
        public final List<HookMethodEntry> entries = new ArrayList<HookMethodEntry>();
        public final String[] classImports;
        public final String classPackage;
        public final ClassDeclarationResolver variablesResolver;

        public HookMethodList() {
            this.hookClassLoader = HookMethodList.class.getClassLoader();
            this.classImports = new String[0];
            this.classPackage = null;
            this.variablesResolver = null;
        }

        public HookMethodList(Class<?> hookClassType) {
            this.hookClassLoader = hookClassType.getClassLoader();

            this.classImports = ReflectionUtil.getAllClassesAndInterfaces(hookClassType)
                    .flatMap(c -> Stream.of(c.getDeclaredAnnotationsByType(HookImport.class)))
                    .map(HookImport::value)
                    .toArray(String[]::new);

            HookPackage packageAnnot = hookClassType.getAnnotation(HookPackage.class);
            this.classPackage = (packageAnnot == null) ? null : packageAnnot.value();
            this.variablesResolver = loadHookVariablesResolver(hookClassType,
                    ReflectionUtil.recurseFindAnnotationValue(hookClassType,
                            HookLoadVariables.class, HookLoadVariables::value, null));
        }
    }

    private static class BaseClassInterceptor extends ClassInterceptor {
        private final HookMethodList methodList;

        public BaseClassInterceptor(HookMethodList methodList) {
            this.methodList = methodList;
        }

        @Override
        protected Invoker<?> getCallback(Method method) {
            HookMethodEntry foundEntry = null;
            Iterator<HookMethodEntry> iter = this.methodList.entries.iterator();
            do {
                if (!iter.hasNext()) return null;
            } while (!(foundEntry = iter.next()).method.equals(method));
            return foundEntry.baseInvokable;
        }
    }

    private static class HookMethodEntry extends InterceptorCallback {
        public final InputTypeMap<Method> superMethodMap = new InputTypeMap<Method>();
        public final Map<Class<?>, Invoker<?>> superInvokerMap = new HashMap<Class<?>, Invoker<?>>();
        public final HookMethodList owner;
        public final String declaration;
        public final boolean optional;
        public final boolean enabled;
        public final Method method;
        public final String[] hookImports;
        public final String hookPackage;

        // This invokable is called with the hook as an instance
        public final Invoker<?> baseInvokable = (instance, args) -> {
            // Figure out what object we are currently handling and what type it is
            Object enhancedInstance = ((ClassHook<?>) instance).instance();
            Class<?> enhancedType = enhancedInstance.getClass();

            if (enhancedInstance instanceof EnhancedObject) {
                // Find a cached super-method invoker, or create one if missing
                Invoker<?> invoker = superInvokerMap.computeIfAbsent(enhancedType, (type) -> {
                    EnhancedObject enhanced = (EnhancedObject) enhancedInstance;
                    Method m = findMethodIn(TypeDeclaration.fromClass(enhanced.CI_getBaseType()));
                    if (m == null) {
                        throw new UnsupportedOperationException("Class " + MPLType.getName(enhanced.CI_getBaseType()) + 
                                " does not contain method " + HookMethodEntry.this.toString());
                    }
                    return GeneratedHook.createSuperInvoker(enhancedType, m);
                });

                // Call invokeSuper() on the MethodProxy to call the base class method
                return invoker.invokeVA(enhancedInstance, args);
            } else {
                // Not an enhanced instance, find the method in the class and invoke
                Method m = findMethodIn(TypeDeclaration.fromClass(enhancedType));
                if (m == null) {
                    throw new UnsupportedOperationException("Class " + MPLType.getName(enhancedType) + 
                            " does not contain method " + HookMethodEntry.this.toString());
                }

                // Invoke the method directly
                try {
                    return m.invoke(enhancedInstance, args);
                } catch (Throwable ex) {
                    throw ReflectionUtil.fixMethodInvokeException(m, enhancedInstance, args, ex);
                }
            }
        };

        public HookMethodEntry(HookMethodList list, Method method, String name, boolean optional) {
            this.owner = list;
            this.method = method;
            this.declaration = name;
            this.optional = optional;
            this.interceptorCallback = InitInvoker.forMethod(this, "interceptorCallback", method);
            this.hookImports = Stream.of(method.getDeclaredAnnotationsByType(HookImport.class))
                    .map(HookImport::value)
                    .toArray(String[]::new);

            HookPackage hookAnnot = method.getAnnotation(HookPackage.class);
            this.hookPackage = (hookAnnot == null) ? null : hookAnnot.value();

            HookMethodCondition conditionAnnot = method.getAnnotation(HookMethodCondition.class);
            if (conditionAnnot != null) {
                ClassResolver resolver = new ClassResolver();
                resolver.setDeclaredClass(Object.class); // Eh.
                {
                    HookLoadVariables loadVarsAnnot = method.getAnnotation(HookLoadVariables.class);
                    ClassDeclarationResolver variablesLoader = (loadVarsAnnot == null) ? list.variablesResolver
                            : loadHookVariablesResolver(method.getDeclaringClass(), loadVarsAnnot.value());
                    if (variablesLoader != null) {
                        resolver.setAllVariables(variablesLoader);
                    }
                }
                this.enabled = resolver.evaluateExpression(conditionAnnot.value());
            } else {
                this.enabled = true;
            }
        }

        @Override
        public String toString() {
            return declaration;
        }

        public boolean foundMethod(TypeDeclaration type) {
            return superMethodMap.containsKey(type);
        }

        public void setMethod(TypeDeclaration type, Method method) {
            superMethodMap.put(type, method);
        }

        public ClassResolver createResolver(Class<?> type) {
            ClassResolver resolver = new ClassResolver();
            resolver.setDeclaredClass(type);
            if (this.hookPackage != null) {
                resolver.setPackage(this.hookPackage);
            } else if (this.owner.classPackage != null) {
                resolver.setPackage(this.owner.classPackage);
            }
            resolver.addImports(Arrays.asList(this.owner.classImports));
            resolver.addImports(Arrays.asList(this.hookImports));
            resolver.setLogErrors(true);
            resolver.setClassLoader(this.owner.hookClassLoader);
            return resolver;
        }

        public Method findMethodIn(TypeDeclaration type) {
            if (type == null || !type.isResolved()) {
                return null;
            }

            Method m = superMethodMap.get(type);
            if (m == null) {
                ClassResolver resolver = this.createResolver(type.type);
                MethodDeclaration mDec = new MethodDeclaration(resolver, this.declaration);
                mDec = mDec.discover();
                if (mDec != null) {
                    m = mDec.method;
                    if (m != null) {
                        superMethodMap.put(type, m);
                    }
                }
            }
            return m;
        }
    }

    private static ClassDeclarationResolver loadHookVariablesResolver(Class<?> declaringClass, String code) {
        if (code == null) {
            return null;
        }
        try {
            ClassResolver resolver = new ClassResolver();
            resolver.setDeclaredClass(ClassDeclarationResolver.class);
            MethodDeclaration decl = new MethodDeclaration(resolver,
                    "public static ClassDeclarationResolver run() {\n" +
                    "    return " +code + ";\n" +
                    "}");
            GeneratedCodeInvoker<ClassDeclarationResolver> invoker = GeneratedCodeInvoker.create(decl);
            return invoker.invoke(null);
        } catch (Throwable t) {
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to initialize hook load variables: " + code, t);
            MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to load Hook Variables for " + declaringClass.getName());
            return null;
        }
    }
}
