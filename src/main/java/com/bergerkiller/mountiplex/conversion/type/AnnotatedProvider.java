package com.bergerkiller.mountiplex.conversion.type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Uses an (annotated) method to create new converters on the fly
 */
public class AnnotatedProvider implements ConverterProvider {
    public final Method method;

    public AnnotatedProvider(Method method) {
        this.method = method;
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Converter provider method is not static");
        }
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 2 || !types[0].equals(TypeDeclaration.class) || !types[1].equals(List.class)) {
            throw new IllegalArgumentException("Converter provider method does not have the expected method signature");
        }
    }

    @Override
    public void getConverters(TypeDeclaration output, List<Converter<?, ?>> converters) {
        try {
            this.method.invoke(null, output, converters);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
