package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.Template;

public abstract class AnnotationTestClass {

    public static String someStringWithTheToken = "/* <TEST_REPLACEMENT> **/";
    public static String someStringWithTheToken2 = "<TEST_REPLACEMENT>";

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
