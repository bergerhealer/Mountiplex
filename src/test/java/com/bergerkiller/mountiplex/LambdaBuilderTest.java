package com.bergerkiller.mountiplex;

import com.bergerkiller.mountiplex.reflection.util.LambdaBuilder;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class LambdaBuilderTest {

    @Test
    public void testBuildFunction() {
        LambdaBuilder<Function<String, String>> lambda = LambdaBuilder.of(Function.class);
        Function<String, String> func = lambda.create((instance, args) -> (String) args[0] + "APPEND");
        assertEquals("INPUTAPPEND", func.apply("INPUT"));
    }

    @Test
    public void testConstantReturningFunction() {
        LambdaBuilder<Function<String, String>> lambda = LambdaBuilder.of(Function.class);
        Function<String, String> func = lambda.createConstant("CONSTANT");
        assertEquals("CONSTANT", func.apply("INPUT"));
    }
}
