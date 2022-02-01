package com.bergerkiller.mountiplex.reflection.util;

import com.bergerkiller.mountiplex.reflection.ReflectionUtil;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;
import com.bergerkiller.mountiplex.reflection.util.asm.javassist.MPLMemberResolver;

/**
 * Wraps a StringBuilder to cleanly generate Java method bodies.
 * Offers additional helper methods for generating code constructs.
 */
public final class MethodBodyBuilder {
    public final StringBuilder builder = new StringBuilder();

    /**
     * Appends a statement end (;) and a newline
     *
     * @return this
     */
    public MethodBodyBuilder appendEnd() {
        builder.append(";\n");
        return this;
    }

    public MethodBodyBuilder append(String text) {
        builder.append(text);
        return this;
    }

    public MethodBodyBuilder appendFieldName(String text, String postfix) {
        builder.append(text).append(postfix);
        return this;
    }

    public MethodBodyBuilder append(char character) {
        builder.append(character);
        return this;
    }

    public MethodBodyBuilder append(int value) {
        builder.append(value);
        return this;
    }

    public MethodBodyBuilder appendBoxPrimitive(Class<?> primitiveType, String input, String input_postfix) {
        builder.append(BoxedType.getBoxedType(primitiveType).getSimpleName());
        builder.append(".valueOf(").append(input).append(input_postfix).append(')');
        return this;
    }

    public MethodBodyBuilder appendUnboxPrimitive(Class<?> boxedType) {
        builder.append('.').append(boxedType.getSimpleName());
        builder.append("Value()");
        return this;
    }

    public MethodBodyBuilder appendTypeName(Class<?> type) {
        return append(ReflectionUtil.getTypeName(type));
    }

    public MethodBodyBuilder appendTypeCast(Class<?> castType) {
        builder.append('(').append(ReflectionUtil.getTypeName(castType)).append(')');
        return this;
    }

    public MethodBodyBuilder appendBoxPrimitive(Class<?> primitiveType, String input) {
        builder.append(BoxedType.getBoxedType(primitiveType).getSimpleName());
        builder.append(".valueOf(").append(input).append(')');
        return this;
    }

    public MethodBodyBuilder appendAccessibleTypeName(Class<?> type) {
        return append(ReflectionUtil.getAccessibleTypeName(type));
    }

    public MethodBodyBuilder appendAccessibleTypeCast(Class<?> castType) {
        if (castType != Object.class && Resolver.isPublic(castType)) {
            String name = ReflectionUtil.getTypeName(castType);

            // If a resolver would 'double-resolve' the type name, prefix with $mpl
            // This prevents accidents like that
            if (Resolver.resolveClassPath(name).equals(name)) {
                builder.append('(').append(name).append(')');
            } else {
                builder.append('(');
                builder.append(MPLMemberResolver.IGNORE_PREFIX).append(name);
                builder.append(')');
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return builder.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof MethodBodyBuilder) {
            return builder.equals(((MethodBodyBuilder) o).builder);
        } else {
            return false;
        }
    }
}
