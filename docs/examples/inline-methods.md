# Runtime-Generated Methods Without Handles (Example)

How to compile and call a piece of code at runtime from a plain Java `String`, using `MethodDeclaration`, `ClassResolver`
and [`FastMethod`](../fast-access.md), **outside of the template and `*Handle` flow**. It is the quickest way to get version-specific code
executed: no template file, no Gradle generation step, no handle class. [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)
uses this pattern in many places (for example `BlockDataSerializer_1_13_to_1_18_2`, `PlayerFileDataHandler_1_21_6`,
`SoundEffectParser`, `MaterialDataToIBlockData`) for small, self-contained pieces of version-dependent logic.

The code on this page was run against Mountiplex (static and instance bodies, `#if` on a variable, `#require`, a missing member, and a
body that fails to compile).

## When to Use It

- A single method body that differs per version, called from one place, where a full template and handle would be overkill.
- Code that must touch non-public or version-specific types, referenced by full name from the string, without compile-time access.
- Anything that needs the [template method body](../template-syntax.md#method-bodies) features (`instance`, `#require`, `#if`)
  but lives in ordinary Java code.

For members that many classes use, or that need a type-safe exposed API, use templates and handles instead
([template-syntax.md](../template-syntax.md), [project-setup.md](project-setup.md)).

## The Recipe

1. Create a `ClassResolver` for the context of the code (declared class, imports, variables).
2. Write the method as a Java source string, with `#if`/`#require` macros as needed.
3. Run it through `SourceDeclaration.preprocess(source, resolver)` to evaluate the `#if` macros.
4. Parse it with `new MethodDeclaration(resolver, source)`.
5. Wrap it in a `FastMethod`, call `forceInitialization()` to compile it early, and call `invoke`.

```java
ClassResolver resolver = new ClassResolver();
resolver.setDeclaredClassName("net.minecraft.commands.synchronization.SuggestionProviders");   // context class
resolver.addImport("com.mojang.brigadier.suggestion.SuggestionProvider");                      // short names usable in the body
resolver.setAllVariables(Common.TEMPLATE_RESOLVER);                                            // supplies 'version' etc.

FastMethod<Object> getSuggestions = new FastMethod<Object>(
        new MethodDeclaration(resolver, SourceDeclaration.preprocess("" +
                "public static SuggestionProvider getSoundSuggestions() {\n" +
                "#if version >= 1.17\n" +
                "    return SuggestionProviders.AVAILABLE_SOUNDS;\n" +
                "#else\n" +
                "    return SuggestionProviders.c;\n" +
                "#endif\n" +
                "}", resolver)));

Object provider = getSuggestions.invoke(null);          // static: instance argument is null
```

This is adapted from BKCommonLib's `SoundEffectParser`. The pieces are explained below.

**Call `forceInitialization()` after creating the `FastMethod`.** The body is compiled lazily, so without it a compile error
only surfaces at the first `invoke`, possibly much later and in the middle of unrelated code. Forcing the initialization compiles
the body right away, so mistakes are caught early, at startup (BKCommonLib does this in `PlayerFileDataHandler_1_21_6` and
`SoundEffectParser`):

```java
getSuggestions.forceInitialization();      // compile now; a broken body fails here, not at the first call
```


## The `ClassResolver` Context

| Call | Effect |
|---|---|
| `setDeclaredClass(Class)` / `setDeclaredClassName(String)` | The class the method "belongs to". Non-static bodies see its objects as `instance`. Names in the body resolve relative to it, and its members can be reached through `#require`. |
| `addImport("a.b.Name")` | Short name usable in the body and in the signature. |
| `setPackage("a.b")` | Package used to resolve short names. |
| `setVariable("version", "1.20")` | A variable for `#if`/`#select` in the source string. Version-like values compare version-aware. |
| `setAllVariables(...)` | Copies all variables from a map or a `ClassDeclarationResolver` (BKCommonLib passes its template resolver, so the version variables match the templates). |

Variables are only needed when the source string has macros. Without macros, the resolver can be a bare `new ClassResolver()`
with a declared class. See [ClassResolver](../overview.md#template-context-classresolver) and
[Conditions](../template-syntax.md#conditions-expressions).

## Examples

All of these use a target class like:

```java
package tmpex;
public class Greeter {
    private final String name;
    public Greeter(String name) { this.name = name; }
    public String a() { return "a:" + name; }
    private int secret() { return 42; }
}
```

with `resolver.setDeclaredClass(Greeter.class)`, `resolver.setVariable("version", "1.20")`.

**Static method with a version-gated body**

```java
FastMethod<String> describe = new FastMethod<String>(new MethodDeclaration(resolver, SourceDeclaration.preprocess("" +
        "public static String describe(int value) {\n" +
        "#if version >= 1.19\n" +
        "    return \"new:\" + value;\n" +
        "#else\n" +
        "    return \"old:\" + value;\n" +
        "#endif\n" +
        "}", resolver)));

describe.invoke(null, 5);                                  // "new:5"
```

**Instance method, with `instance` and `#require`.** Non-public members are reached through `#require`, exactly as in
[template method bodies](../template-syntax.md#requirement-syntax-name); the first argument of `invoke` is the instance:

```java
FastMethod<String> shout = new FastMethod<String>(new MethodDeclaration(resolver, SourceDeclaration.preprocess("" +
        "public String shout(String suffix) {\n" +
        "    #require tmpex.Greeter private String name;\n" +
        "    #require tmpex.Greeter private int secret();\n" +
        "    String n = instance#name;\n" +
        "    int s = instance#secret();\n" +
        "    return n.toUpperCase() + suffix + s;\n" +
        "}", resolver)));

shout.invoke(new Greeter("bob"), "!");                     // "BOB!42"
```

**Declaring an existing method instead of writing a body.** A declaration without a body is not matched against the real class
by itself; call `discover()` on it, which returns the matched declaration or `null`. A `null` makes the `FastMethod` unavailable:

```java
FastMethod<String> real = new FastMethod<String>(new MethodDeclaration(resolver, "public String a();").discover());
real.invoke(new Greeter("x"));                             // "a:x"
```

**Variables as part of the string.** Besides `#if`, plain Java string building works for values only known at runtime
(`"... return " + someConstant + ";"`), but prefer `#if` and resolver variables when a version decides the content: it keeps the
code readable and reuses the same conditions as templates. Remember that conditions run without imports, so class names in
`exists`/`assignable` conditions inside the string must be fully qualified.

## `FastMethod` Behaviour

| Aspect | Behaviour |
|---|---|
| Construction | `new FastMethod<T>(MethodDeclaration)`, or an empty `new FastMethod<T>()` assigned later with `init(declaration)`. The empty form is the usual choice for `static` fields that are filled in during startup. |
| Compilation | Lazy. Parsing the declaration does not compile the body: that happens on `forceInitialization()` or the first `invoke`. Call `forceInitialization()` during startup to fail early rather than on first use. |
| Calling | `invoke(instance, args...)` for up to five arguments (`invokeVA` for more). Static methods take `null` as instance. The return type is the generic `T` of `FastMethod<T>`; arguments are passed as `Object`. |
| Compile errors | The body is compiled with Javassist. A failing body logs a `SEVERE` message with the source and the cause (for example `doesNotExist() not found in ...`) and throws `RuntimeException("An exception occurred")` on `forceInitialization()` and every `invoke`. Check the log for the actual cause. |
| Unavailable | A `null` or unmatched declaration gives an unavailable method: `isAvailable()` is `false` and invoking throws `UnsupportedOperationException: method ... is not available`. `initUnavailable(info)` marks one explicitly. |
| Performance | After first use the call goes through generated code, with no reflection per call. Keep the `FastMethod` in a static or instance field and reuse it; do not create one per call. |

## Gotchas

- **Call `preprocess` yourself.** BKCommonLib always passes the source through `SourceDeclaration.preprocess(source, resolver)` before
  constructing the `MethodDeclaration`, with the same resolver, so that `#if`/`#select` lines are evaluated and only the kept lines are parsed. Do the same.
- The limited Java subset of method bodies applies (compiled with Javassist: avoid generics, lambdas and enhanced `for` loops; use `Outer$Inner`
  for nested classes). See [Method Bodies](../template-syntax.md#method-bodies) and the fully-qualified-name advice in
  [Imports and Type Names](../template-syntax.md#imports-and-type-names).
- Errors only show up when the body is compiled (first use), and only for the version being run, so test every supported version
  or call `forceInitialization()` on startup.
- This is not type-safe at compile time: the string is only checked when it runs.

## Source Files

| File | Role |
|---|---|
| `reflection/declarations/MethodDeclaration.java` | Parses the method source (signature, body) and finds real methods with `discover()`. |
| `reflection/declarations/ClassResolver.java` | Context for resolving names, imports and variables. |
| `reflection/declarations/SourceDeclaration.java` | `preprocess(source, resolver)` evaluates the `#if` macros. |
| `reflection/util/FastMethod.java` | Lazily initialized, fast invoker wrapper around a `MethodDeclaration`. |
| `reflection/util/fast/GeneratedCodeInvoker.java` | Compiles and runs generated bodies. |
