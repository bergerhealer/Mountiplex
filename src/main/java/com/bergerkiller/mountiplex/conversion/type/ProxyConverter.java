package com.bergerkiller.mountiplex.conversion.type;

import com.bergerkiller.mountiplex.conversion.BasicConverter;
import com.bergerkiller.mountiplex.conversion2.Conversion;
import com.bergerkiller.mountiplex.conversion2.type.InputConverter;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;

@Deprecated
public class ProxyConverter<T> extends BasicConverter<T> {
    private InputConverter<T> converter = null;
    private final boolean isCastingSupported;

    public ProxyConverter(Class<?> outputType) {
        this(outputType, false);
    }

    public ProxyConverter(Class<?> outputType, boolean isCastingSupported) {
        super(outputType);
        this.isCastingSupported = isCastingSupported;
    }

    public ProxyConverter(ClassResolver resolver, String outputName, boolean isCastingSupported) {
        super(resolver, outputName);
        this.isCastingSupported = isCastingSupported;
    }

    @Override
    public final boolean isCastingSupported() {
        return this.isCastingSupported;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected final T convertSpecial(Object value, Class<?> valueType, T def) {
        if (this.converter == null) {
            this.converter = (InputConverter<T>) Conversion.find(this.getOutput());
            if (this.converter == null) {
                throw new RuntimeException("Converter to " + this.getOutput() + " not found");
            }
        }
        return this.converter.convert(value, def);
    }
}
