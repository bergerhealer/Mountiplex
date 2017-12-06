package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.ConstructorDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.Constructor;
import com.bergerkiller.mountiplex.reflection.util.fast.ReflectionConstructor;

public class FastConstructor<T> implements Constructor<T>, LazyInitializedObject {
    private Constructor<T> constructor;
    private ConstructorDeclaration constructorDec;
    private String missingInfo = "!!UNKNOWN!!"; // stored info for when constructor is null

    public FastConstructor() {
        this.constructorDec = null;
        this.constructor = new FastConstructorInitProxy();
    }

    public FastConstructor(java.lang.reflect.Constructor<?> constructor) {
        this.constructorDec = new ConstructorDeclaration(ClassResolver.DEFAULT, constructor);
        this.constructor = new FastConstructorInitProxy();
    }

    public final void init(java.lang.reflect.Constructor<?> constructor) {
        this.constructorDec = new ConstructorDeclaration(ClassResolver.DEFAULT, constructor);
        this.constructor = new FastConstructorInitProxy();
    }

    public final void init(ConstructorDeclaration constructorDeclaration) {
        if (constructorDeclaration != null && constructorDeclaration.constructor == null) {
            this.constructorDec = null;
            this.constructor = new FastConstructorInitProxy();
        } else {
            this.constructorDec = constructorDeclaration;
            this.constructor = new FastConstructorInitProxy();
        }
    }

    /**
     * Declares this constructor to be unavailable, providing a missing information String to later identify it
     * 
     * @param missingInfo to print when trying to access it
     */
    public final void initUnavailable(String missingInfo) {
        this.constructorDec = null;
        this.constructor = new FastConstructorInitProxy();
        this.missingInfo = missingInfo;
    }

    /**
     * Checks whether this fast constructor is initialized, and throws an exception if it is not.
     */
    public final void checkInit() {
        if (constructorDec == null) {
            throw new UnsupportedOperationException("Constructor " + this.missingInfo + " is not available");
        }
    }

    /**
     * Checks whether this constructor is available
     * 
     * @return True if the constructor is available
     */
    public final boolean isAvailable() {
        return this.constructorDec != null;
    }

    /**
     * Gets the backing Java Reflection Constructor for this Fast Constructor. If this fast constructor
     * is not initialized, this function returns <i>null</i>.
     * To check whether this fast constructor is initialized, use {@link #isAvailable()}.
     * 
     * @return constructor
     */
    public final java.lang.reflect.Constructor<?> getConstructor() {
        return this.constructorDec == null ? null : this.constructorDec.constructor;
    }

    /**
     * Gets whether this Fast Constructor represents a class constructor exactly
     * 
     * @param constructor to check
     * @return True if it manages the same Constructor, False if not
     */
    public final boolean isConstructor(java.lang.reflect.Constructor<?> constructor) {
        return this.constructorDec != null && this.constructorDec.constructor != null && this.constructorDec.constructor.equals(constructor);
    }

    /**
     * Gets the name of this constructor.
     * Returns <i>"null"</i> if this fast constructor is not initialized.
     * 
     * @return constructor name
     */
    public final String getName() {
        if (this.constructorDec == null) {
            return "null";
        } else {
            StringBuilder name = new StringBuilder();
            name.append("constr");
            for (ParameterDeclaration param : this.constructorDec.parameters.parameters) {
                name.append(param.name.real());
            }
            return name.toString();
        }
    }

    @Override
    public void forceInitialization() {
        if (this.constructor instanceof FastConstructor.FastConstructorInitProxy) {
            ((FastConstructorInitProxy) this.constructor).init();
        }
    }

    @Override
    public T newInstanceVA(Object... args) {
        return constructor.newInstanceVA(args);
    }

    @Override
    public T newInstance() {
        return constructor.newInstance();
    }

    @Override
    public T newInstance(Object arg0) {
        return constructor.newInstance(arg0);
    }

    @Override
    public T newInstance(Object arg0, Object arg1) {
        return constructor.newInstance(arg0, arg1);
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2) {
        return constructor.newInstance(arg0, arg1, arg2);
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
        return constructor.newInstance(arg0, arg1, arg2, arg3);
    }

    @Override
    public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
        return constructor.newInstance(arg0, arg1, arg2, arg3, arg4);
    }

    // This object is used at the first call to initialize the constructor
    private final class FastConstructorInitProxy implements Constructor<T> {
        /**
         * Initializes this constructor invoker. The method will be found through
         * reflection and optimized accessors compiled. All of this is only performed once.
         * 
         * All calls to invoke() in this class explicitly call init().
         * 
         * @return the new invoker that should be used from now on
         */
        private final Constructor<T> init() {
            if (constructor == this) {
                synchronized (FastConstructor.this) {
                    if (constructor == this) {
                        checkInit();

                        // Calls an existing member method
                        constructorDec.constructor.setAccessible(true);
                        constructor = ReflectionConstructor.create(constructorDec.constructor);
                    }
                }
            }
            return constructor;
        }

        @Override
        public T newInstanceVA(Object... args) {
            return init().newInstanceVA(args);
        }

        @Override
        public T newInstance() {
            return init().newInstance();
        }

        @Override
        public T newInstance(Object arg0) {
            return init().newInstance(arg0);
        }

        @Override
        public T newInstance(Object arg0, Object arg1) {
            return init().newInstance(arg0, arg1);
        }

        @Override
        public T newInstance(Object arg0, Object arg1, Object arg2) {
            return init().newInstance(arg0, arg1, arg2);
        }

        @Override
        public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3) {
            return init().newInstance(arg0, arg1, arg2, arg3);
        }

        @Override
        public T newInstance(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            return init().newInstance(arg0, arg1, arg2, arg3, arg4);
        }
    }
}
