# Legacy: `ClassTemplate` and Field/Method Accessors

> **Deprecated approach. Do not write new code with these types.** `ClassTemplate` predates the [template system](template-syntax.md).
> New code should declare members in templates and use generated handles, or use [`FastMethod`/`FastField`](fast-access.md) for
> plain reflection. This page exists so the old code can be read and maintained, or migrated. It deliberately has no usage examples.

Source: `src/main/java/com/bergerkiller/mountiplex/reflection/`: `ClassTemplate.java`, `FieldAccessor.java`, `MethodAccessor.java`,
`TranslatorFieldAccessor.java`.

## What `ClassTemplate` Is

`ClassTemplate<T>` is a reflection model of one class: a type-aware, error-tolerant way to describe the members of a class (usually an
internal one that changes between versions) in Java code instead of in a template file. A class that wraps an internal type used to hold
a static `ClassTemplate` constant, plus one `FieldAccessor` or `MethodAccessor` constant per member, each declared with a text declaration
in the same syntax as [template member declarations](template-syntax.md#member-declarations).

Where the old design differs from templates:

| | `ClassTemplate` | Template system |
|---|---|---|
| Where members are declared | Java code, as declaration strings in static initializers | Template files (or annotations), separate from Java code |
| Version differences | Handled by hand in Java, with `if` statements around the string | `#if` conditions in the template |
| Output | Accessor objects, held in fields by the class itself | Generated handle classes with real typed methods |
| Missing members | Warning in the log; the accessor is invalid (`isValid()` is false) and does nothing | Logged and reported in a consistent diagnostics format ([Diagnosing Missing Members](template-syntax.md#diagnosing-missing-members)) |

## Creating and Describing a Class

A `ClassTemplate` is made from a class, an instance, or a class name (`create(Class)`, `create(Object)`, `create(String)`, each returning an
empty, invalid template if the class cannot be found). `addImport` adds import paths used to resolve short type names in the declaration strings,
the same role as imports in a template. `resolveClass` and `resolveClassName` use those imports. `getType()` is the class, `isValid()` whether it exists.
Subclasses (BKCommonLib's `PacketType` is one) use the protected `setClass`.

## Declaring Members

The declaration methods parse a declaration string, run it through the registered name resolvers ([Resolver](overview.md)), find the real member
and return an accessor. All of them log a warning with similar candidates if nothing matches, and return an invalid accessor rather than throwing.

| Method | Finds | Notes |
|---|---|---|
| `nextField(declaration)` | The next field, in declaration order, matching the modifiers, type **and name** | Keeps a cursor: each call continues after the previously matched field, and skips (and forgets) anything before it. Fields must therefore be declared in the same order as in the class. |
| `nextFieldSignature(declaration)` | The next field matching modifiers and type, **ignoring the name** | For obfuscated names that are unstable. Logs the skipped fields, and warns when the name differs. |
| `skipField(declaration)`, `skipFieldSignature(declaration)` | Same as the above, discarding the result | Moves the cursor past a field that is not needed. |
| `selectField(declaration)` | A field by declaration, anywhere in the class | No cursor. Logs close matches (same type, similar name) when it is missing, to help see what changed. |
| `selectStaticValue(declaration)` | The value of a static field | `null` on failure. |
| `selectMethod(declaration)` / `selectRawMethod(...)` | A method, as a `MethodAccessor` or a raw `java.lang.reflect.Method` | |
| `getField`, `getMethod`, `getConstructor` | Members by plain name and types | Without declaration strings. |
| `getStaticFieldValue`, `setStaticFieldValue` | Static fields by name, optionally converted | |

Field order matters for the `next*` methods: the cursor only moves forward, which is why old code lists every field of the class in order. A field missing from
the middle of the class is reported as skipped, which is a useful signal that the class changed.

## Working with Instances

| Method | Does |
|---|---|
| `newInstance()` | A new object through the empty constructor. |
| `newInstanceNull()` | A new object **without** running any constructor. All fields are default values. |
| `cloneInstance(object)` | A shallow clone: a null-constructed instance with every field copied over. |
| `transfer(from, to)` | Copies all the fields from one instance to another. |
| `getFields()`, `getFieldAt(index)` | The fields of the class hierarchy in declaration order. |
| `isInstance`, `isAssignableFrom`, `isType`, `cast`, `tryCast` | Type checks and casts relative to the described class. |

## Accessor Types

`FieldAccessor<T>`, `MethodAccessor<T>` and `TranslatorFieldAccessor<T>` are interfaces and a class describing "something that gets or sets a
value" without exposing how. They are what the declaration methods return, and they remain useful as a bridge, because the template system can produce them too
(template field and method elements have `toFieldAccessor()`).

| Type | Role |
|---|---|
| `FieldAccessor<T>` | `get(instance)`, `set(instance, value)` (returns `false` when it could not set), `isValid()`, and `transfer(from, to)`. Static members take a `null` instance. |
| `FieldAccessor.translate(DuplexConverter)` | Wraps the accessor in a `TranslatorFieldAccessor` that converts the stored value on `get` and the new value on `set` (see [conversion.md](conversion.md)). |
| `FieldAccessor.ignoreInvalid(defaultValue)` | An accessor that quietly does nothing, returning the default value, if the field is missing. |
| `FieldAccessor.wrapMethods(getter, setter)` | Makes a "field" out of a getter and a setter method (template methods, converted template methods, or `MethodAccessor`s), so code that expects a field works with members that are really methods in some versions. |
| `MethodAccessor<T>` | `invoke(instance, args...)`, `isValid()`, `isMethod(Method)`. |
| `TranslatorFieldAccessor<T>` | A `FieldAccessor` with a converter pair. `getInternal` and `setInternal` bypass the conversion. |

The concrete implementations behind these (`SafeField`, `SafeMethod` and their direct variants) are legacy as well and are not documented.
New accessors-like objects should be `FastField` and `FastMethod`.

## Where It Is Still Found

BKCommonLib keeps many `@Deprecated` classes in `com.bergerkiller.reflection.net.minecraft.server` (the `NMS*` classes, one per internal class).
Each holds a static `ClassTemplate` and a `FieldAccessor` per member, most of them now forwarding to generated handles (through `toFieldAccessor()`),
so older plugins that still call them keep working. A few other places still use `ClassTemplate` directly (`PacketType`, `CommonEntityType`,
`CommonPacket`).
When changing code that does, prefer migrating it to handles over extending the old API. Do not add new `NMS*`-style classes.
