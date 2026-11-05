# Generating Classes with ASM (`ExtendedClassWriter`, `MPLType`)

How to write a new class at runtime as bytecode, to implement an interface or extend a class that cannot be written in Java at compile time (for example because
the types it needs only exist on some versions of the environment, or are not public). Mountiplex uses it for its own generated code, and
[BKCommonLib](https://github.com/bergerhealer/BKCommonLib) uses it for a few classes that implement version-specific interfaces (for example
`EmptyBlockGetterInit`, `NullPacketDataSerializerInit`). Source: `src/main/java/com/bergerkiller/mountiplex/reflection/util/ExtendedClassWriter.java` and
`.../util/asm/MPLType.java`.

For calling code that is available as text, prefer [inline methods](examples/inline-methods.md) or [templates](template-syntax.md): they need no bytecode
knowledge. Use this page when a whole class must exist, for example to implement a version-specific interface.
A complete working example is in [examples/generate-class.md](examples/generate-class.md).

## Dependencies: ASM Must Be Added and Relocated by You

`ExtendedClassWriter` extends ASM's `ClassWriter`, and its `visit*` methods take and return ASM types (`MethodVisitor`, `FieldVisitor`) and use the
constants of `org.objectweb.asm.Opcodes`. Code that calls them must therefore be compiled against ASM. Two things need to be done by the project that
uses it:

1. **Add ASM to the compile classpath yourself** (`org.ow2.asm:asm`, the same version Mountiplex uses, currently 9.7), as a compile-only dependency.
   BKCommonLib uses ASM internally and does **not** pass it on to projects that depend on it, so the ASM types are otherwise not visible and the code does not compile.
2. **Relocate ASM when shading.** BKCommonLib ships Mountiplex and its dependencies relocated, with ASM at
   `com.bergerkiller.mountiplex.dep.org.objectweb.asm` (also `org.objenesis` and `javassist` under that same `dep` prefix).
   The `ExtendedClassWriter` in the BKCommonLib jar therefore extends a `ClassWriter` at that relocated name. Your own jar must have its
   `org.objectweb.asm` references rewritten to the same name, or it fails at runtime with errors such as `NoClassDefFoundError`/`NoSuchMethodError` on `ClassWriter`:

```kotlin
// Gradle shadow plugin
relocate("org.objectweb.asm", "com.bergerkiller.mountiplex.dep.org.objectweb.asm")
```

Maven Shade takes the same two names as the `pattern` and `shadedPattern` of a relocation. Do **not** include ASM itself in your jar: relocation
only rewrites your references, and the relocated classes are provided by BKCommonLib at runtime. Projects that use Mountiplex directly, without BKCommonLib,
add ASM as a normal dependency and relocate it wherever they like, as long as it is the same everywhere the library is shaded.

## Overview

| Type | Role |
|---|---|
| `ExtendedClassWriter<T>` | An ASM `ClassWriter` that is set up to extend a class and/or implement interfaces. You add fields and methods through the standard `visit*` calls, then generate the class. |
| `ExtendedClassWriter.Builder<T>` | Configures the writer: super class, extra interfaces, name, singleton mode, class loader. Created with `ExtendedClassWriter.builder(SuperClass.class)`. |
| `ExtendedClassWriter.Deferred<T>` | A class whose name is decided now, but which is only generated when first needed. |
| `MPLType` | Names, descriptors and opcode helpers for writing bytecode, in place of ASM's `Type` and `Class.getName()`. |
| `GeneratorClassLoader` | The class loader that defines the generated class. One per base class loader, chosen automatically. |
| `GeneratorArgumentStore` | Hands objects to a generated class when it initializes (used for the static and singleton fields). |

## Workflow

1. **Build the writer.** `ExtendedClassWriter.builder(Type.class)`, then optional settings, then `build()`. If the type is an interface, the generated class extends `Object` and
   implements it; if it is a class, that class is extended. Extra interfaces are added with `addInterface`.
2. **Write members** with the ASM calls on the writer: `visitField`, `visitMethod` (plus the `MethodVisitor` instructions) and the helpers described below.
3. **Generate.** `generateInstance()` writes the class, loads it and returns an instance. Other outputs: `generate()` returns the `Class`, `generateConstructor(paramTypes...)` a constructor,
   `generateInstance(paramTypes, args)` calls a specific constructor, and `generateInstanceNull()` creates an instance without calling any constructor.
4. Call the methods of the result through the interface or super type it implements. The generated class cannot be named in your source, which is the point of it.

### Builder Options

| Option | Effect |
|---|---|
| `addInterface(type)`, `addInterfaces(types)` | Also implement these interfaces. |
| `setSingleton(true)` | The class gets a generated private constructor and a `public static final INSTANCE` field with the one instance. Use `visitSingletonField` for per-instance data. `generateInstance()` returns the instance. |
| `setExactName(name)` | Use exactly this class name. Fails if the name is already in use. Without it a unique name is made from the super type name and a `$mpldefgen...` postfix. |
| `setPostfix(postfix)` | Replace the generated name postfix. |
| `setClassLoader(loader)` | The base class loader the generated class can see. Defaults to the one that loaded the super type. It must be able to see every type the bytecode refers to. |
| `setFlags(flags)`, `setAccess(access)` | ASM `ClassWriter` flags (for example `COMPUTE_FRAMES`), and extra class access flags. The class is `public static` by default. |
| `defer(callback)` | Instead of `build()`: returns a `Deferred`. See below. |

Super types from `java.*` or `sun.*` packages cannot have a class generated into their own package, so the generated class is placed in Mountiplex's own package instead.

