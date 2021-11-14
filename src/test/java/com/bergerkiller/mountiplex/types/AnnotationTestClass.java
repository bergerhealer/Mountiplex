package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

/*
 * <TEST_REQUIREMENT_A>
 * com.bergerkiller.mountiplex.types.AnnotationTestClass public static String generatedBody() {
 *     // This is a comment
 *     return "generated123";
 * }
 */
@Template.Require("%TEST_REQUIREMENT_A%")
/*
 * <TEST_REQUIREMENT_B>
 * public static String testMethod();
 */
@Template.Require(declaring="com.bergerkiller.mountiplex.types.AnnotationTestClass",
                  value="%TEST_REQUIREMENT_B%")
@Template.Require(declaring="com.bergerkiller.mountiplex.types.AnnotationTestClass",
                  value="public static int testFieldAlias:testFieldUsedInRequirement;")
public abstract class AnnotationTestClass extends Template.Class<Template.Handle> {

    public static String someStringWithTheToken = "/* <TEST_REPLACEMENT> **/";
    public static String someStringWithTheToken2 = "<TEST_REPLACEMENT>";
    public static int testFieldUsedInRequirement = 200;

    public static String testMethod() {
        return "hello123";
    }

    /*
     * <TEST_REQUIREMENTS>
     * public static String testRequirements() {
     *     String a = #generatedBody();
     *     String b = #testMethod();
     *     String c = Integer.toString(#testFieldAlias);
     *     return a + "/" + b + "/" + c;
     * }
     */
    @Template.Generated("%TEST_REQUIREMENTS%")
    public abstract String testRequirements();

    /* <TEST_REPLACEMENT1>
     * public void test() {
     *     System.out.println("Hello, world!");
     * 
     *     // Comment
     *     
     *     // Spaces
     * }
     */
    @Template.Generated("%TEST_REPLACEMENT1%")
    public abstract void test1();

    /**
     * <TEST_REPLACEMENT2>
     * public void test() {
     *     System.out.println("Hello, world!");
     * 
     *     // Comment
     *     
     *     // Spaces
     * }
     */
    @Template.Generated("%TEST_REPLACEMENT2%")
    public abstract void test2();

    /*
     * <TEST_REPLACEMENT3>
     * public void test() {
     *     System.out.println("Hello, world!");
     * 
     *     // Comment
     *     
     *     // Spaces
     * }
     */
    @Template.Generated("%TEST_REPLACEMENT3%")
    public abstract void test3();

    /*
    <TEST_REPLACEMENT4>
    public void test() {
        System.out.println("Hello, world!");

        // Comment
        
        // Spaces
    }
    */
    @Template.Generated("%TEST_REPLACEMENT4%")
    public abstract void test4();
}
