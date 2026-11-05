# Generating a Class at Runtime (Example)

Implements an interface with ASM using [`ExtendedClassWriter`](../class-generation.md). The class has a member field, one method with hand-written instructions, and one that returns a constant.
It was run against Mountiplex, and printed `Hello, World 42` for the calls below. Compiling it needs ASM on the classpath, see
[Dependencies](../class-generation.md#dependencies-asm-must-be-added-and-relocated-by-you).

```java
public interface Greeter {
    String greet(String who);
    int count();
}

ExtendedClassWriter<Greeter> cw = ExtendedClassWriter.builder(Greeter.class)
        .setSingleton(true)
        .build();

// private final String prefix, initialized with a value from this code
cw.visitSingletonField("prefix", String.class, "Hello, ");

// String greet(String who) { return this.prefix.concat(who); }
MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "greet",
        MPLType.getMethodDescriptor(Greeter.class.getMethod("greet", String.class)), null, null);
mv.visitCode();
mv.visitVarInsn(ALOAD, 0);
mv.visitFieldInsn(GETFIELD, cw.getInternalName(), "prefix", MPLType.getDescriptor(String.class));
mv.visitVarInsn(ALOAD, 1);
ExtendedClassWriter.visitInvoke(mv, String.class, String.class.getMethod("concat", String.class));
mv.visitInsn(ARETURN);
mv.visitMaxs(2, 2);
mv.visitEnd();

// int count() { return 42; }
cw.visitMethodReturnConstant(Greeter.class.getMethod("count"), 42);

Greeter greeter = cw.generateInstance();
greeter.greet("World"); // "Hello, World"
greeter.count();        // 42
```

What happens:

1. The interface means the class extends `Object` and implements `Greeter`. Because of `setSingleton(true)` a private constructor and an `INSTANCE` field are generated, and `generateInstance()` returns that instance.
2. `visitSingletonField` adds the `prefix` field and the value is delivered to it when the instance is created.
3. Names and descriptors come from `MPLType` and the class's own name from `cw.getInternalName()`, never from string literals, so they stay correct when the class is renamed or remapped.
4. The generated class got the name `<Greeter's name>$mpldefgen<hex>`.

Without `setSingleton(true)`, a no-argument constructor must be written by hand (a method named `<init>` that calls the super constructor and returns), or the instance must be created with
`generateInstanceNull()`, which skips constructors. Both were verified. Without either, `generateInstance()` fails with `NoSuchMethodException`.
