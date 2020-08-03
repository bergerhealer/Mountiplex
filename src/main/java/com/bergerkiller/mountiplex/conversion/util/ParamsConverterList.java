package com.bergerkiller.mountiplex.conversion.util;

import java.util.function.Function;

import com.bergerkiller.mountiplex.MountiplexUtil;
import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.type.NullConverter;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.ParameterListDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * All the converters used to convert the arguments/parameters/return value of a method or constructor
 */
public class ParamsConverterList<T> {
    public Function<Object, T> result = null; // Is null when result value doesn't have to be converted
    public Function<Object, ?>[] args = null; // Is null when arguments don't have to be converted
    public Function<Object, ?> arg0 = null; // Equal to argConverters[0] (null if missing)
    public Function<Object, ?> arg1 = null; // Equal to argConverters[1] (null if missing)
    public Function<Object, ?> arg2 = null; // Equal to argConverters[2] (null if missing)
    public Function<Object, ?> arg3 = null; // Equal to argConverters[3] (null if missing)
    public Function<Object, ?> arg4 = null; // Equal to argConverters[4] (null if missing)
    public boolean valid = true;

    @SuppressWarnings("unchecked")
    public ParamsConverterList(String name, TypeDeclaration returnType, ParameterListDeclaration parameters) {
        this.result = null;
        this.args = null;
        this.arg0 = null;
        this.arg1 = null;
        this.arg2 = null;
        this.arg3 = null;
        this.arg4 = null;

        // Initialize the converter for the return value
        if (returnType.cast != null) {
            this.result = (Converter<?, T>) Conversion.find(returnType, returnType.cast);
            if (this.result == null) {
                this.valid = false;
                MountiplexUtil.LOGGER.warning("Converter for " + name + 
                        " return type not found: " + returnType.toString());
            } else if (this.result instanceof NullConverter) {
                this.result = null;
            }
        }

        // Converters for the arguments of the method
        ParameterDeclaration[] params = parameters.parameters;
        this.args = new Function[params.length];
        boolean hasArgumentConversion = false;
        for (int i = 0; i < params.length; i++) {
            if (params[i].type.cast != null) {
                this.args[i] = Conversion.find(params[i].type.cast, params[i].type);
                if (this.args[i] == null) {
                    this.valid = false;
                    MountiplexUtil.LOGGER.warning("Converter for " + name + 
                            " argument " + params[i].name.toString() + " not found: " + params[i].type.toString());
                } else if (this.args[i] instanceof NullConverter) {
                    this.args[i] = Function.identity();
                } else {
                    // It's ok
                    hasArgumentConversion = true;
                }
            } else {
                // No conversion
                this.args[i] = Function.identity();
            }
        }
        if (hasArgumentConversion) {
            if (params.length >= 1) this.arg0 = this.args[0];
            if (params.length >= 2) this.arg1 = this.args[1];
            if (params.length >= 3) this.arg2 = this.args[2];
            if (params.length >= 4) this.arg3 = this.args[3];
            if (params.length >= 5) this.arg4 = this.args[4];
        } else {
            this.args = null;
        }
    }

    @SuppressWarnings("unchecked")
    public final T convertResult(Object result) {
        if (this.result != null) {
            return this.result.apply(result);
        } else {
            return (T) result;
        }
    }

    public final Object[] convertArgs(Object[] arguments) {
        if (this.args != null) {
            // Got to convert the parameters
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < convertedArgs.length; i++) {
                convertedArgs[i] = this.args[i].apply(arguments[i]);
            }
            return convertedArgs;
        } else {
            return arguments;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final Supplier UNINITIALIZED_SUPPLIER = new Supplier() {
        @Override
        public ParamsConverterList get() {
            throw new UnsupportedOperationException("Converters have not been initialized");
        }

        @Override
        public boolean available() {
            return false;
        }
    };

    public static interface Supplier<T> {
        /**
         * Gets the functional parameter list. If initialization fails,
         * a runtime exception is thrown.
         * 
         * @return parameter list
         */
        ParamsConverterList<T> get();

        /**
         * Checks whether {@link #get()} will succeed.
         * 
         * @return True if available
         */
        boolean available();

        @SuppressWarnings("unchecked")
        public static <T> Supplier<T> uninitialized() {
            return UNINITIALIZED_SUPPLIER;
        }

        public static <T> Supplier<T> unsupported(final String message) {
            return new Supplier<T>() {
                @Override
                public ParamsConverterList<T> get() {
                    throw new UnsupportedOperationException(message);
                }

                @Override
                public boolean available() {
                    return false;
                }
            };
        }

        public static <T> Supplier<T> of(final ParamsConverterList<T> value) {
            return new Supplier<T>() {
                @Override
                public ParamsConverterList<T> get() {
                    return value;
                }

                @Override
                public boolean available() {
                    return true;
                }
            };
        }

        public static <T> Supplier<T> lazy(java.util.function.Supplier<Supplier<T>> supplierOfSupplier) {
            return new Supplier<T>() {
                @Override
                public ParamsConverterList<T> get() {
                    return supplierOfSupplier.get().get();
                }

                @Override
                public boolean available() {
                    return supplierOfSupplier.get().available();
                }
            };
        }
    }
}