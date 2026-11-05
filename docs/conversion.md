# Type Conversion

Mountiplex translates values between types at runtime through a registry of **converters** (package
`com.bergerkiller.mountiplex.conversion`, entry point `Conversion`). Template [type casts](template-syntax.md#type-casts-exposed-real)
use it to turn internal values into stable API types and back. It can also be used directly. A worked example is in
[examples/custom-converter.md](examples/custom-converter.md).

## Principles

- A **converter** converts one input type to one output type. It is found by type, not by name: callers ask the registry for
  "input type A to output type B" and get back a ready-to-use object.
- Types are `TypeDeclaration`s, so the system is **generics-aware**: `List<Integer>` and `List<Entity>` are different types.
- Converters are **registered once, globally**, and found later by a search. Nobody wires converters together by hand.
- Conversion is **best effort**: a failed conversion returns `null` (or the default value for a primitive output) and does not throw.
- A **duplex** converter converts in both directions. Field casts in templates need one (a field is both read and written),
  method return values and parameters each need one direction.

## Converter Basics

`Converter<I, O>` (`conversion/Converter.java`, abstract) holds the `input` and `output` `TypeDeclaration`s and implements
`java.util.function.Function<Object, O>`.

| Member | Meaning |
|---|---|
| `convert(Object)`, `apply(Object)` | Convert. Returns `null` when the value is `null` (unless `acceptsNullInput()`), is not an instance of the input type, or the conversion fails. A primitive output type returns its default value (`0`, `false`) instead of `null`. |
| `convert(Object, O default)` | Same, but returns the default on failure. |
| `convertInput(I)` | The abstract method to implement. Receives a non-null, correctly typed value. |
| `acceptsNullInput()` | `false` by default. Return `true` to receive `null` in `convertInput`. |
| `getCost()` | Priority, see [Cost](#cost). |
| `isLazy()` | Marks a converter as a last resort. A lazy converter costs 100. |

`DuplexConverter<A, B>` (`conversion/type/`) adds `convertOutput(B)` for the reverse direction, `convertReverse(Object)` (the
null-safe wrapper) and `reverse()`. Registering a duplex converter registers both directions.

Other converter kinds in `conversion/type/` are mostly internal building blocks: `ChainConverter` (several converters run as
one), `NullConverter` (no-op, used when the input is already assignable to the output), `CastingConverter` (up-cast checked at
runtime), `InputConverter` (builds a converter once the actual input type is known, used by the generic converters), `LazyConverter`
(initialized on first use), `AnnotatedConverter` (wraps a `@ConverterMethod`), `FailingConverter` and `DisabledConverter`.

## Registering Converters

Ways to register, all on `Conversion`:

| Way | When |
|---|---|
| `registerConverters(Class)` | Preferred. Scans the class for static methods annotated `@ConverterMethod` and `@ProviderMethod`, and for static fields holding a `Converter` or `ConverterProvider`. |
| `registerConverter(Converter)` | A hand-written `Converter` or `DuplexConverter` subclass. Replaces an existing converter with the same input and output. |
| `registerProvider(ConverterProvider)` | One object that supplies converters for many output types, see [Providers](#providers-and-generics). |
| `@ConverterMethod` with type variables | Registers a provider automatically when the types contain type variables (`T extends Foo`). |

`@ConverterMethod` goes on a `static` method with **one parameter and a non-void return type**. The input and output
types come from the signature. The `input` and `output` attributes exist because often one of the two types is an internal implementation type that cannot be named
in Java source (the method then uses `Object` for it). They are an optional fallback for that common case: a type declaration string
that replaces the type taken from the signature. Typically only the internal side is given, the other comes from the signature:

| Attribute | Meaning |
|---|---|
| `input` / `output` | Type declaration as text, fully qualified, generics allowed (`"java.util.List<net.minecraft.world.entity.Entity>"`). Replaces the type taken from the signature. |
| `acceptsNull` | The method is called for `null` inputs too. |
| `optional` | Do not log an error when a type in the declaration does not exist at runtime. The converter is skipped. Use for types only present in some environments. |
| `cost` | See [Cost](#cost). |

A converter with a missing or unresolved input/output type is logged as a warning (`Converter has unresolved input: ...`) and skipped. Registration does not throw.

**Register converters early**: before templates that use them are initialized, and after the name resolvers they depend on are set
up. See the ordering warning in [examples/project-setup.md](examples/project-setup.md#5-version-gated-class-name-remappings).
Registering a converter later is allowed. Cached search results for affected types are reset.

### Keep Converters Free of Template Logic

Converters should ideally **not use templates**. Initializing a template handle can itself require a converter (any member with a
`(Exposed) Real` cast looks one up), so a converter whose class initialization touches a handle can end up waiting on the converter
it is part of. This causes hard-to-diagnose cyclical initialization problems.

- Keep the class that declares the converters **pure**: no `Template` handle (`XHandle.T`, `createHandle`, ...) in its static
  initializer or static fields, and no template-backed lookups while registering.
- Using a handle **inside** the conversion method body (for example calling a static method through a template) can be acceptable,
  because it only runs when a value is converted, after initialization. It is still risky: the first conversion initializes that handle,
  which may in turn look up converters. Prefer plain reflection or direct code where possible.
- If a template is unavoidable, do the template access lazily (at first conversion, not during class initialization or registration).

### Handle Converters Are Registered Automatically

Every `Template.Class` registers a **duplex converter between its internal (instance) type and its handle type** when it
initializes (`Template.java`, `getHandleConverter()` returns it). A cast like `(CrashReportHandle) CrashReport` therefore needs no
converter written by hand: it is the one registered by `CrashReportHandle.T`. If the template defines a custom `createHandle(Object)`
method, that is called to create the handle instead of the default. The custom method can return a different handle subclass, see [Custom `createHandle`](template-syntax.md#custom-createhandle). Because this happens when the handle class first initializes, the handle
must have been initialized (or be initializable by the registry) before the cast is resolved. `Conversion` does this for handle types it is asked to convert to or from.

## Finding Converters

```java
Converter<Integer, Temperature> c = Conversion.find(int.class, Temperature.class);
InputConverter<Temperature> any    = Conversion.find(Temperature.class);              // input decided per call
DuplexConverter<Integer, Temperature> d = Conversion.findDuplex(Integer.class, Temperature.class);
Converter<Object, Object> g = Conversion.find(TypeDeclaration.parse("java.util.List<Integer>"),
                                              TypeDeclaration.parse("java.util.Collection<my.Temperature>"));
```

- `find(input, output)` returns `null` when there is no way to convert. Check for it.
- If the input is already assignable to the output, a no-op `NullConverter` is returned.
- Results are cached. Looking up again is cheap, so converters are normally stored in a `static final` field.
- `find(output)` returns an `InputConverter`: it converts any value by looking at its runtime class (`Object` inputs are resolved this way).
- `findDuplex` returns `null` if either direction is missing.

Templates do the same: a cast on a method return type calls `find(real, exposed)`, a parameter cast calls `find(exposed, real)`,
and a field cast calls `findDuplex(real, exposed)`.

## Chaining

When no converter goes straight from the input to the output, the registry **chains** converters. The search works backwards from the output
type: it takes every converter that produces the output, then every converter that produces *their* inputs, and so on, until the requested input type
is reached. The result is a single `ChainConverter`. Properties:

- The **cheapest** path wins. Each converter in a path costs its own `getCost()` plus 1 for the step, so a path with fewer and cheaper steps is preferred.
  Conversion of an input type is only replaced by a cheaper path, never a more expensive one.
- Chains are built once per input/output pair, then cached. At runtime a chain just calls each converter in order, and stops (returning `null`) when one step returns `null`.
- Boxing is a free step (`int` to `Integer` and back are registered no-op converters), so a converter declared with `int` is also found for `Integer`.
- Chains can be surprising: a `String` can become a `Temperature` by parsing it to an `Integer` first, if only an `int -> Temperature` converter exists.
  A `Float` becomes one through `Number -> Integer` (the decimals are lost). If a path is not wanted, register a more specific converter of low cost, or make the unwanted step expensive.
- The search is bounded (10,000 steps). A cycle that is not cheaper never extends the search, but two registered converters that keep producing new types log `Cyclical Conversion step() detected!`.

### Cost

`cost` ranks converters when several paths exist. Lower is preferred; default 1.

- `@ConverterMethod(cost = N)` or an override of `getCost()` sets it. Lazy converters return 100.
- Use a higher cost for conversions that are lossy, slow or should only be used when nothing else works (a generic fallback).
- Costs only matter when two paths could be used. A single registered converter is used regardless of cost.
- `ChainConverter.getCost()` is the sum of the costs of its converters (`NullConverter` has cost 0).

Inspect the result of the search with `toString()` on the converter, which prints `[cost=N, input -> output]` and for chains each step.

## Sub-classing

The lookup honours the class hierarchy in both directions:

- **Output side**: a converter that produces a subtype also satisfies a request for the supertype. If a converter exists
  that makes an `ArrayList`, then asking for a `List`, a `Collection` or an `Iterable` can use it. A `List<String>` converter serves a
  `Collection<String>` request. The registry lists a type's own converters first, then those of its superclass and interfaces.
- **Input side**: a converter that accepts a supertype accepts any subtype. A converter from `Number` is used for `Integer` inputs, and one from `CharSequence`
  for `String`. A converter from `Object` accepts everything.
- When nothing matches, but the output type is a supertype of the input type, a runtime up-cast (`CastingConverter`) is used.
- Registering a converter with a raw output generic type (such as `String -> List`) also registers it for `List<?>`.

## Providers and Generics

A `ConverterProvider` has a single method, `getConverters(TypeDeclaration output, List<Converter> out)`. The registry calls it when it
needs the converters that produce `output`. The provider adds converters that apply to that exact (generic) type, or adds nothing.
Providers are how generic conversions work: the converter for `List<Entity>` is created on demand, once the element type is known.

A `@ConverterMethod` with type variables registers one. So does a static method annotated `@ProviderMethod` with the signature
`(TypeDeclaration output, List<Converter<?, ?>> converters)`, or a static `ConverterProvider` field, when passed to `registerConverters`.

**Collections and elements.** Converting `List<A>` to `List<B>` looks up the element converter `A -> B` with `findDuplex`
and wraps the list in a live, converting view (`ConvertingList`, `ConvertingSet` and so on in `conversion/util/`, see [converting-collections.md](converting-collections.md) for creating them by hand). The
elements are converted on access, and writes (`add`, `set`) are converted back through the reverse direction. The view and the original
share the same data. If the element types are the same, the collection is only converted to the requested collection type
(a `Set` to a `List` copies into a new `ArrayList`; a `List` asked as a `Collection` is returned as is). Element conversion needs a duplex path (both directions), except for `Iterable` targets, which only read.
If it is missing, `find` returns `null`. These converters cost 2.

## Built-in Converters

`BuiltinTypeConversion.register()` (`conversion/builtin/`) runs when `Conversion` is first loaded.

| Converters | What they do |
|---|---|
| `NumberConversion` | `String` parsed to `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` and `Number` (smallest fitting type). Any `Number` cast to each of the numeric types (truncating). |
| `ToStringConversion` | Everything to `String`: `CharSequence`, each number type and `Boolean` through their own `toString`, `char[]`, and `Object` (lazy, cost 100: calls `toString()`). |
| `BooleanConversion` | `Boolean` to `Byte`/`Short`/`Integer`/`Long` (0 or 1), `Number` to `Boolean` (non-zero is true). Cost 2. |
| `EnumConversion` (provider) | `String` (by name, case-insensitive) or `Number` (ordinal) to any enum type. |
| `CollectionConversion` (provider) | `Iterable` to `Iterable`, `Collection`, `Queue`, `List` and `Set`, converting the elements when their types differ. |
| `MapConversion` (provider) | `Map<K1, V1>` to `Map<K2, V2>`, converting keys and values through live views. |
| `ArrayConversion` | Primitive arrays to and from boxed arrays (`int[]` and `Integer[]`), `Object[]` to `List`, `List` or `Collection` to arrays, and `A[]` to `B[]` with element conversion. |
| `StreamConversion`, `PredicateConversion`, `ConsumerConversion`, `SupplierConversion` (providers) | `Stream<T>` to `Stream<R>` with `map`, `Collection<T>` and `T` to `Stream<T>`, and `Stream<T>` to `List<T>`. `Predicate`, `Consumer` and `Supplier` wrap the original, converting the argument or result. |
| `VoidTypeConverter` | Anything to `void`, producing `null`. |
| Boxed/unboxed `NullConverter`s | `int` to `Integer` and back, for every primitive. Free (cost 0). |

Everything composes: `String -> List<Integer>`, `int[] -> List<String>` or `Set<String> -> Collection<Level>` (with `Level` an enum) all resolve through chains of the above.

## Debugging

- `Conversion.find(...)` returning `null`, or an unexpected chain printed with `toString()`, is the first thing to check.
- `Conversion.debugTree(input, output)` prints the search tree for an output type, with the chosen path for the input marked as `>>Type<<`. Read it from the root
  (the output) downwards: each indented line is a type that can be converted into its parent.
- `Conversion.debugExportConverterTree(path)` writes every converter that has been cached so far (`toString()` of each) to a file.
- A converter that is not found often has an unresolved type (the class does not exist in this environment) or was registered after the cache was built for it.
  Registration warnings such as `Converter has unresolved input` point at these.

## Gotchas

- Registering a converter for the same input and output replaces the earlier one. Be careful with broad inputs (`Object -> X`); they apply to everything.
- Chains can pass through lossy built-ins (`Number -> Integer`). Check `toString()` of the converter when results look rounded.
- `convert` never throws on a type mismatch; it returns `null`. A `null` result for a non-null input means a mismatch or a failed step.
- Collection conversions return **views**, not copies (except when the collection type itself must change). Writing to the view writes to the source.
- Do not use templates in converter classes, see [Keep Converters Free of Template Logic](#keep-converters-free-of-template-logic).
- Handle types used in casts are initialized when `Conversion` first needs them, which can register converters. Do not hold the conversion lock from converter code.

## Where to Look

| Task | Files |
|---|---|
| Converter API and cost | `conversion/Converter.java`, `conversion/type/DuplexConverter.java`, `conversion/type/ChainConverter.java` |
| Registry, search tree, sub-class handling, caching | `conversion/Conversion.java` (`OutputConverterTree`, `OutputConverterList`) |
| Annotation handling | `conversion/annotations/`, `conversion/type/AnnotatedConverter.java` |
| Providers | `conversion/ConverterProvider.java`, `conversion/builtin/*Conversion.java` |
| Collection/map views | `conversion/util/Converting*.java` ([converting-collections.md](converting-collections.md)) |
| Template use | `reflection/declarations/FieldDeclaration.java`, `MethodDeclaration.java` and `Template.java` (search for `Conversion.find`) |
| Tests | `src/test/java/com/bergerkiller/mountiplex/ConversionTest.java` |
