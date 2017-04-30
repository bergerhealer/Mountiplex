package com.bergerkiller.mountiplex.conversion2;

public @interface ConverterAnnot {
    String input() default "";
    String output() default "";
}
