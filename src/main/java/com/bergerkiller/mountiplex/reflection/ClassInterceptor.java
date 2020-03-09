package com.bergerkiller.mountiplex.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.objenesis.ObjenesisHelper;
import org.objenesis.instantiator.ObjectInstantiator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.FastField;
import com.bergerkiller.mountiplex.reflection.util.FastMethod;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

import org.objectweb.asm.Type;
import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.CallbackFilter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;

/**
 * Base implementation for hooking other classes and intercepting method calls.
 * A single {@link ClassInterceptor} can be used to hook multiple objects in a single session.
 * Every hooked object stores a reference to the ClassInterceptor instance, which can be retrieved
 * using {@link #get(Object, Class)}.
 * <br><br>
 * It is required that an implementation of ClassInterceptor never changes the behavior
 * of {@link #getCallback(Method)}. In other words, for the given Method parameter, it should
 * consistently return the same {@link CallbackDelegate} across all instances.
 */
public abstract class ClassInterceptor {
    private static Map<Class<?>, Map<Method, Invoker<?>>> globalMethodDelegatesMap = new HashMap<Class<?>, Map<Method, Invoker<?>>>();
    private static Map<ClassPair, EnhancedClass> enhancedTypes = new HashMap<ClassPair, EnhancedClass>();
    private boolean useGlobalCallbacks = true;
    private final Map<Method, Invoker<?>> globalMethodDelegates;
    private final InstanceHolder lastHookedObject = new InstanceHolder();
    private final ThreadLocal<StackInformation> stackInfo = ThreadLocal.withInitial(StackInformation::new);

    static {
        MountiplexUtil.registerUnloader(new Runnable() {
            @Override
            public void run() {
                globalMethodDelegatesMap = new HashMap<Class<?>, Map<Method, Invoker<?>>>(0);
                enhancedTypes = new HashMap<ClassPair, EnhancedClass>(0);
            }
        });
    }

    public ClassInterceptor() {
        synchronized (globalMethodDelegatesMap) {
            Map<Method, Invoker<?>> globalMethodDelegates = globalMethodDelegatesMap.get(getClass());
            if (globalMethodDelegates == null) {
                globalMethodDelegates = new HashMap<Method, Invoker<?>>();
                globalMethodDelegatesMap.put(getClass(), globalMethodDelegates);
            }
            this.globalMethodDelegates = globalMethodDelegates;
        }
    }

    /**
     * Retrieves the callback delegate for handling a certain Method call in the base object.
     * To not intercept the Method, return NULL.
     * 
     * @param method
     * @return Callback delegate to execute, or NULL to not intercept the Method
     */
    protected abstract Invoker<?> getCallback(Method method);

    /**
     * Callback function called when a new intercepted hooked class type has been generated.
     * 
     * @param hookedType that was generated
     */
    protected void onClassGenerated(Class<?> hookedType) {
    }

    /**
     * Creates a new hooked instance of the type specified, without constructing it
     * 
     * @param type to create a new hook of
     * @return new instance of the type, hooked by this ClassInterceptor
     */
    public <T> T createInstance(Class<T> type) {
        return createEnhancedClass(this, type, null, null, null);
    }

    /**
     * Creates a new hooked instance of the type specified by calling a constructor
     * 
     * @param type to create a new hook of
     * @param paramTypes of the constructor
     * @param params for when calling the constructor
     * @return newly constructed instance of the type, hooked by this ClassInterceptor
     */
    public <T> T constructInstance(Class<T> type, Class<?>[] paramTypes, Object[] params) {
        return createEnhancedClass(this, type, null, paramTypes, params);
    }

    /**
     * Creates an extension of the object intercepting the callbacks as specified by {@link #getCallback}.
     * The object state (fields) are copied over from the old object to the new one.
     * 
     * @param object to hook
     * @return hooked object
     */
    public <T> T hook(T object) {
        return createEnhancedClass(this, object.getClass(), object, null, null);
    }

    /**
     * Initializes the interceptor so that it is aware of the object and callbacks it has to call
     * for a particular object type, but does not hook. Mocking an object will not intercept its
     * method calls, but will enable proper function of the callback functions on the object.
     * <br><br>
     * Only one object can be mocked by an interceptor at one time. Hooking a new object will
     * replace the original mocked object.
     * 
     * @param object to mock
     */
    public void mock(Object object) {
        this.lastHookedObject.value = object;
    }

