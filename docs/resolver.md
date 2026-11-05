# The `Resolver` (Runtime API)

`Resolver` (`src/main/java/com/bergerkiller/mountiplex/reflection/resolver/Resolver.java`) is the global, static entry point for turning **names into
classes, fields and methods** in the running environment. It does two jobs:

1. **Lookup**: load classes by name, find declared methods and fields, inspect classes. Usable by any code, not just templates.
2. **A registry of translators** ("resolvers") that map the names written in templates and code onto what really exists, so the same name works in
   every environment (obfuscated, remapped, renamed between versions).

All of Mountiplex goes through it (templates, `FastMethod`, converters, hooks), so a name translation registered once applies everywhere. Registering
translators is shown with a full setup in [examples/project-setup.md](examples/project-setup.md#4-a-basic-remapper). This page lists the whole API.
The resolver interfaces are in the same package. This is separate from `ClassResolver`, the per-template context (see [overview.md](overview.md#template-context-classresolver)).

Results below were produced by running the code with a small `Greeter` class.

## Loading Classes

| Method | Use |
|---|---|
| `loadClass(path, initialize)` | Loads a class by name, `null` if it does not exist. `initialize` runs its static initializers. Uses the library's class loader. |
| `loadClass(path, initialize, loader)` | Same, preferring a given class loader. |
| `getClassByExactName(name)` | A previously loaded class by its exact name. Throws `ClassNotFoundException`. |
| `initializeClass(Class)` | Runs static initializers (once). Errors are logged, not thrown. |

```java
Resolver.loadClass("tmpres.Greeter", false);            // class tmpres.Greeter
Resolver.loadClass("tmpres.Nope", false);               // null (and the miss is cached)
Resolver.loadClass("tmpres.Greeter.Nested", false);     // class tmpres.Greeter$Nested   (dots for nested classes work)
Resolver.loadClass("int[]", false);                     // class [I     (arrays and primitives work too)
```

It differs from `Class.forName` in useful ways: the **registered class path resolvers are applied first** (so a name from a newer version loads the
older class, see below), nested classes can be written with dots, arrays and primitives are understood, results (including misses) are cached, and failures
return `null`. Registering a class path resolver clears that cache. Pass `false` for `initialize` unless the static initializers are needed.

## Translating Names

The `resolve*` methods run a name through the registered translators. When nothing is registered, they return the input.

| Method | Translates |
|---|---|
| `resolveClassPath(path)` | A class name into the real class name. Used by `loadClass`. |
| `resolveMethodName(declaringClass, name, paramTypes)` | A method name into the real name in that class. |
| `resolveFieldName(declaringClass, name)` | A field name into the real name. |
| `resolveClassDeclaration(...)` | A class (by `Class` or name) into its registered [`ClassDeclaration`](template-syntax.md#class-declarations), or `null`. |
| `resolveClassVariables(path, type)` | The variables (such as `version`) that apply for that class's declarations. Empty map if none. |

```java
Resolver.registerClassResolver(p -> p.equals("tmpres.OldGreeter") ? "tmpres.Greeter" : p);
Resolver.registerMethodResolver((c, n, p) -> (c == Greeter.class && n.equals("greet")) ? "hello" : n);

Resolver.resolveClassPath("tmpres.OldGreeter");                  // "tmpres.Greeter"
Resolver.loadClass("tmpres.OldGreeter", false);                  // class tmpres.Greeter
Resolver.resolveMethodName(Greeter.class, "greet", new Class[]{String.class});   // "hello"
```

### Finding Members by Resolved Name

```java
Method m = Resolver.resolveAndGetDeclaredMethod(Greeter.class, "greet", String.class);
// public java.lang.String tmpres.Greeter.hello(java.lang.String)

Field f = Resolver.resolveAndGetDeclaredField(Greeter.class, "pre");
// private java.lang.String tmpres.Greeter.prefix
```

These translate the name, then call `getDeclaredMethod`/`getDeclaredField`. They throw `NoSuchMethodException`/`NoSuchFieldException` when it does not exist
(`java.lang.NoSuchMethodException: tmpres.Greeter.nothing()`), and **do not search super classes**, like the standard calls. The member is not made accessible; pair it with
[`FastMethod`/`FastField`](fast-access.md), which do that, or call `setAccessible` yourself. This is the most common way BKCommonLib looks up members that differ per version.

### Finding Members by Declaration

For when the member should be described in template syntax, with the type information checked, rather than by name only:

| Method | Returns |
|---|---|
| `findMethod(Class, declaration)` | A discovered `MethodDeclaration`, or `null`. |
| `findMethod(MethodDeclaration)` | Discovers an existing declaration. |
| `findField(Class, declaration)` | A discovered `FieldDeclaration`, or `null`. |
| `resolveMethodAlias(TypeDeclaration, Method)` | A declaration for a real method with the alias a template gave it (looks in the declaring type and its super types). |

```java
MethodDeclaration m = Resolver.findMethod(Greeter.class, "public String greet(String who)");
m.toString();      // public String greet:hello(String who);     <- alias form: exposed name : real name
m.method;          // the java.lang.reflect.Method  public java.lang.String tmpres.Greeter.hello(java.lang.String)

FieldDeclaration f = Resolver.findField(Greeter.class, "private String pre");
f.toString();      // private String pre:prefix;
f.field;           // the java.lang.reflect.Field

Resolver.findMethod(Greeter.class, "public String missing(String who)");    // null, and logs the failure
```

Both log failures, with the alternatives, in the format of [Diagnosing Missing Members](template-syntax.md#diagnosing-missing-members). The declaration is a normal template
member declaration, so aliases (`greet:hello`), modifiers and type parameters can be used. Template declarations registered for the class are consulted first.
A `MethodDeclaration` is also the input to `FastMethod`, see [examples/inline-methods.md](examples/inline-methods.md).

## Class Information

| Method | Returns |
|---|---|
| `getMeta(Class)` | A cached `ClassMeta`: `type`, `typeDec` (the `TypeDeclaration` with generics), `superType` and `interfaces` (generic-aware), and `isPublic`. `null` input gives a placeholder. |
| `isPublic(Field)`, `isPublic(Method)`, `isPublic(Constructor)`, `isPublic(Class)` | `true` only if the member **and the class that declares it (and any enclosing classes)** are public. |

```java
Resolver.isPublic(Greeter.class.getDeclaredField("prefix"));           // false  (private)
Resolver.isPublic(Greeter.class.getMethod("hello", String.class));     // true
Resolver.getMeta(Greeter.class).isPublic;                              // true
Resolver.getMeta(Class.forName("tmpres.Greeter$Hidden")).isPublic;     // false (private nested class)
Resolver.getMeta(ArrayList.class).superType;                           // AbstractList<E>
Resolver.getMeta(ArrayList.class).interfaces;                          // [List<E>, RandomAccess, Cloneable, java.io.Serializable]
```

A `public` modifier on a member alone is not enough: if the declaring class (or an enclosing class) is package-private or private, code outside its package cannot reach the member, so `isPublic` returns `false` even for a `public` method or field. In other words it answers "is this member accessible from anywhere", not just "does it carry the `public` keyword". `ClassMeta.isPublic` applies the same check to a class and its enclosing classes.

`isPublic` is what decides whether Mountiplex can generate direct-call code or must use reflection ([fast-access.md](fast-access.md)), and whether a type is treated as
`Object` in handles ([Private and Inaccessible Types](template-syntax.md#private-and-inaccessible-types)).

## Registering Translators

Each `register*` call **adds to a chain** and applies from then on. Chains cannot be modified or removed afterwards (a library unload resets them). Register
before anything is loaded: see the ordering warning in [project-setup.md](examples/project-setup.md#5-version-gated-class-name-remappings).

| Register | Interface | Called to |
|---|---|---|
| `registerClassResolver` | `ClassPathResolver` | Translate class names. Optional `canLoadClassPath(path)` returns `false` to stop reading `.class` data for runtime-compiled code (see the [internals page](internals/remapping-and-javassist.md)). Clears the class cache. |
| `registerMethodResolver` | `MethodNameResolver` | Translate method names. |
| `registerFieldResolver` | `FieldNameResolver` | Translate field names. |
| `registerMethodAliasResolver`, `registerFieldAliasResolver` | `MethodAliasResolver`, `FieldAliasResolver` | The reverse: given a real member, supply its readable alias name (for the `alias:real` form in logs and declarations). |
| `registerCompiledMethodResolver`, `registerCompiledFieldResolver` | `CompiledMethodNameResolver`, `CompiledFieldNameResolver` | Correct names for the code being compiled at runtime, when reflection reports names that differ from the ones the JVM uses. |
| `registerClassDeclarationResolver` | `ClassDeclarationResolver` | Supply the template `ClassDeclaration` for a class, and the variables for it. See the resolver in [project-setup.md](examples/project-setup.md#3-the-resolver). |

How several registered resolvers combine (`ChainResolver`):

- **Class path and name resolvers** run in registration order and each receives the **output of the previous one**. Every resolver must return its input when it does not handle the name.
- **Alias resolvers** run **newest first**; the first one that returns non-`null` wins. With none, the member's real name is its alias.
- **Class declaration resolvers** run in registration order; the first non-`null` declaration wins, and the **variables of all of them are merged**.

The environment-wide switch `setClassLoaderRemappingEnabled(boolean)` / `isClassLoaderRemappingEnabled()` tells the library's class loader to remap generated code (see
the [internals page](internals/remapping-and-javassist.md)). `getPackageNameCache()` returns the cache used to reject names that are known packages.

### Example: Mojang Mappings in BKCommonLib

[BKCommonLib](https://github.com/bergerhealer/BKCommonLib) contains a full-scale implementation of these resolvers in its `common/internal/cdn` package. `MojangMappings` downloads and parses the
server mappings file published by Mojang, `SpigotMappings` covers the Spigot side, and `MojangRemapper` combines the two to translate mojang-mapped class, field and method names to the
obfuscated names present at runtime. It is registered as a class, field and method resolver in `common/internal/CommonBootstrap.java`. It shows how the chain stays practical for very large
name tables: lookups are keyed by declaring class and the tables are built once and reused. Read it only as a reference for what a resolver can do; this library does not depend on it.

## Gotchas

- Name translation is by **declaring class**: `resolveMethodName` receives the class that declares the method. A resolver that compares `declaringClass` must be written for that class (a sub class asking does not match).
- A resolver that does not return its input for unknown names breaks every other lookup, since the result is chained.
- `loadClass` caches failures. A class that was missing at first lookup stays missing until a class resolver is registered (which clears the cache).
- A translator is applied again on every lookup, so it should be cheap: build lookup maps once, like the version-gated table in the project setup example.
- `findMethod`/`findField` log at warning level when members are missing. Call them for members that should exist, not to probe.

## Where to Look

| Task | Files |
|---|---|
| Static API and `ClassMeta` | `reflection/resolver/Resolver.java` |
| Resolver interfaces | `ClassPathResolver`, `MethodNameResolver`, `FieldNameResolver`, `*AliasResolver`, `Compiled*NameResolver`, `ClassDeclarationResolver` |
| Chaining rules | `reflection/resolver/ChainResolver.java`, `NoOpResolver.java` |
| Declaration discovery behind `findMethod`/`findField` | `reflection/declarations/MethodDeclaration.java` (`discover`), `FieldDeclaration.java` |
