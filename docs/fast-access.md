# Fast Member Access (`FastMethod`, `FastField`, `FastConstructor`, `Invoker`)

Wrappers for calling methods, reading and writing fields, and constructing objects, as an alternative to `java.lang.reflect`. They are
what Mountiplex uses everywhere internally (template elements, converters, hooks) and what projects using it should prefer for their own
reflection. Source: `src/main/java/com/bergerkiller/mountiplex/reflection/util/` (`FastMethod`, `FastField`, `FastConstructor`,
`FastConvertedField`, `LazyInitializedObject`) and `.../util/fast/` (`Invoker` and the implementations).

The older `SafeField`, `SafeDirectField` and `SafeMethod` wrappers are kept for backwards compatibility only. New code should use the
classes on this page, and the Safe variants are intentionally not documented.

## Why Use Them

1. **Lazy, exception-free initialization.** A `FastMethod`/`FastField`/`FastConstructor` can be created empty, as a `static final` field, and
   filled in later with `init(...)`, or marked missing with `initUnavailable(info)`. There is no try-catch around declarations and no
   half-initialized state: one object exists from the start, and using it before it was set up, or after it was marked unavailable, throws a clear exception.
2. **No `java.lang.reflect` overhead for public members.** For a public method, constructor or field, Mountiplex generates a small class that
   calls the member directly, so a call costs about the same as normal Java. Non-public members fall back to reflection with `setAccessible`
   done once. The caller does not choose; it is picked on first use.
3. **Compiled method bodies.** `FastMethod` can run a method body given as source text at runtime, see
   [examples/inline-methods.md](examples/inline-methods.md).

## Initialization and Laziness

All of the types are *lazy*: creating one does no work. The expensive part (resolving the member, generating the accessor class) happens on the
first call, once, thread-safe, and the optimized invoker is swapped in. Implementation of `LazyInitializedObject`
(`forceInitialization()`) lets you do that work early.

| Call | Effect |
|---|---|
| `new FastMethod<>()` | Empty and unavailable until `init`. |
| `new FastMethod<>(Method)`, `(Constructor)`, `(MethodDeclaration)` | Bound to a member. Not yet generated. |
| `new FastMethod<>(DelayedInitializer)` | A callback that runs on first use (or `isAvailable()`/`forceInitialization()`) and calls `init` itself. Errors in it are logged and the method becomes unavailable. |
| `init(...)` | Binds (or re-binds) to a member. `init(null)` makes it unavailable. |
| `initUnavailable(String info)` | Marks missing. Using it throws `UnsupportedOperationException: ... <info> ... is not available`. |
| `isAvailable()` | Whether it is bound to a member (`FastMethod` runs a delayed initializer first). |
| `forceInitialization()` | Generates the invoker now. Throws if unavailable or if generation fails. Call it at startup to find problems early rather than at the first real call. |

```java
private static final FastMethod<String> GREET = new FastMethod<>();       // static final, filled in later
private static final FastField<Integer> COUNT = new FastField<>();

static void init() {
    GREET.init(Resolver.resolveAndGetDeclaredMethod(Target.class, "greet", String.class, int.class));
    COUNT.init(Resolver.resolveAndGetDeclaredField(Target.class, "count"));
    GREET.forceInitialization();                       // optional: fail now, not at the first call
}
```

When a member may not exist in the running environment, mark it instead of leaving it empty, so the later error says what is missing.
This is the pattern BKCommonLib uses (resolving a member per server version):

```java
try {
    GREET.init(Resolver.resolveAndGetDeclaredMethod(Target.class, methodNameForThisVersion, String.class, int.class));
} catch (Throwable t) {
    GREET.initUnavailable("Target greet() is not available");        // later use: "...Target greet() is not available is not available"
}
```

`Resolver.resolveAndGetDeclaredMethod` / `resolveAndGetDeclaredField` apply the registered name remappers (see [overview](overview.md)),
so the same call works across environments. Plain `Class.getDeclaredMethod` works too.

## Usage

Results below are from running the code.

```java
FastMethod<String> greet = new FastMethod<>(Target.class.getDeclaredMethod("greet", String.class, int.class));   // a private method
greet.invoke(target, "ann", 2);              // "hi ann x2 from bob"    instance first, then up to 5 arguments

FastMethod<Integer> twice = new FastMethod<>(Target.class.getDeclaredMethod("twice", int.class));
twice.invoke(null, 21);                      // 42                       static: instance is null

FastField<Integer> count = new FastField<>(Target.class.getDeclaredField("count"));    // private: made accessible once
count.setInteger(target, 9);
count.get(target);                           // 9      (boxed)   also getInteger, getDouble, getBoolean, ... for primitives
count.getType();                             // int

FastField<String> name = new FastField<>(Target.class.getDeclaredField("name"));
name.copy(from, to);                         // copies the field value between two instances of the same class

FastConstructor<Target> ctor = new FastConstructor<>(Target.class.getConstructor(String.class));
ctor.newInstance("neo");
```

- **`invoke(instance, args...)`**: overloads for 0 to 5 arguments avoid array allocation. `invokeVA(instance, Object... args)` takes any number.
  Static methods and constructors take `null` as the instance.