    /**
     * Removes all extensions added to an object, ending the method intercepting.
     * The object state (fields) are copied over from the old object to the new one.
     * 
     * @param object to unhook
     * @return unhooked base object
     */
    public static <T> T unhook(T object) {
        // If this object was last-hooked, clear the state so this object is no more returned
        if (object instanceof EnhancedObject) {
            EnhancedObject enhancedObject = (EnhancedObject) object;
            ClassInterceptor ci = enhancedObject.CI_getInterceptor();
            EnhancedClass eh = enhancedObject.CI_getEnhancedClass();

            if (ci.lastHookedObject.value == object) {
                ci.lastHookedObject.value = null;
            }

            return eh.createBase(object);
        } else {
            return object; // not enhanced; ignore
        }
    }

    /**
     * Retrieves the ClassInterceptor owner of a hooked object
     * 
     * @param object to query
     * @param interceptorClass type
     * @return hook owner, or NULL if the object is not hooked by this ClassInterceptor type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ClassInterceptor> T get(Object object, Class<T> interceptorClass) {
        ClassInterceptor interceptor = get(object);
        return interceptorClass.isInstance(interceptor) ? (T) interceptor : null;
    }

    /**
     * Retrieves the ClassInterceptor owner of a hooked object, without checking for type
     * 
     * @param object to query
     * @return interceptor
     */
    protected static ClassInterceptor get(Object object) {
        return (object instanceof EnhancedObject) ? ((EnhancedObject) object).CI_getInterceptor() : null;
    }

    /**
     * Stores a callback for the current thread for this interceptor. This allows
     * for optimized callback execution.
     * 
     * @param method
     * @param callback
     */
    protected void storeCallbackForCurrentThread(Method method, Invoker<?> callback) {
        stackInfo.get().storeCallback(method, callback);
    }

    /**
     * When all method callbacks are global:
     * <ul>
     * <li>getCallback() is only called once per method (faster!)
     * <li>it is impossible to have instance-specific callbacks
     * <li>this is the default setting
     * </ul>
     * When methods are only kept local to this ClassInterceptor:
     * <ul>
     * <li>getCallback() is called for every method, for every thread, for every interceptor instance (slower!)
     * <li>it is possible to have instance-specific callbacks
     * </ul>
     * 
     * @param global whether to cache callback methods globally
     */
    public final void setUseGlobalCallbacks(boolean global) {
        useGlobalCallbacks = global;
    }

    /**
     * Gets the Object that is currently being invoked.
     * If this is called when no callback is being handled, then the
     * last hooked instance is returned instead.
     * 
     * @return Underlying Object instance
     */
    protected final Object instance() {
        Object stack_instance = this.stackInfo.get().currentInstance();
        if (stack_instance != null) {
            return stack_instance;
        } else if (lastHookedObject.value == null) {
            throw new IllegalStateException("No object is handled right now");
        } else {
            return lastHookedObject.value;
        }
    }

    /**
     * Gets the Base Type of the Object that is currently being invoked.
     * If this is called when no callback is being handled, then the Base Type
     * of the last hooked instance is returned instead.
     * 
     * @return Underlying base type
     */
    protected final Class<?> instanceBaseType() {
        return findInstanceBaseType(this.instance());
    }

    /**
     * Invokes the method on the superclass. This bypasses the callbacks and enables the original base class
     * to handle the method.
     * 
     * @param method to invoke
     * @param instance to invoke the method on
     * @param args arguments for the method
     * @return The response from executing
     * @throws Throwable
     */
    public final Object invokeSuperMethod(Method method, Object instance, Object[] args) {
        try {
            if (instance instanceof EnhancedObject) {
                return findMethodProxy(method, instance).invokeSuper(instance, args);
            } else {
                return method.invoke(instance, args);
            }
        } catch (Throwable ex) {
            throw ReflectionUtil.fixMethodInvokeException(method, instance, args, ex);
        }
    }

