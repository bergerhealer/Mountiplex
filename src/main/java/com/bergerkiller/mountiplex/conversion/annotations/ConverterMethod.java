package com.bergerkiller.mountiplex.conversion.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a static method as an input-output converter
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConverterMethod {
    /**
     * If not left empty, declares the input type for the converter.
     * 
     * @return Input type declaration
     */
    String input() default "";
    /**
     * If not left empty, declares the output type result of the converter.
     * 
     * @return Output type declaration
     */
    String output() default "";
    /**
     * Whether null inputs are valid for this converter method.
     * 
     * @return True if null is allowed, False if not
     */
    boolean acceptsNull() default false;
    /**
     * Specifies whether the input/output declaration is allowed to not exist at runtime.
     * This disabled error reporting for missing types.
     * 
     * @return True if optional, False if the converter must be resolved
     */
    boolean optional() default false;
    /**
     * Sets the conversion cost, which controls the priority and order converters are used
     */
    int cost() default 1;
}
