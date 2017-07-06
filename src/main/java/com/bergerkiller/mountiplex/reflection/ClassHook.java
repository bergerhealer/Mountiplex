package com.bergerkiller.mountiplex.reflection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.InputTypeMap;

import net.sf.cglib.proxy.MethodProxy;

public class ClassHook<T extends ClassHook<?>> extends ClassInterceptor {
    private static final Map<Class<?>, HookMethodList> hookMethodMap = new HashMap<Class<?>, HookMethodList>();
    public T base;
    private final HookMethodList methods;

    @SuppressWarnings("unchecked")
    public ClassHook() {
        this.methods = loadMethodList(getClass());
        this.base = (T) this.methods.baseInterceptor.hook(this);
    }

    @Override
    protected Invokable getCallback(Method method) {
        TypeDeclaration method_type = TypeDeclaration.fromClass(method.getDeclaringClass());
        MethodDeclaration methodDec = Resolver.resolveMethod(method);

        for (HookMethodEntry entry : methods.entries) {
            // Check if signature matches with method
            MethodDeclaration m = new MethodDeclaration(methodDec.getResolver(), entry.declaration);
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
                        baseType = hookedType.getInterfaces()[1];
                    }
                }
                MountiplexUtil.LOGGER.warning("Hooked method " + method.toString() +
                        " was not found in " + baseType.getName());
            }
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HookMethod {
        String value();
        boolean optional() default false;
    }

    private static HookMethodList loadMethodList(Class<?> hookClass) {
        if (!ClassHook.class.isAssignableFrom(hookClass)) {
            return new HookMethodList();
        }

        HookMethodList list = hookMethodMap.get(hookClass);
        if (list == null) {
            list = new HookMethodList();

            // Find all methods with a @HookMethod annotation
            for (Method method : hookClass.getDeclaredMethods()) {
                HookMethod hm = method.getAnnotation(HookMethod.class);
                if (hm != null) {
                    list.entries.add(new HookMethodEntry(method, hm.value(), hm.optional()));
                }
            }

            // Handle superclasses recursively
            list.entries.addAll(loadMethodList(hookClass.getSuperclass()).entries);
            hookMethodMap.put(hookClass, list);
        }
        return list;
    }

    private static class HookMethodList {
        public final List<HookMethodEntry> entries = new ArrayList<HookMethodEntry>();
        public final ClassInterceptor baseInterceptor = new ClassInterceptor() {
            @Override
            protected Invokable getCallback(Method method) {
                HookMethodEntry foundEntry = null;
                Iterator<HookMethodEntry> iter = entries.iterator();
                do {
                    if (!iter.hasNext()) return null;
                } while (!(foundEntry = iter.next()).isMethod(method));

                final HookMethodEntry entry = foundEntry;
                return new Invokable() {
                    @Override
                    public Object invoke(Object instance, Object... args) {
                        // Figure out what object we are currently handling and what type it is
                        Object enhancedInstance = ((ClassHook<?>) instance).instance();
                        Class<?> enhancedType = enhancedInstance.getClass();

                        if (enhancedInstance instanceof EnhancedObject) {
                            // Find a method proxy to use for calling the super method, and cache it
                            MethodProxy proxy = entry.superMethodProxyMap.get(enhancedType);
                            if (proxy == null) {
                                Class<?> baseType = ((EnhancedObject) enhancedInstance).CI_getBaseType();
                                Method m = entry.findMethodIn(TypeDeclaration.fromClass(baseType));
                                if (m == null) {
                                    throw new UnsupportedOperationException("Class " + baseType.getName() + 
                                            " does not contain method " + entry.toString());
                                }

                                proxy = findMethodProxy(m, enhancedInstance);
                                entry.superMethodProxyMap.put(enhancedType, proxy);
                            }

                            // Call invokeSuper() on the MethodProxy to call the base class method
                            try {
                                return proxy.invokeSuper(enhancedInstance, args);
                            } catch (Throwable ex) {
                                Class<?> baseType = ((EnhancedObject) enhancedInstance).CI_getBaseType();
                                Method m = entry.findMethodIn(TypeDeclaration.fromClass(baseType));
                                throw ReflectionUtil.fixMethodInvokeException(m, enhancedInstance, args, ex);
                            }
                        } else {
                            // Not an enhanced instance, find the method in the class and invoke
                            Method m = entry.findMethodIn(TypeDeclaration.fromClass(enhancedType));
                            if (m == null) {
                                throw new UnsupportedOperationException("Class " + enhancedType.getName() + 
                                        " does not contain method " + entry.toString());
                            }

                            // Invoke the method directly
                            try {
                                return m.invoke(enhancedInstance, args);
                            } catch (Throwable ex) {
                                throw ReflectionUtil.fixMethodInvokeException(m, enhancedInstance, args, ex);
                            }
                        }
                    }
                };
            }
        };
    }

    private static class HookMethodEntry extends MethodInvokable {
        public final InputTypeMap<Method> superMethodMap = new InputTypeMap<Method>();
        public final Map<Class<?>, MethodProxy> superMethodProxyMap = new HashMap<Class<?>, MethodProxy>();
        public final String declaration;
        public final boolean optional;

        public HookMethodEntry(Method method, String name, boolean optional) {
            super(method);
            this.declaration = name;
            this.optional = optional;
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

        public Method findMethodIn(TypeDeclaration type) {
            if (type == null || !type.isResolved()) {
                return null;
            }

            Method m = superMethodMap.get(type);
            if (m == null) {
                MethodDeclaration mDec = Resolver.findMethod(type.type, this.declaration);
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
}
