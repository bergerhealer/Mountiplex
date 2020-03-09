package com.bergerkiller.mountiplex.conversion.type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import com.bergerkiller.mountiplex.conversion.Converter;
import com.bergerkiller.mountiplex.conversion.ConverterProvider;
import com.bergerkiller.mountiplex.conversion.annotations.ConverterMethod;
import com.bergerkiller.mountiplex.reflection.declarations.ClassResolver;
import com.bergerkiller.mountiplex.reflection.declarations.MethodDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;
import com.bergerkiller.mountiplex.reflection.util.fast.InitInvoker;
import com.bergerkiller.mountiplex.reflection.util.fast.Invoker;

/**
 * A converter that calls a static method, preferably annotated with a
 * {@link ConverterMethod} annotation
 */
public class AnnotatedConverter extends RawConverter {
    public Invoker<Object> invoker;
    public final boolean isUpcast;
    private final boolean nullInput;
    private final int cost;

    public AnnotatedConverter(MethodDeclaration method, TypeDeclaration input, TypeDeclaration output) {
        this(method, null, input, output, false);
    }

    public AnnotatedConverter(MethodDeclaration method, TypeDeclaration input, TypeDeclaration output, boolean isUpcast) {
        this(method, null, input, output, isUpcast);
    }

    private AnnotatedConverter(MethodDeclaration method, Invoker<Object> invoker, TypeDeclaration input, TypeDeclaration output, boolean isUpcast) {
        super(input, output);
        if (invoker == null) {
            this.invoker = new InitInvoker.MethodInvoker<Object>(method) {
                @Override
                protected Invoker<Object> getField() {
                    return AnnotatedConverter.this.invoker;
                }

                @Override
                protected void setField(Invoker<Object> field) {
                    AnnotatedConverter.this.invoker = field;
                }
            };
        } else {
            this.invoker = new InitInvoker.ProxyInvoker<Object>(invoker) {
                @Override
                protected Invoker<Object> getField() {
                    return AnnotatedConverter.this.invoker;
                }

                @Override
                protected void setField(Invoker<Object> field) {
                    AnnotatedConverter.this.invoker = field;
                }
            };
        }
        this.isUpcast = isUpcast;

        ConverterMethod annot = (method.method == null) ? null : method.method.getAnnotation(ConverterMethod.class);
        this.nullInput = (annot != null && annot.acceptsNull());
        this.cost = (annot == null) ? 1 : annot.cost();
    }

    @Override
    public Object convertInput(Object value) {
        Object result = invoker.invoke(null, value);
        if (!isUpcast || this.output.isAssignableFrom(result)) {
            return result;
        } else {
            return null;
        }
    }

    @Override
    public int getCost() {
        return this.cost;
    }

    @Override
    public boolean acceptsNullInput() {
        return this.nullInput;
    }

    public static TypeDeclaration parseType(Method method, boolean input) {
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
            TypeDeclaration type = TypeDeclaration.parse(resolver, typeStr);
            if (!type.isValid()) {
                throw new IllegalArgumentException("Type is invalid: " + type.toString());
            }
            if (!type.isResolved()) {
                if (annot.optional()) {
                    return null;
                } else {
                    throw new IllegalArgumentException("Type could not be resolved: " + type.toString());
                }
            }
            return type;
        } else {
            // Parse from the method signature
            return TypeDeclaration.fromType(resolver, input ? 
                    method.getGenericParameterTypes()[0] : method.getGenericReturnType());
        }
    }

    /**
     * Handles generic input/output types to provide the appropriate {@link AnnotatedConverter} for
     * requested output types. This handles generic conversion such as List&lt;T&gt; -> Set&lt;T&gt;
     */
    public static class GenericProvider implements ConverterProvider {
        public final TypeDeclaration input;
        public final TypeDeclaration output;
        public final MethodDeclaration method;
        public Invoker<Object> invoker;

        public GenericProvider(MethodDeclaration method, TypeDeclaration input, TypeDeclaration output) {
            this.input = input;
            this.output = output;
            this.method = method;
            this.invoker = new InitInvoker.MethodInvoker<Object>(method) {
                @Override
                protected Invoker<Object> getField() {
                    return invoker;
                }

                @Override
                protected void setField(Invoker<Object> field) {
                    invoker = field;
                }
            };
        }

        @Override
        public void getConverters(TypeDeclaration output, List<Converter<?, ?>> converters) {
            boolean isUpcast = false;
            TypeDeclaration relOutput;
            if (this.output.variableName != null || this.output.isWildcard) {
                // T extends <name> OR ? extends <name>'
                // When this happens, we allow for up-casting if generics permit it
                // Here we want to check if the requested output type is a derivative of this output type
                isUpcast = true;
                if (!this.output.type.isAssignableFrom(output.type)){
                    return;
                }

                // Get input-relative output type from the upcasted type
                relOutput = output.castAsType(this.output.type);
            } else {
                // No generic type information, so no upcast is used
                if (!output.type.equals(this.output.type)) {
                    return;
                }

                relOutput = output; // same type
            }

            // Process generic parameters
            TypeDeclaration[] inTypes;
            if (relOutput.genericTypes.length == 0) {
                // Raw types
                inTypes = new TypeDeclaration[0];
            } else if (relOutput.isInstanceOf(this.output) && this.output.genericTypes.length == relOutput.genericTypes.length) {
                // Generic typed convert
                inTypes = this.input.genericTypes.clone();
                for (int i = 0; i < inTypes.length; i++) {
                    // Find this declaration in the output
                    String typeName = inTypes[i].variableName;
                    if (typeName != null) {
                        boolean found = false;
                        for (int j = 0; j < this.output.genericTypes.length; j++) {
                            if (typeName.equals(this.output.genericTypes[i].variableName)) {
                                inTypes[i] = relOutput.genericTypes[i];
                                found = true;
                                break;
                            }
                        }
                        if (!found ) {
                            return;
                        }
                    }
                }
            } else {
                return;
            }

            converters.add(new AnnotatedConverter(this.method, this.invoker, this.input.setGenericTypes(inTypes), output, isUpcast));
        }

    }
}