## Helpers on `ExtendedClassWriter`

These write common method bodies and fields so that raw instructions are only needed for custom logic.

| Method | What it adds |
|---|---|
| `visitMethodReturnConstant(method, value)` | Implements the method to ignore its arguments and return a constant (checked against the return type; `null` for objects, a boxed value for primitives). Typical for stubbing out every method of an interface. |
| `visitMethodUnsupported(method, message)` | Implements the method to throw `UnsupportedOperationException(message)`. |
| `visitStaticField(name, type, value)` | A `public static final` field initialized with an object you hand over. Use it to give generated code access to an object that cannot be referred to by a constant. |
| `visitSingletonField(name, type, value)` | A private final member field of a singleton, initialized in the generated constructor. |
| `visitStaticInvokerField(name, methodDeclaration)` | A static [`Invoker`](fast-access.md#invokers) field set up to call the method described by a `MethodDeclaration`. |
| `visitCallSuperMethod(mv, method)` | Writes the `INVOKESPECIAL` instruction for calling the super implementation. It picks the direct super type or interface, as some JVMs reject deeper targets. |
| `static visitInvoke(mv, type, method)` | Writes the right invoke instruction (static, interface or virtual) for a method. |
| `static visitInit(mv, type, constructor)` | Writes the constructor call. |
| `static visitPush*` and `visitPush(mv, type, value)` | Pushes a constant of the given primitive or `String` type on the stack. |
| `static visitUnboxObjectVariable(mv, type)` | Casts or unboxes the object on the stack to the given type. |
| `getName()`, `getInternalName()`, `getTypeDescriptor()` | The name of the class being generated, for use in `GETFIELD`/`INVOKEVIRTUAL` on itself. |

Constructors: a singleton gets a constructor automatically. For other classes, a no-argument constructor is **not** generated: `generateInstance()` fails with `NoSuchMethodException`
unless you write an `<init>` method, use `generateInstanceNull()`, or add one through the Javassist hook (`addJavassist`, `getCtClass`, mainly used internally).

## `MPLType`

Use `MPLType` wherever bytecode needs a name or descriptor, instead of `Type.getInternalName` or `Class.getName()`. It returns the names the JVM uses, even when the library was
loaded by a class loader that remaps names (see [internals/remapping-and-javassist.md](internals/remapping-and-javassist.md) for why), and it applies the
[Resolver](resolver.md)'s compiled-name translation to members.

| Group | Methods |
|---|---|
| Names and descriptors | `getInternalName(Class)`, `getInternalNames(Class[])`, `getDescriptor(Class)`, `getMethodDescriptor(Method)` and `(returnType, paramTypes)`, `getConstructorDescriptor`, `getInternalMethodDescriptor(MethodDeclaration)`, `getName(Class/Method/Field)` |
| ASM `Type` objects | `getType(Class)`, `getTypes(Class...)`, `getReturnType(Method)`, `getArgumentTypes(Method)` |
| Opcodes | `getOpcode(Class, opcode)` adapts a load/store/return opcode to the type (for example `IRETURN` becomes `ARETURN`, `DRETURN`...) |
| Registers and boxing | `visitVarILoad`/`visitVarIStore` (load or store one or more parameters, returns the next free register), `visitVarILoadAndBox`, `visitBoxVariable`, `visitUnboxVariable` |
| Member lookup | `getDeclaredMethod`/`getDeclaredField`, with `tryGet...` (returns null) and `...CheckSuperclasses` variants |
| Class lookup | `getClassByName(name[, initialize, loader])`: also finds classes created by a `GeneratorClassLoader` |

## Deferred Generation

`builder.defer(callback)` fixes the class name right away but generates the class later: when someone first loads that exact name through the generator class loader, or when
`forceInitialization()` is called. The callback receives the writer and must return the generated instance. This is for code that must refer to the name of a generated class before that class has to exist (Mountiplex's `GeneratedCodeInvoker` does this for method bodies compiled from source). One class is generated; an error from the callback is remembered and rethrown each time.

## Gotchas

- A mistake in the instructions only shows up as a `VerifyError` or `ClassFormatError` when the class is loaded (`generate*`), not when writing. For development, add `ClassWriter.COMPUTE_FRAMES`/`COMPUTE_MAXS` via
  `setFlags`, or check the output with ASM's `CheckClassAdapter`. Handwritten `visitMaxs` values must be exact if the flag is not used.
- Types referred to from the bytecode must be visible to the class loader of the super type. A type from another plugin's loader is not.
- The generated class cannot refer to the classes of your own jar by name unless the base class loader can see them.
- Generated class names are unique per run. Do not store them. If a class with a fixed name already exists (for example after a plugin reload), `setExactName` fails, so check with
  `Resolver.getClassByExactName` first and reuse the existing class.
- Do not generate classes in a hot path. Generate once, in a `static` initializer or lazily, and keep the instance.

## Where to Look

| Task | Files |
|---|---|
| Builder, writer, helpers, `Deferred` | `reflection/util/ExtendedClassWriter.java` |
| Names, descriptors, opcodes | `reflection/util/asm/MPLType.java` |
| Class definition and loader selection | `reflection/util/GeneratorClassLoader.java`, `asm/MPLGeneratorClassLoaderBuilder.java` |
| Passing objects into generated classes | `reflection/util/GeneratorArgumentStore.java` |
| Bytecode inspection helpers | `reflection/util/asm/ASMUtil.java` |
| Real usage in BKCommonLib | `common/internal/logic/EmptyBlockGetterInit.java`, `NullPacketDataSerializerInit.java`, `UnsetDataWatcherItemInit.java` |
