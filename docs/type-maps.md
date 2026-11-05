# Sub-class Aware Type Maps (`InputTypeMap`, `OutputTypeMap`, `InputTypeSet`)

Collections in `src/main/java/com/bergerkiller/mountiplex/reflection/util/` that store values **per type** and answer lookups
with the class hierarchy taken into account. A plain `Map<Class, V>` only finds exact matches. These find "everything that applies to this
type, including what was stored for its super types (or sub types)". They are public and used outside Mountiplex too.

| Class | Lookup of type `X` returns values stored at |
|---|---|
| `InputTypeMap<T>` | `X` itself, and every **super type** of `X` (superclasses and interfaces) |
| `OutputTypeMap<T>` | `X` itself, and every **sub type** of `X` |
| `InputTypeSet` | A set of types: `contains(X)` is true when `X` or one of its super types was added |

Think of *input* as "a value of type X is given, which stored rules apply to it?" and *output* as "a value of type X is wanted, which stored
things could produce one?". `TypeMap<T>` is the shared abstract base. The two maps differ only in the direction of `isParentTypeOf`.

## Using `InputTypeMap` and `OutputTypeMap`

Types are `Class` or `TypeDeclaration` (so generics are respected, see [glossary](glossary.md)). Results, as run:

```java
InputTypeMap<String> in = new InputTypeMap<>();
in.add(Number.class, "number");
in.add(Object.class, "object");
in.add(Long.class, "long");
in.add(Collection.class, "coll");

in.getAll(Long.class);          // [long, number, object]   own value first, then more specific super types first
in.getAll(Integer.class);       // [number, object]
in.getAll(String.class);        // [object]
in.getAll(ArrayList.class);     // [coll, object]           interfaces count too
in.get(Long.class);             // "long"                   the first value only: the most specific one
in.getAll(TypeDeclaration.parse("java.util.List<String>"));   // [coll, object]

OutputTypeMap<String> out = new OutputTypeMap<>();
out.add(Long.class, "long");
out.add(Float.class, "float");
out.add(String.class, "str");

out.getAll(Number.class);       // [long, float]            sub types of Number
out.getAll(Long.class);         // [long]
out.getAll(Object.class);       // [long, float, str]       everything
```

Methods (shared by both, from `TypeMap`):

| Method | Use |
|---|---|
| `get(type)` | The first value for the type (the most specific match), or `null`. |
| `getAll(type)` | All values that apply, as an unmodifiable list. Safe to hold on to. It is cached, so repeated lookups are cheap. |
| `add(type, value)` | Adds a value at the type. Several values per type are allowed. |
| `put(type, value)` | Replaces all values stored **at exactly that type** with one value. |
| `putAll(type, values)` | Replaces the values stored at that type with a collection. |
| `amend(type, value)` / `amendAll(...)` | Stores only when nothing is stored at that exact type yet. Returns whether it stored. |
| `remove(type, value)` | Removes one value from that exact type. |
| `containsKey(type)` | True if anything **applies** to the type (including inherited values). Not the same as "stored at exactly this type". |
| `values()` | Every stored value, regardless of type. |
| `clear()`, `clone()` | Reset and copy. |

`add`, `put` and `remove` only act on the exact type given. Hierarchy only affects what `get`, `getAll` and `containsKey` return.
Replacing a super type's values updates what sub types see (`in.put(Number.class, "N2")` changes `getAll(Long.class)` to `[long, N2, object]`).

## `InputTypeSet`

```java
InputTypeSet set = new InputTypeSet();
set.add(TypeDeclaration.fromClass(ArrayList.class));
set.contains(TypeDeclaration.fromClass(ArrayList.class));   // true
set.contains(TypeDeclaration.fromClass(LinkedList.class));  // false
set.contains(TypeDeclaration.fromClass(List.class));        // false
```

`contains(X)` is true if `X` **or a super type of `X`** was added. Adding `Collection` makes `ArrayList`, `LinkedList` and `Set` types contained.
Adding `ArrayList` does not make `List` contained. This is covered by `TypeMapLookupTest`.

## Where They Are Used

| Use | Class | Purpose |
|---|---|---|
| Conversion search | `InputTypeMap` in `Conversion.OutputConverterTree` | Finds the cheapest converter for an input type through converters registered for its super types (a converter from `Number` serves `Integer`). See [conversion.md](conversion.md#sub-classing). |
| Class hooks | `InputTypeMap<Method>` in `ClassHook` | Finds the super method to call for a declaring type, including its super types. See [classhook.md](classhook.md). |
| Remapping | `InputTypeMap<ClassRemapping>` in `Remapping` | Finds the member remappings declared for a class and for its super types. |
| BKCommonLib | `OutputTypeMap<MapDisplay>` in `CommonMapController`; `InputTypeMap` in `AdvancementDataPlayerHook` | Finds all registered displays of a given display class or its sub classes. |

## Gotchas

- Looking up a type creates a bin for it, so the maps grow with the number of distinct types queried. Types are cheap, but do not feed them unbounded generated types.
- The maps are **not synchronized**. Only the lists returned by `getAll` are safe to share. Guard `add`, `put`, `remove` and first-time lookups of new types with your own lock when used from several threads.
- Ordering inside a result: the type's own values in insertion order, then the values of related types, with the more specific type first.
- Equality of types is by `TypeDeclaration`, so `List<String>` and `List<Integer>` are different keys, and `List<?>` or a raw `List` covers both.

## Where to Look

`reflection/util/TypeMap.java` (all logic, including caching), `InputTypeMap.java`, `OutputTypeMap.java`, `InputTypeSet.java`, and the test
`src/test/java/com/bergerkiller/mountiplex/TypeMapTest.java`.
