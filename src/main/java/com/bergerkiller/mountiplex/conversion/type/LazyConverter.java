package com.bergerkiller.mountiplex.conversion.type;

import java.util.ArrayList;
import java.util.List;

import com.bergerkiller.mountiplex.conversion.Conversion;
import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.LazyInitializedObject;

/**
 * Converter that lazily initializes. The first time a conversion is requested,
 * it will request the converter to use, throwing an exception if none could be found.
 */
public final class LazyConverter<I, O> extends Converter<I, O> implements LazyInitializedObject {
    public Converter<I, O> converter;

    private LazyConverter(Converter<I, O> constant) {
        super(constant.input, constant.output);
        this.converter = constant;
    }

    private LazyConverter(TypeDeclaration input, TypeDeclaration output) {
        super(input, output);
        this.converter = new InitConverter(input, output);
    }

    /**
     * Initializes this lazy converter, and then checks whether this initialization was successful.
     * If not successful, false is returned, rather than throwing an exception.
     * 
     * @return True if the lazy converter is available
     */
    public boolean isAvailable() {
        Converter<I, O> c = this.converter;
        if (c instanceof LazyInitializedObject) {
            ((LazyInitializedObject) c).forceInitialization();
        }

        return !(this.converter instanceof FailingConverter);
    }

    /**
     * Calls the consumer with the failing converter when the conversion fails to be done
     * in the future. If it has already failed, the consumer is called right away.
     * 
     * @param consumer
     */
    public void whenFailing(FailCallback consumer) {
        Converter<I, O> c = this.converter;
        if (c instanceof LazyConverter.InitConverter) {
            ((InitConverter) c).failConsumers.add(consumer);
        } else if (c instanceof FailingConverter) {
            consumer.failed(c.input, c.output);
        }
    }

    @Override
    public O convertInput(I value) {
        return converter.convertInput(value);
    }

    @Override
    public boolean acceptsNullInput() {
        return converter.acceptsNullInput();
    }

    // Stored in the converter field until the converter is initialized
    private final class InitConverter extends Converter<I, O> implements LazyInitializedObject {
        private final List<FailCallback> failConsumers = new ArrayList<>(); 

        public InitConverter(TypeDeclaration input, TypeDeclaration output) {
            super(input, output);
        }

        @Override
        public void forceInitialization() {
            init();
        }

        @SuppressWarnings("unchecked")
        private Converter<I, O> init() {
            Converter<I, O> result = (Converter<I, O>) Conversion.find(this.input, this.output);
            if (result != null) {
                converter = result;
            } else {
                FailingConverter<I, O> f = FailingConverter.create(this.input, this.output,
                        "Converter not found: " + this.input.toString(true) + " -> " + this.output.toString(true));
                converter = f;

                for (FailCallback consumer : failConsumers) {
                    consumer.failed(this.input, this.output);
                }
            }
            return converter;
        }

        @Override
        public O convertInput(I value) {
            return init().convertInput(value);
        }

        @Override
        public boolean acceptsNullInput() {
            return init().acceptsNullInput();
        }
    }

    @Override
    public void forceInitialization() {
        Converter<I, O> c = this.converter;
        if (c instanceof LazyInitializedObject) {
            ((LazyInitializedObject) c).forceInitialization();
        }

        c = this.converter;
        if (c instanceof FailingConverter) {
            throw new UnsupportedOperationException(((FailingConverter<I, O>) c).getMessage());
        }
    }

    @SuppressWarnings("rawtypes")
    private static final LazyConverter UNINITIALIZED = of(FailingConverter.uninitialized());

    /**
     * Creates a LazyConverter that is initially uninitialized, failing all the time with
     * an exception if called.
     * 
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <I, O> LazyConverter<I, O> uninitialized() {
        return UNINITIALIZED;
    }

    /**
     * Creates a new LazyConverter that is already initialized to use the converter specified.
     * This helps combining lazy converters and non-lazy ones.
     * 
     * @param converter
     * @return lazy converter
     */
    public static <I, O> LazyConverter<I, O> of(Converter<I, O> converter) {
        return new LazyConverter<I, O>(converter);
    }

    /**
     * Creates a new LazyConverter for converting from the input class type to the output class type
     * 
     * @param input class type
     * @param output class type
     * @return lazy converter
     */
    public static <I, O> LazyConverter<I, O> create(Class<?> input, Class<?> output) {
        return new LazyConverter<I, O>(TypeDeclaration.fromClass(input), TypeDeclaration.fromClass(output));
    }

    /**
     * Creates a new LazyConverter for converting from the input type to the output type
     * 
     * @param input type
     * @param output type
     * @return lazy converter
     */
    public static <I, O> LazyConverter<I, O> create(TypeDeclaration input, TypeDeclaration output) {
        return new LazyConverter<I, O>(input, output);
    }

    /**
     * Callback that can be used in {@link #whenFailing(FailCallback)}
     */
    public static interface FailCallback {
        public void failed(TypeDeclaration input, TypeDeclaration output);
    }
}