    /**
     * Finds the CGLib method proxy that can be used to invoke a super method in the enhanced instance.
     * No caching is done, so it may be preferable to cache the proxy when repeatedly used.
     * Please note that the MethodProxy returned is only valid for objects hooked by this
     * ClassInterceptor and Object instance type passed in.
     * 
     * @param method to find
     * @param instance to find the method in
     * @return MethodProxy
     * @throws UnsupportedOperationException when the method can not be proxied
     */
    protected final MethodProxy findMethodProxy(Method method, Object instance) {
        // Fast access: check if the last-called proxy matches the method
        // This is a common case where a handler calls a base method
        // Doing it this way avoids a map get/put call
        StackInformation stack = this.stackInfo.get();
        if (instance == stack.frame.instance && method.equals(stack.frame.method)) {
            return stack.frame.proxy;
        }

        // Slower way of instantiating a new MethodProxy for this type
        Signature sig = new Signature(method.getName(), Type.getReturnType(method), Type.getArgumentTypes(method));
        MethodProxy proxy = null;
        try {
            proxy = MethodProxy.find(instance.getClass(), sig);
        } catch (Throwable t) {
        }

        // Dont allow this!
        if (proxy == null) {
            throw new UnsupportedOperationException("Proxy for super method " + method.toGenericString() + 
                    " does not exist in class " + instance.getClass().getName());
        }
        return proxy;
    }

    /**
     * Gets the base type of an instance hooked by a class interceptor. Of the instance is not hooked,
     * then the instance type is returned instead.
     * 
     * @param instance
     * @return base type of the instance
     */
    public static Class<?> findInstanceBaseType(Object instance) {
        if (instance instanceof EnhancedObject) {
            return ((EnhancedObject) instance).CI_getBaseType();
        } else if (instance != null) {
            return instance.getClass();
        } else {
            return null;
        }
    }

    /**
     * Finds the base class of an enhanced class type. If the type is not enhanced,
     * it is returned as is.
     * 
     * @param enhancedType
     * @return base type of enhancedType
     */
    public static Class<?> findBaseType(Class<?> enhancedType) {
        if (enhancedType == null) {
            return null;
        }
        while (EnhancedObject.class.isAssignableFrom(enhancedType)) {
            enhancedType = enhancedType.getSuperclass();
        }
        return enhancedType;
    }

    /* ====================================================================================== */
    /* ================================== Implementation Code =============================== */
    /* ====================================================================================== */

