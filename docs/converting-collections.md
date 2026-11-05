# Converting Collection Views (`ConvertingList`, `ConvertingMap`, ...)

Wrappers that present a collection of one element type as a collection of another, converting elements as they are read and converting them back as they are written.
The [conversion system](conversion.md) creates them automatically for generic casts such as `List<Internal>` to `List<Handle>`. This page is for the cases where that does not happen: code, such as a
converter, a template method body or a hand-written Java method, that has a collection and must create the view itself. Source: `src/main/java/com/bergerkiller/mountiplex/conversion/util/`.

## Views, Not Copies

The views **do not copy** the collection. Every call goes to the original (the *base*) collection, and each element is converted at the moment it is read or written. That is the point: a
large collection (every entity in a world, every block in a chunk) can be exposed with the types of the public API without cloning it, and changes made through the view, or to the base, are visible on the other side right away.

This has consequences to design for:

- **Writes go through.** `add`, `set`, `remove` and `clear` on the view change the base collection.
- **The view is live.** If the base changes, the view changes (and an iteration can fail with `ConcurrentModificationException`, as with the base).
- **Elements are converted on every access.** If the converter creates a new object each time (a handle wrapper, for example), two calls to `get(0)` return different objects. Compare with `equals`, not identity, and do not cache the result expecting the view to do it.
- **Not thread-safe by themselves.** They add no synchronization, so thread safety is exactly that of the base collection.
- **No `toString`, `equals` or `hashCode`.** The views implement the interface only, so printing a view shows the default object representation. Copy it first to log it.

### If the Result Must Be Immutable or Detached

Never hand out a view where the caller expects a snapshot or a collection that cannot be modified. Create a copy explicitly, in the template or Java code that returns it:

| Need | Do |
|---|---|
| A detached snapshot of the converted elements | `new ArrayList<>(view)` (or `new HashSet<>(view)`). It converts every element once, then no longer follows the base. |
| Immutable | `Collections.unmodifiableList(new ArrayList<>(view))`. Wrapping only the view in `unmodifiableList` blocks writes but is still live. |

## The Types

| Class | Wraps | Notes |
|---|---|---|
| `ConvertingCollection<T>` | `Collection<?>` | The base of the others. Implements the `Collection` API; `getBase()` and `getElementConverter()` give access to the wrapped collection and the converter. |
| `ConvertingList<T>` | `List<?>` | Adds index access, `listIterator` and `subList` (the sub list is a view too). |
| `ConvertingSet<T>` | `Set<?>` | |
| `ConvertingQueue<T>` | `Queue<?>` | `offer`, `poll`, `peek`, `element` and `remove` convert. |
| `ConvertingMap<K, V>` | `Map<?, ?>` | Takes a key converter and a value converter. `keySet()`, `values()` and `entrySet()` are views too. |
| `ConvertingEntry<K, V>` | `Map.Entry<?, ?>` | Used by the map's `entrySet()`. `setValue` writes through. |
| `ConvertingIterator<T>`, `ConvertingListIterator<T>` | `Iterator<?>`, `ListIterator<?>` | The iterators the views return. `remove`, `set` and `add` go to the base. |
| `ConvertingIterable<T>` | `Iterable<?>` | Read-only: its `iterator()` converts elements. It takes a plain `Converter` and needs no reverse direction. |

`ConvertingList` and the others require a **`DuplexConverter`**: it has the converter in both directions (`convert` for reading, `convertReverse` for writing, `reverse()` for the swapped converter). `ConvertingIterable`
and `ConvertingIterator` only read, so a one-way `Converter` is enough.

## Creating One

1. **Get an element converter that goes in both directions.**
   - Look one up: `Conversion.findDuplex(Integer.class, String.class)`, or with `TypeDeclaration`s for generic types. It returns `null` if no duplex path exists. This includes the handle converters that are registered automatically for every
     handle class, so `Conversion.findDuplex(InternalType.class, SomeHandle.class)` works for handles ([see conversion.md](conversion.md#handle-converters-are-registered-automatically)).
   - Or write one: subclass `DuplexConverter<A, B>` with `convertInput(A)` and `convertOutput(B)` (constructor takes the two types), or build one from two one-way converters with `DuplexConverter.pair(forward, reverse)`.
2. **Wrap the collection** in the matching type, passing the base and the converter: `new ConvertingList<B>(list, converter)`.
3. **Return the view**, or copy it if it must be detached (see above).

```java
// InternalEntity: a server type, EntityHandle: its generated handle. Both names are placeholders.
DuplexConverter<InternalEntity, EntityHandle> toHandle = Conversion.findDuplex(InternalEntity.class, EntityHandle.class);
List<EntityHandle> handles = new ConvertingList<EntityHandle>(internalEntityList, toHandle);

Map<String, Wrapper> view = new ConvertingMap<String, Wrapper>(baseMap, keyConverter, valueConverter);
```

Run checks used to write this page: a `ConvertingList` over `List<Integer>` with the `Integer`/`String` converter reflected `add`, `set` and `add(index, ...)` into the base; a `ConvertingMap` with a custom `Wrapper` converter read
`get("1")` as the wrapper and stored `put("2", ...)` as the raw value `20`; a `ConvertingIterable` read the live list. The elements of a copy (`new ArrayList<>(view)`) did not change when the base was cleared.

## Through the Conversion System

A cast between generic collection types does all of the above for you. Calling `Conversion.find(inputType, outputType).convert(list)` with `TypeDeclaration`s such as `List<A>` and `List<B>` returns a `ConvertingList` over the same list if an element converter exists.
The built-in providers (`CollectionConversion`, `MapConversion`, `ArrayConversion`, see [conversion.md](conversion.md#built-in-converters)) choose the right type for `Collection`, `List`, `Set`, `Queue`, `Iterable`, `Map` and arrays
(an array is exposed as a fixed-size `ConvertingList` over `Arrays.asList`, so writes go through to the array). In templates, `(List<ExposedType>)` on a field or return value gets the same view automatically. Write it by hand when
the conversion cannot be expressed as a type cast: the elements need a converter you build at runtime, the collection comes from a source with no matching template, or the result must be a copy that is not the default view.

## Gotchas

- A `Converter` that is not a `DuplexConverter` cannot be used for lists, sets, queues or maps. Find or build the duplex.
- `null` elements go through the converter too. A converter that rejects `null` input (check `acceptsNullInput`) is handled by the converter itself.
- `contains`, `indexOf` and `remove(Object)` convert the argument back to the base type first, then search the base. They only find elements the converter maps back to an equal base element.
- A `Set` view of a converter that maps two different base elements to equal values lists both, since the base decides uniqueness. Keep the converter one-to-one.
- Because the view is live, do not iterate it on a thread while the base is changed by another.

## Where to Look

| Task | Files |
|---|---|
| The view classes | `conversion/util/Converting*.java` |
| Duplex converters and `pair` | `conversion/type/DuplexConverter.java` |
| Automatic creation | `conversion/builtin/CollectionConversion.java`, `MapConversion.java`, `ArrayConversion.java`, `EntryConverter.java` |
| Lookup of element converters | `conversion/Conversion.java` (`findDuplex`) |
