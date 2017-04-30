package com.bergerkiller.mountiplex.conversion2.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.bergerkiller.mountiplex.conversion2.annotations.ConverterMethod;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * A converter that calls a static method, preferably annotated with a
 * {@link ConverterMethod} annotation
 */
public class AnnotatedConverter extends RawConverter {
    public final Method method;

    public AnnotatedConverter(Method method) {
        super(parseType(method, true), parseType(method, false));
        this.method = method;
    }

    @Override
    public Object convertInput(Object value) {
        try {
            return method.invoke(null, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static TypeDeclaration parseType(Method method, boolean input) {
        ClassResolver resolver = ClassResolver.DEFAULT; // should this be different?

        // Verify the method signature: static, one parameter, non-void return type
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method is not static");
        }
        if (method.getReturnType().equals(void.class)) {
            throw new IllegalArgumentException("Method has no return type");
        }
        if (method.getParameterTypes().length != 1) {
            throw new IllegalArgumentException("Method does not have one parameter");
        }

        // Make sure the method is accessible
        method.setAccessible(true);

        ConverterMethod annot = method.getAnnotation(ConverterMethod.class);
        String typeStr = (annot == null) ? "" : (input ? annot.input() : annot.output());
        if (typeStr.length() > 0) {
            // Parse from the annotation
            TypeDeclaration type = new TypeDeclaration(resolver, typeStr);
            if (!type.isValid()) {
                throw new IllegalArgumentException("Type is invalid: " + type.toString());
            }
            if (!type.isResolved()) {
                throw new IllegalArgumentException("Type could not be resolved: " + type.toString());
            }
            return type;
        } else {
            // Parse from the method signature
            return new TypeDeclaration(resolver, input ? 
                    method.getGenericParameterTypes()[0] : method.getGenericReturnType());
        }
    }
}