- **`FastField`**: `get`/`set`, typed primitive variants (`getInteger`, `setInteger`, ... avoid boxing), `copy(from, to)` and `getType()`, `isStatic()`.
  Static fields use a `null` instance. Final fields can be written (static finals included).
- **`FastConstructor`**: `newInstance(...)`, up to 5 arguments. A public constructor with at most 5 parameters gets generated code, others use reflection, like the invokers below.
- **Introspection**: `isAvailable()`, `getMethod()`/`getField()`/`getConstructor()` (the underlying `java.lang.reflect` object, `null` if
  unavailable or the method is generated code), `getName()`.
- **`FastConvertedField<T>`**: a `FastField` combined with a `DuplexConverter`, reading and writing converted values (see [conversion.md](conversion.md)).
  Template `Field.Converted` elements do the same.

### Errors

Exceptions are descriptive, and mismatches are checked for you:

| Mistake | Exception |
|---|---|
| Used while empty (never `init`ed) | `UnsupportedOperationException: method !!UNKNOWN!! is not available` (field: `Field !!UNKNOWN!! is not available`) |
| Marked with `initUnavailable(info)` | `UnsupportedOperationException: method <info> is not available` |
| Wrong number of arguments | `InvalidArgumentCountException: Invalid amount of arguments for method (1 given, 2 expected)` |
| `null` instance for an instance member | `IllegalArgumentException: Instance can not be null for member ... declared in <class>` (the text says "fields" even for methods) |
| Instance of the wrong class | `IllegalArgumentException: Instance of type X does not contain the field declared in Y` |
| Exceptions thrown by the target method | Unchecked ones are rethrown as they are (not wrapped in `InvocationTargetException`). Checked ones through the reflection path are wrapped in `UnhandledInvokerCheckedException`. |

## Invokers

`FastMethod` and `FastConstructor` delegate to an `Invoker<T>` (`util/fast/Invoker.java`): the common interface for "call something with an
instance and arguments". It is also useful directly: it has the same `invoke(instance, args...)` / `invokeVA` methods, and a default
`initializeInvoker()` / `forceInitialization()`. `FastMethod.getInvoker()` returns the current one.

An invoker is a lambda-friendly type (`invokeVA` is its one abstract method), so hand-written ones are short: `(instance, args) -> ...`.

| Implementation | Used when |
|---|---|
| `GeneratedInvoker` | Public method or constructor with at most 5 parameters: bytecode that calls it directly. |
| `ReflectionInvoker` | Everything else: `Method.invoke` after `setAccessible`. |
| `GeneratedCodeInvoker` / `InitGeneratedCodeInvoker` | A method declaration with a body: compiled source text. |
| `InitInvoker` | Lazy wrapper that creates the real invoker on first call and replaces itself. The unavailable variants live here (`unavailable(type, info)`). |
| `ConstantReturningInvoker` | Always returns one value, see below. |

Choosing generated or reflection invokers is automatic and depends only on the member: public with 5 or fewer parameters gets the generated one.
To keep calls fast, declare hot members public (templates can declare them less accessible than they are: see
[Modifiers](template-syntax.md#modifiers)) or keep the parameter count low.

### `ConstantReturningInvoker`

`ConstantReturningInvoker.of(value)` is an invoker that ignores its arguments and returns `value`. `nullOfType(Class)` returns `null` or the default
primitive (`0`, `false`). It matters most for [class hooks and interceptors](classhook.md): `ClassInterceptor.getCallback(...)` returns the `Invoker`
to run for an intercepted method, and when it is a `ConstantReturningInvoker` (with global callbacks), Mountiplex generates the
hooked method to return the value directly. It skips the whole bookkeeping that call tracking for `base.method()` super calls
needs, since a constant never calls super. Use it for intercepted methods that just return a fixed value:

```java
new ClassInterceptor() {
    @Override
    protected Invoker<?> getCallback(Method method) {
        if (method.getName().equals("getHandle")) {
            return ConstantReturningInvoker.of(fixedHandle);        // fast path, no per-call overhead
        }
        return (instance, args) -> compute(method, args);          // general callback
    }
}.createInstance(SomeClass.class);
```

It is also used by `LambdaBuilder.createConstant`, which implements a single-method interface returning a constant.
(BKCommonLib uses it in its block-state conversion to fake `getHandle()` on proxy worlds.)

## Where to Look

| Task | Files |
|---|---|
| Public API | `reflection/util/FastMethod.java`, `FastField.java`, `FastConstructor.java`, `FastConvertedField.java` |
| Invoker types and choosing | `reflection/util/fast/InitInvoker.java` (`forMethod` decides body, method, constructor or record field changer), `GeneratedInvoker.java` (`canCreate`), `ReflectionInvoker.java` |
| Field access | `reflection/util/fast/ReflectionAccessor.java` (`create`), `GeneratedAccessor.java`, interfaces `Reader`, `Writer`, `Copier` |
| Constant fast path | `reflection/util/fast/ConstantReturningInvoker.java`, `reflection/ClassInterceptor.java` |