    private static synchronized <T> T createEnhancedClass(ClassInterceptor interceptor, 
                                                          Class<?> objectType, T object,
                                                          Class<?>[] paramTypes, Object[] params)
    {
        // The key used to access the EnhancedClass instance for creating this instance
        final ClassPair key = new ClassPair(interceptor.getClass(), objectType);

        // A list of fixed values returned by the EnhancedObject interface
        final Callback[] callbacks = new Callback[] {
                new EnhancedObjectProperty("CI_getInterceptor", interceptor),
                new EnhancedObjectProperty("CI_getBaseType", objectType),
                new EnhancedObjectProperty("CI_getEnhancedClass", null),
                new CallbackMethodInterceptor(interceptor),
                NoOp.INSTANCE
        };

        // Try to find the CGLib-generated enhanced class that provides the needed callbacks
        // If none exists yet, generate a new one and put it into the table for future re-use
        EnhancedClass enhanced = enhancedTypes.get(key);
        if (enhanced == null) {
            Enhancer enhancer = new Enhancer();
            enhancer.setClassLoader(key.hookClass.getClassLoader());

            // When its an interface, we have to implement it as opposed to extending it
            if (objectType.isInterface()) {
                enhancer.setSuperclass(Object.class);
                enhancer.setInterfaces(new Class<?>[] { EnhancedObject.class, objectType } );
            } else {
                enhancer.setSuperclass(objectType);
                enhancer.setInterfaces(new Class<?>[] { EnhancedObject.class } );
            }

            // Create callback filter
            EnhancedClassCallbackFilter callbackFilter = new EnhancedClassCallbackFilter(interceptor, callbacks);

            // Initialize the callback types and callback mapping
            enhancer.setCallbackTypes(MountiplexUtil.getTypes(callbacks));
            enhancer.setCallbackFilter(callbackFilter);

            // Finally create the enhanced class type and store it in the mapping for later use
            enhanced = new EnhancedClass(objectType, enhancer.createClass(), callbackFilter);
            enhancedTypes.put(key, enhanced);
            interceptor.onClassGenerated(enhanced.enhancedType);
        } else {
            // Set to valid values before initializing callbacks potentially
            enhanced.callbackFilter.interceptor = interceptor;
            enhanced.callbackFilter.callbacks = callbacks;
        }

        // Update EnhancedClass property
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof EnhancedObjectProperty) {
                EnhancedObjectProperty prop = (EnhancedObjectProperty) callbacks[i];
                if (prop.name.equals("CI_getEnhancedClass")) {
                    callbacks[i] = new EnhancedObjectProperty(prop.name, enhanced);
                }
            }
        }

        // Register the method callbacks, and then create the enhanced object instance using Objenesis
        // Note that since we don't call any constructors, CGLib does not update the object callback list
        // We force an explicit internal update by calling the CI_getInterceptor() interface function
        // After this is done, we must delete the callbacks again to prevent a memory leak
        // This is done using disableCallbacks() and by setting filter members to null
        // This way the interceptor instance is allowed to be garbage collected
        enhanced.setCallbacks(callbacks);
        T enhancedObject = enhanced.createEnhanced(object, paramTypes, params);
        interceptor.lastHookedObject.value = enhancedObject;
        ((EnhancedObject) enhancedObject).CI_getInterceptor();
        enhanced.disableCallbacks();
        return enhancedObject;
    }

    private static class EnhancedObjectProperty implements FixedValue {
        private final String name;
        private final Object value;

        public EnhancedObjectProperty(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        public final String getName() {
            return name;
        }

        @Override
        public Object loadObject() throws Exception {
            return this.value;
        }
    }

    private static final class ClassPair {
        public final Class<?> hookClass;
        public final Class<?> instanceClass;
        private final int hashcode;

        public ClassPair(Class<?> hookClass, Class<?> instanceClass) {
            this.hookClass = hookClass;
            this.instanceClass = instanceClass;
            this.hashcode = (hookClass.hashCode() >> 1) + (instanceClass.hashCode() >> 1);
        }

        @Override
        public int hashCode() {
            return this.hashcode;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ClassPair) {
                ClassPair p = (ClassPair) other;
                return hookClass.equals(p.hookClass) && instanceClass.equals(p.instanceClass);
            }
            return false;
        }
    }

    /**
     * When generating the CGLib proxy class, handles returning the appropriate callbacks
     * for each method that can be overridden.
     */
    private static final class EnhancedClassCallbackFilter implements CallbackFilter {
        public ClassInterceptor interceptor;
        public Callback[] callbacks;

        public EnhancedClassCallbackFilter(ClassInterceptor interceptor, Callback[] callbacks) {
            this.interceptor = interceptor;
            this.callbacks = callbacks;
        }

        @Override
        public int accept(Method method) {
            // We don't expect this to be called after the first time
            if (callbacks == null) {
                throw new IllegalStateException("Filter was disabled and is called too late");
            }

            // Properties are returned as Fixed Value types for quick access
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof EnhancedObjectProperty &&
                        ((EnhancedObjectProperty) callbacks[i]).getName().equals(method.getName())) {
                    return i;
                }
            }

            // Handle callbacks/no-op
            StackInformation stackInfo = interceptor.stackInfo.get();
            Invoker<?> callback = stackInfo.getCallback(method);
            if (callback == null) {
                callback = interceptor.getCallback(method);
                if (callback == null) {
                    return (callbacks.length - 1); /* No callback, redirect to No Operation handler */
                }
                stackInfo.storeCallback(method, callback);
            }
            return (callbacks.length - 2); /* Has callback, redirect to CallbackMethodInterceptor */
        }
    }

    /**
     * Maintains metadata information about a particular CGLib-enhanced class.
     * Also handles the construction of new objects during hooking/unhooking.
     */
    public static final class EnhancedClass {
        private static final String SET_THREAD_CALLBACKS_NAME = "CGLIB$SET_THREAD_CALLBACKS";
        public final ObjectInstantiator<?> enhancedInstantiator;
        public final ObjectInstantiator<?> baseInstantiator;
        public final Class<?> baseType;
        public final Class<?> enhancedType;
        private final EnhancedClassCallbackFilter callbackFilter;
        private final FastField<?>[] baseTypeFields;
        private final FastMethod<Void> setCallbacksMethod;
        private final Callback[] disabledCallbacks;

        public EnhancedClass(Class<?> baseType, Class<?> enhancedType, EnhancedClassCallbackFilter callbackFilter) {
            this.baseType = baseType;
            this.enhancedType = enhancedType;
            this.callbackFilter = callbackFilter;
            this.baseInstantiator = ObjenesisHelper.getInstantiatorOf(baseType);
            this.enhancedInstantiator = ObjenesisHelper.getInstantiatorOf(enhancedType);
            if (this.baseInstantiator == null)
                throw new RuntimeException("Base Class " + baseType.getName() + " has no instantiator");
            if (this.enhancedInstantiator == null)
                throw new RuntimeException("Enhanced Class " + enhancedType.getName() + " has no instantiator");
        
            // This method is cached to reduce performance overhead when constructing new enhanced classes
            try {
                Method m = enhancedType.getDeclaredMethod(SET_THREAD_CALLBACKS_NAME, new Class[]{ Callback[].class });
                this.setCallbacksMethod = new FastMethod<Void>(m);
            } catch (Throwable t) {
                throw MountiplexUtil.uncheckedRethrow(t);
            }

            // These are used for transferring all fields from one Object to another
            List<FastField<?>> fieldsList = ReflectionUtil.fillFastFields(new ArrayList<FastField<?>>(), baseType);
            this.baseTypeFields = fieldsList.toArray(new FastField<?>[fieldsList.size()]);

            // These disabled callbacks are used whenever the enhanced type is created outside of here
            // This can happen when, for example, calling a constructor on the enhanced type.
            // Without these a random interceptor, or worse, null ends up being used
            this.disabledCallbacks = new Callback[] {
                    new EnhancedObjectProperty("CI_getInterceptor", null),
                    new EnhancedObjectProperty("CI_getBaseType", baseType),
                    new EnhancedObjectProperty("CI_getEnhancedClass", EnhancedClass.this),
                    new CallbackMethodInterceptor(null) {
                        @Override
                        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
                            return proxy.invokeSuper(obj, args);
                        }
                    },
                    NoOp.INSTANCE
            };
        }

        public final void setCallbacks(Callback[] callbacks) {
            this.setCallbacksMethod.invoke(null, callbacks);
        }

        public final void disableCallbacks() {
            this.setCallbacksMethod.invoke(null, this.disabledCallbacks);
            this.callbackFilter.interceptor = null;
            this.callbackFilter.callbacks = null;
        }

        @SuppressWarnings("unchecked")
        public <T> T createBase(T enhanced) {
            Object base = this.baseInstantiator.newInstance();
            if (base == null)
                throw new RuntimeException("Class " + baseType.getName() + " could not be instantiated (newInstance failed)");

            for (FastField<?> ff : this.baseTypeFields) {
                ff.copy(enhanced, base);
            }
            return (T) base;
        }

        @SuppressWarnings("unchecked")
        public <T> T createEnhanced(T base, Class<?>[] paramTypes, Object[] params) {
            Object enhanced = null;
            if (paramTypes == null) {
                // Null parameter array: use Objenesis to create an instance without calling a constructor
                enhanced = this.enhancedInstantiator.newInstance();
            } else {
                // Find the constructor in the base class and call it
                Constructor<?> constructor = null;
                try {
                    constructor = enhancedType.getConstructor(paramTypes);
                    enhanced = constructor.newInstance(params);
                } catch (Throwable t) {
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to construct " + enhancedType.getName(), t);
                }
            }

            if (enhanced == null)
                throw new RuntimeException("Class " + enhancedType.getName() + " could not be instantiated (newInstance failed)");

            if (base != null) {
                for (FastField<?> ff : this.baseTypeFields) {
                    ff.copy(base, enhanced);
                }
            }
            return (T) enhanced;
        }
    }

    /**
     * The MethodInterceptor is the first to receive method execution notification from CGLib.
     * In here the right callback to use is found and executed. It also updates the call stack.
     * The call stack is important for the correct workings of:
     * <ul>
     * <li>{@link ClassInterceptor#findMethodProxy(method, instance)}
     * <li>{@link ClassInterceptor#instance()}
     * </ul>
     */
    private static class CallbackMethodInterceptor implements MethodInterceptor {
        private final ClassInterceptor interceptor;

        public CallbackMethodInterceptor(ClassInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            StackInformation stack = this.interceptor.stackInfo.get();
            StackFrame frame = stack.frame.next;

            // Push new stack element
            if (frame == null) {
                frame = stack.frame.next = new StackFrame(stack.frame);
            }
            stack.frame = frame;

            try {
                frame.instance = obj;
                frame.proxy = proxy;
                frame.method = method;

                // Find method callback delegate if we don't know yet
                Invoker<?> callback = stack.getCallback(method);
                if (callback == null) {
                    synchronized (interceptor.globalMethodDelegates) {
                        callback = interceptor.globalMethodDelegates.get(method);
                    }
                    if (callback == null) {
                        callback = interceptor.getCallback(method);
                        if (callback == null) {
                            callback = new SuperClassInvokable(method, proxy);
                        }

                        // Register globally if needed
                        if (interceptor.useGlobalCallbacks){
                            synchronized (interceptor.globalMethodDelegates) {
                                interceptor.globalMethodDelegates.put(method, callback);
                            }
                        }
                    }
                    stack.storeCallback(method, callback);
                }

                // Make sure to inline the InterceptorCallback to avoid a stack frame
                if (callback instanceof InterceptorCallback) {
                    callback = ((InterceptorCallback) callback).interceptorCallback;
                    obj = interceptor;
                }

                // Execute the callback
                return callback.invokeVA(obj, args);
            } finally {
                // Pop stack element
                // Make sure to reset instance, otherwise we risk a memory leak
                frame.instance = null;
                stack.frame = frame.prev;
            }
        }
    }

    /**
     * This will never be used in reality and is strictly here to deal with unexpected NULL callbacks
     */
    private static final class SuperClassInvokable implements Invoker<Object> {
        private final MethodProxy proxy;
        private final Method method;

        public SuperClassInvokable(Method method, MethodProxy proxy) {
            this.method = method;
            this.proxy = proxy;
        }

        @Override
        public Object invokeVA(Object instance, Object... args) {
            try {
                return proxy.invokeSuper(instance, args);
            } catch (Throwable ex) {
                throw ReflectionUtil.fixMethodInvokeException(method, instance, args, ex);
            }
        }
    };

    /**
     * Calls a method on this interceptor when invoked. The interceptorCallback field
     * should be set to a valid invoker.
     */
    public static abstract class InterceptorCallback implements Invoker<Object> {
        public Invoker<Object> interceptorCallback = InitInvoker.unavailableMethod();

        @Override
        public Object invokeVA(Object instance, Object... args) {
            // This is never actually called because of optimizations in the method interceptor
            return interceptorCallback.invokeVA(((EnhancedObject) instance).CI_getInterceptor(), args);
        }
    }

    /**
     * We have to track the 'current' object this interceptor is handling.
     * When only a single instance is ever used, it will always be the same.
     * But with multiple instances, we must guarantee that the handler is made
     * aware of the current object when invoking from callback delegates.
     * 
     * This is also important when handling a callback -> super invocation, which
     * can re-use the method called to avoid expensive map get/put operations.
     * 
     * In addition, it stores a mapping of methods to callback delegates for use by the interceptor.
     * These are also stored here so they are thread-local, preventing cross-thread Map access.
     * 
     * Because it is thread local, it is absolutely essential all data inside this class is wiped
     * when invocation on a thread finishes. Otherwise there is a real risk of a memory leak.
     */
    private static final class StackInformation {
        // Method -> Invokable (Fast & slow method that uses Method.equals)
        private final Map<Method, Invoker<?>> methodDelegates_fast = new IdentityHashMap<>();
        private final Map<Method, Invoker<?>> methodDelegates = new HashMap<>();

        // Avoid map lookup for methods called very often
        private Method last_method = null;
        private Invoker<?> last_method_callback = null;
        public StackFrame frame = new StackFrame(null);

        public Object currentInstance() {
            return this.frame.instance;
        }

        public Invoker<?> getCallback(Method method) {
            // Last called method (Fastest)
            if (method == last_method) {
                return last_method_callback;
            }

            // From Method instance cache (Faster)
            Invoker<?> callback = methodDelegates_fast.get(method);
            if (callback != null) {
                last_method = method;
                last_method_callback = callback;
                return callback;
            }

            // From Method equals-based hashmap (slower)
            callback = methodDelegates.get(method);
            if (callback != null) {
                methodDelegates_fast.put(method, callback);
                last_method = method;
                last_method_callback = callback;
                return callback;
            }

            // Not found
            return null;
        }

        public void storeCallback(Method method, Invoker<?> callback) {
            last_method = method;
            last_method_callback = callback;
            methodDelegates.put(method, callback);
        }
    }

    /**
     * A single execution stack frame as handled by the method interceptor
     */
    private static final class StackFrame {
        public Object instance = null;
        public MethodProxy proxy = null;
        public Method method = null;
        public final StackFrame prev;
        public StackFrame next = null;

        public StackFrame(StackFrame prev) {
            this.prev = prev;
        }
    }

    /**
     * Sometimes ClassInterceptors can be copied (ClassHook!) and so we must wrap the object
     */
    private static class InstanceHolder {
        public Object value = null;
    }

    /**
     * All hooked objects implement this interface to retrieve the interceptor owner and base type
     */
    protected static interface EnhancedObject {
        public ClassInterceptor CI_getInterceptor();
        public Class<?> CI_getBaseType();
        public EnhancedClass CI_getEnhancedClass();
    }
}
