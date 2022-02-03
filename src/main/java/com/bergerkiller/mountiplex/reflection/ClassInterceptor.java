package com.bergerkiller.mountiplex.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.objenesis.ObjenesisHelper;
import org.objenesis.instantiator.ObjectInstantiator;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.reflection.util.asm.MPLType;
import com.bergerkiller.mountiplex.reflection.util.fast.ClassFieldCopier;
import com.bergerkiller.mountiplex.reflection.util.fast.ConstantReturningInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.GeneratedHook;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

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
     * To not intercept the Method, return NULL.<br>
     * <br>
     * If you require information about what Class this interceptor is handling, override
     * {@link #getCallback(Class, Method)} as well. If that method is overrided, and the
     * super method is not called, then this method isn't called anymore.
     *
     * @param method Method potentially being hooked
     * @return Callback delegate to execute, or NULL to not intercept the Method
     */
    protected abstract Invoker<?> getCallback(Method method);

    /**
     * Retrieves the callback delegate for handling a certain Method call in the base object.
     * To not intercept the Method, return NULL.<br>
     * <br>
     * Default implementation calls {@link #getCallback(Method)}. When overriding you can
     * call super.getCallback(hookedType, method) to call it.
     * 
     * @param hookedType Class type that is currently being hooked, and this method is of
     * @param method Method potentially being hooked
     * @return Callback delegate to execute, or NULL to not intercept the Method
     */
    protected Invoker<?> getCallback(Class<?> hookedType, Method method) {
        return getCallback(method);
    }

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
        return GeneratedHook.createSuperInvoker(instance.getClass(), method).invokeVA(instance, args);
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
        if (objectType == null) {
            throw new IllegalArgumentException("Input class type to be intercepted is null");
        }

        // The key used to access the EnhancedClass instance for creating this instance
        final ClassPair key = new ClassPair(interceptor.getClass(), objectType);

        // Try to find the CGLib-generated enhanced class that provides the needed callbacks
        // If none exists yet, generate a new one and put it into the table for future re-use
        EnhancedClass enhanced = enhancedTypes.get(key);
        if (enhanced == null) {
            final EnhancedClass new_enhanced = new EnhancedClass(objectType);
            final StackInformation current_stack = interceptor.stackInfo.get();

            new_enhanced.setupEnhancedType(GeneratedHook.generate(interceptor.getClass().getClassLoader(),
                                           objectType,
                                           Arrays.asList(EnhancedObject.class),
                                           method -> {
                String name = MPLType.getName(method);

                // Implement EnhancedObject interface
                if (method.getParameterCount() == 0) {
                    if (name.equals("CI_getInterceptor")) {
                        return new_enhanced.getInterceptorCallback;
                    } else if (name.equals("CI_getBaseType")) {
                        return ConstantReturningInvoker.of(objectType);
                    } else if (name.equals("CI_getEnhancedClass")) {
                        return ConstantReturningInvoker.of(new_enhanced);
                    }
                }

                // Ask interceptor what methods to intercept
                Invoker<?> callback = interceptor.getCallback(objectType, method);
                if (callback == null) {
                    return null;
                }

                // Register the callback
                current_stack.storeCallback(method, callback);

                // Create callback handler for this method
                // This interceptor will call the actual callback
                return new CallbackMethodInterceptor(method);
            }));

            // Finally create the enhanced class type and store it in the mapping for later use
            enhanced = new_enhanced;
            enhancedTypes.put(key, enhanced);
            interceptor.onClassGenerated(enhanced.enhancedType);
        }

        // Create the enhanced object instance using Objenesis
        // Explicitly initialize the result of CI_getInterceptor()
        enhanced.currentInterceptor = interceptor;
        T enhancedObject = enhanced.createEnhanced(object, paramTypes, params);
        interceptor.lastHookedObject.value = enhancedObject;
        ((EnhancedObject) enhancedObject).CI_getInterceptor();
        enhanced.currentInterceptor = null;
        return enhancedObject;
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
     * Maintains metadata information about a particular CGLib-enhanced class.
     * Also handles the construction of new objects during hooking/unhooking.
     */
    public static final class EnhancedClass {
        public final Class<?> baseType;
        public final ObjectInstantiator<?> baseInstantiator;
        public final Invoker<?> getInterceptorCallback;
        private final ClassFieldCopier<Object> baseFieldCopier;
        public Class<?> enhancedType;
        public ObjectInstantiator<?> enhancedInstantiator;
        public ClassInterceptor currentInterceptor;

        @SuppressWarnings("unchecked")
        public EnhancedClass(Class<?> baseType) {
            this.baseType = baseType;
            this.baseInstantiator = ObjenesisHelper.getInstantiatorOf(baseType);
            if (this.baseInstantiator == null)
                throw new RuntimeException("Base Class " + MPLType.getName(baseType) + " has no instantiator");

            // These are used for transferring all fields from one Object to another
            this.baseFieldCopier = (ClassFieldCopier<Object>) ClassFieldCopier.of(baseType);

            // Initializes the CI_getInterceptor() function, stores it in a field
            this.getInterceptorCallback = GeneratedHook.createLocalField(() -> currentInterceptor);
        }

        public void setupEnhancedType(Class<?> enhancedType) {
            this.enhancedType = enhancedType;
            this.enhancedInstantiator = ObjenesisHelper.getInstantiatorOf(enhancedType);
            if (this.enhancedInstantiator == null)
                throw new RuntimeException("Enhanced Class " + MPLType.getName(enhancedType) + " has no instantiator");
        }

        @SuppressWarnings("unchecked")
        public <T> T createBase(T enhanced) {
            Object base = this.baseInstantiator.newInstance();
            if (base == null)
                throw new RuntimeException("Class " + MPLType.getName(baseType) + " could not be instantiated (newInstance failed)");

            this.baseFieldCopier.copy(enhanced, base);
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
                    MountiplexUtil.LOGGER.log(Level.SEVERE, "Failed to construct " + MPLType.getName(enhancedType), t);
                }
            }

            if (enhanced == null)
                throw new RuntimeException("Class " + MPLType.getName(enhancedType) + " could not be instantiated (newInstance failed)");

            if (base != null) {
                this.baseFieldCopier.copy(base, enhanced);
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
    private static class CallbackMethodInterceptor implements Invoker<Object> {
        private final Method method;

        public CallbackMethodInterceptor(Method method) {
            this.method = method;
        }

        @Override
        public Object invokeVA(Object instance, Object... args) {
            ClassInterceptor interceptor = ((EnhancedObject) instance).CI_getInterceptor();
            StackInformation stack = interceptor.stackInfo.get();
            StackFrame frame = stack.frame.next;

            // Push new stack element
            if (frame == null) {
                frame = stack.frame.next = new StackFrame(stack.frame);
            }
            stack.frame = frame;

            try {
                frame.instance = instance;

                // Find method callback delegate if we don't know yet
                Invoker<?> callback = stack.getCallback(method);
                if (callback == null) {
                    synchronized (interceptor.globalMethodDelegates) {
                        callback = interceptor.globalMethodDelegates.get(method);
                    }
                    if (callback == null) {
                        callback = interceptor.getCallback(interceptor.instanceBaseType(), method);
                        if (callback == null) {
                            callback = GeneratedHook.createSuperInvoker(instance.getClass(), method);
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
                    instance = interceptor;
                }

                // Execute the callback
                return callback.invokeVA(instance, args);
            } finally {
                // Pop stack element
                // Make sure to reset instance, otherwise we risk a memory leak
                frame.instance = null;
                stack.frame = frame.prev;
            }
        }
    }

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
