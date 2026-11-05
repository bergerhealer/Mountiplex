# Utility Classes

Small, general-purpose helpers that other pages refer to and that projects using Mountiplex import often. None of them is part of the template or conversion systems; each stands alone.
Types for specific systems have their own pages: [fast-access.md](fast-access.md), [type-maps.md](type-maps.md), [text-value-sequence.md](text-value-sequence.md), [class-generation.md](class-generation.md).

| Class | Package | Use it for |
|---|---|---|
| `MountiplexUtil` | `com.bergerkiller.mountiplex` | Logger, exception rethrowing, unload hooks, parsing, array and stream helpers |
| `ReflectionUtil` | `...reflection` | Streams over class hierarchies, methods and fields; type name rendering |
| `BoxedType` | `...reflection.util` | Mapping between primitive and boxed types, primitive defaults |
| `NullInstantiator` | `...reflection.util` | Creating instances without calling a constructor |
| `LambdaBuilder` | `...reflection.util` | Implementing a functional interface from an `Invoker` or constant |
| `UniqueHash` | `...reflection.util` | Thread-safe unique, random-looking numbers for generated names |

## `MountiplexUtil`

`src/main/java/com/bergerkiller/mountiplex/MountiplexUtil.java`. All members are static.

| Member | Behavior |
|---|---|
| `LOGGER` | The `java.util.logging.Logger` named `REFLECTION`, used by all of Mountiplex. Replaceable (it is a non-final static), so a host can route messages into its own logging. |
| `uncheckedRethrow(Throwable)` | For use as `throw MountiplexUtil.uncheckedRethrow(t)`. Unwraps an `InvocationTargetException`, rethrows `Error`s directly, returns `RuntimeException`s as they are, and wraps anything else in a `RuntimeException` with an empty stack trace (the cause carries the real one). Use it in lambdas and generated or reflective code where checked exceptions cannot be declared. |
| `registerUnloader(Runnable)` | Registers cleanup to run on `unloadMountiplex()`. |
| `unloadMountiplex()` | Runs every registered unloader once and clears them. Call it when the plugin or module that bundles Mountiplex is disabled, so the static state registered for cleanup (such as the Resolver's registered translators) is released and a reload starts clean. |
| `toStream(value)` | A one-element `Stream`. Exists because `Stream.of` does not work for this on Java 9+. |
| `iterateNullTerminated(seed, f)` | A stream of `seed`, `f(seed)`, `f(f(seed))`... until a `null` appears (an empty stream for a `null` seed). Used to walk super classes and declaring classes. |
| `parseEnum(text, default)`, `parseEnum(enumClass, text, default)`, `parseArray(values, text, default)` | Forgiving name lookup: ignores case, spaces and underscores, then falls back to a partial match in either direction. Returns the default if nothing matches. The default may not be `null` for the overload that takes only text, as it supplies the enum type. |
| `getClassConstants(class[, type])` | The constants of a class: enum values for an enum, otherwise the static fields of the given type. |
| `getArrayType`, `createArray`, `getTypes`, `toArray`, `retainAll`, `containsChar`, `getLastBefore`, `getPackagePathFromClassPath`, `createUmodifiableList`, `similarity`, `editDistance` | Small array, collection and string helpers. `getLastBefore(text, delimiter)` returns the text before the last delimiter, or `""`. `similarity` is a value from 0.0 to 1.0, `editDistance` the Levenshtein distance. |

The unload hook is the one to remember: it is how static state is released when the library is unloaded. Register one for any static cache you add that holds `Class` objects.

## `ReflectionUtil`

`src/main/java/com/bergerkiller/mountiplex/reflection/ReflectionUtil.java`. Static helpers that return streams.

| Method | Returns |
|---|---|
| `getAllClasses(type)` | The type and all its super classes (empty for `null`). |
| `getAllClassesAndInterfaces(type)` | The above, followed by every interface they implement, including interfaces of interfaces, without duplicates. |
| `getAllDeclaringClasses(type)` | The type and the classes it is nested in, innermost first. |
| `getDeclaredMethods(type)`, `getMethods(type)` | The declared or public methods, sorted so that a covariant override comes before the synthetic bridge it replaces. |
| `getAllMethods(type)` | Methods of the type, its super classes and all interfaces, with duplicate signatures removed (an overriding method hides the one it overrides). Static methods are always kept. |
| `getAllFields(type)`, `getAllNonStaticFields(type)`, `getAllStaticFields(type)` | Fields declared in the type and its super classes. |
| `getTypeName`, `getAccessibleTypeName`, `getAccessibleTypeCast`, `getCastString`, `stringifyType`, `stringifyMethodSignature` | Source-style names of types and methods (primitives and boxed types by simple name, arrays as `Type[]`). The *accessible* variants return `Object` (or no cast) for types that are not public, which is what [generated handle code](template-syntax.md#private-and-inaccessible-types) uses, and prefix a name with `$mpl` when a resolver would otherwise translate it a second time. |
| `parseModifiers`, `compareModifiers`, `filterGenerics` | Helpers used when parsing declarations. `filterGenerics` strips generic arguments from a declaration string. |
| `findMethodClass(method)`, `recurseFindAnnotationValue(...)`, `fixMethodInvokeException(...)` | Locating the class a method originates from, reading an annotation value up the hierarchy, and turning invocation errors into readable exceptions. |

## `BoxedType`

`reflection/util/BoxedType.java`. Maps between the eight primitives (and `void`) and their wrappers.

- `getBoxedType(int.class)` is `Integer.class`, or `null` if the argument is not a primitive. `tryBoxType` returns the argument itself instead of `null`, which is usually what is wanted.
- `getUnboxedType(Integer.class)` is `int.class`, or `null` if it is not a wrapper. `getUnboxedType("int")` finds a primitive by name.
- `getDefaultValue(int.class)` is `0`. It is `null` for any non-primitive type, and for `void`.
- `getBoxedTypes()` and `getUnboxedTypes()` list all of them.

Use it when generating code or comparing types, so that `int` and `Integer` are treated the same.

## `NullInstantiator`

`reflection/util/NullInstantiator.java`. Creates an instance of a class **without calling any constructor** (through Objenesis). All fields are `null`, `0` or `false`, and no initializers run.

- `NullInstantiator.of(type)` is cheap and lazy. Keep it in a `static final` field and call `create()` for each instance. `create()` is thread-safe and the underlying instantiator is looked up once.
- It fails with `IllegalStateException` if the class cannot be instantiated. Checked exceptions from the creation are rethrown unchecked.
- Typical uses: building a stand-in object of a server class that only needs to be a valid reference, instantiating a class whose constructor is expensive or has side effects, and instances of generated classes
  (`ExtendedClassWriter.generateInstanceNull()` uses it).
- The object is incomplete: a method that depends on constructor work will fail or misbehave. Only use it where that is known to be safe.

## `LambdaBuilder`

`reflection/util/LambdaBuilder.java`. Implements a functional interface (exactly one abstract method) when the implementation is an `Invoker` or a constant, without writing a class.

- `LambdaBuilder.of(Interface.class)` returns a cached builder. It throws `IllegalArgumentException` if the type does not have exactly one abstract method, or, for a class, has no accessible empty constructor.
- `create(invoker)` returns an instance whose single method is forwarded to the [`Invoker`](fast-access.md#invokers). `createConstant(value)` returns one that always returns that value.
- Used to produce an implementation of a version-specific functional interface at runtime, for example to hand a callback to code you cannot reference in Java. It is built on [`ClassInterceptor`](classhook.md), so each created
  instance is a generated subclass, not a JVM lambda.

## `UniqueHash`

`reflection/util/UniqueHash.java`. `next()` returns the next number of a sequence that looks random but never repeats (`nextHex()` as a hex string). It is thread-safe and used for generated
class names (`$mplgen...`, `$mpldefgen...`) and static field names. The static `hash(int)` is the one-to-one scrambling function behind it.

## Where to Look

| Task | Files |
|---|---|
| General helpers and unload hooks | `MountiplexUtil.java` |
| Hierarchy and type name helpers | `reflection/ReflectionUtil.java` |
| Primitive and boxed types | `reflection/util/BoxedType.java` |
| Constructor-less instances | `reflection/util/NullInstantiator.java` |
| Functional interface implementations | `reflection/util/LambdaBuilder.java` |
| Name hashing | `reflection/util/UniqueHash.java` |
