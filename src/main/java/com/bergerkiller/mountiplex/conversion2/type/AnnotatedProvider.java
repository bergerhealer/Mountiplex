package com.bergerkiller.mountiplex.conversion2.type;

import java.lang.reflect.Method;
import java.util.List;

import com.bergerkiller.mountiplex.conversion2.Converter;
import com.bergerkiller.mountiplex.conversion2.ConverterProvider;
import com.bergerkiller.mountiplex.reflection.declarations.TypeDeclaration;

/**
 * Handles generic input/output types to provide the appropriate {@link AnnotatedConverter} for
 * requested output types. This handles generic conversion such as List&lt;T&gt; -> Set&lt;T&gt;
 */
public class AnnotatedProvider implements ConverterProvider {
    public final TypeDeclaration input;
    public final TypeDeclaration output;
    public final Method method;

    public AnnotatedProvider(Method method) {
        this.input = AnnotatedConverter.parseType(method, true);
        this.output = AnnotatedConverter.parseType(method, false);
        this.method = method;
    }

    @Override
    public void getConverters(TypeDeclaration output, List<Converter<?, ?>> converters) {
        if (output.type.equals(this.output.type) && output.isInstanceOf(this.output) &&
                this.output.genericTypes.length == output.genericTypes.length) {

            TypeDeclaration[] inTypes = this.input.genericTypes.clone();
            for (int i = 0; i < inTypes.length; i++) {
                // Find this declaration in the output
                String typeName = inTypes[i].typeName;
                boolean found = false;
                if (typeName.length() == 1) {
                    for (int j = 0; j < this.output.genericTypes.length; j++) {
                        if (this.output.genericTypes[i].typeName.equals(typeName)) {
                            inTypes[i] = output.genericTypes[i];
                            found = true;
                            break;
                        }
                    }
                }
                if (!found ) {
                    return;
                }
            }
            converters.add(new AnnotatedConverter(this.method, this.input.setGenericTypes(inTypes), output));
        }
    }

}
