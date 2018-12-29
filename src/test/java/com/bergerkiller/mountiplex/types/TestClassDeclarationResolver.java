package com.bergerkiller.mountiplex.types;

import com.bergerkiller.mountiplex.reflection.declarations.ClassDeclaration;
import com.bergerkiller.mountiplex.reflection.declarations.SourceDeclaration;
import com.bergerkiller.mountiplex.reflection.resolver.ClassDeclarationResolver;
import com.bergerkiller.mountiplex.reflection.resolver.Resolver;

public class TestClassDeclarationResolver implements ClassDeclarationResolver {
    public static final TestClassDeclarationResolver INSTANCE = new TestClassDeclarationResolver();
    static {
        Resolver.registerClassDeclarationResolver(INSTANCE);
    }

    private final SourceDeclaration source;

    public TestClassDeclarationResolver() {
        String template = "" +
                "#resolver com.bergerkiller.mountiplex.types.TestClassDeclarationResolver.INSTANCE\n" +
                "#bootstrap {\n" +
                "com.bergerkiller.mountiplex.types.BootstrapState.CALLED_ROOT = true;\n" +
                "}\n" +
                "\n" +
                "package com.bergerkiller.mountiplex.types;\n" +
                "\n" +
                "public class TestObject {\n" +
                "    #bootstrap com.bergerkiller.mountiplex.types.BootstrapState.CALLED_TESTOBJECT = true;\n" +
                "    \n" +
                "    private static String staticField:a;\n" +
                "    private static final String staticFinalField:a_f;\n" +
                "    private String localField:b;\n" +
                "    private final String localFinalField:b_f;\n" +
                "    private (String) int intConvField:c;\n" +
                "    public final (List<String>) List<Integer> testRawField;\n" +
                "    public optional String unusedField:###;\n" +
                "    public readonly final (UniqueType) OneWayConvertableType oneWay;\n" +
                "    \n" +
                "    private int testFunc:d(int k, int l);\n" +
                "    private (String) int testConvFunc1:e(int k, int l);\n" +
                "    private int testConvFunc2:f((String) int k, (String) int l);\n" +
                "    private static (long) int testing2:g(int a, (String) int b);\n" +
                "    public int defaultInterfaceMethod();\n" +
                "    public int inheritedClassMethod();\n" +
                "    public optional int testGenerated() {\n" +
                "        return 621;\n" +
                "    }\n" +
                "}\n" +
                "package com.bergerkiller.mountiplex.types;\n" +
                "\n" +
                "class PrivateTestObject {\n" +
                "    #bootstrap com.bergerkiller.mountiplex.types.BootstrapState.CALLED_PRIVATETESTOBJECT = true;\n" +
                "    \n" +
                "    public String field;\n" +
                "    public String method();\n" +
                "}\n" +
                "package com.bergerkiller.mountiplex.types;\n" +
                "\n" +
                "public class SpeedTestObject {\n" +
                "    private int i;\n" +
                "    private double d;\n" +
                "    private String s;\n" +
                "    public final int getIMethod();\n" +
                "    public final void setIMethod(int value);\n" +
                "    public final void setSMethod(String value);\n" +
                "    public final String getSMethod();\n" +
                "    public void setLocation(double x, double y, double z, float yaw, float pitch);\n" +
                "    public int lotsOfArgs(int a, int b, int c, int d, int e, int f, int g);\n" +
                "}\n";
        this.source = SourceDeclaration.parse(template);
    }

    @Override
    public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
        if (classPath.equals("com.bergerkiller.mountiplex.types.TestObject")) {
            return source.classes[0];
        } else if (classPath.equals("com.bergerkiller.mountiplex.types.PrivateTestObject")) {
            return source.classes[1];
        } else if (classPath.equals("com.bergerkiller.mountiplex.types.SpeedTestObject")) {
            return source.classes[2];
        }
        return null;
    }

}
