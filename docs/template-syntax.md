# Template Syntax

Reference for the template language: the text format in which classes, fields, methods and constructors
of an [environment](glossary.md) are declared. **Read this page before interpreting, writing or
changing any template code**, and before changing the output or parsing of templates.
See [overview.md](overview.md) for how templates fit into the project. Larger, complete examples live in
[examples/template-walkthrough.md](examples/template-walkthrough.md).

## How to Recognize Template Code

Template code can be recognized by any of these signs:

1. **Plain `.txt` files containing Java-like code with `#macros`** (`#if`, `#require`, `#remap`, `#include`...)
   that are not valid in normal Java. Usually a root file (conventionally `init.txt`) that `#include`s the rest.
2. **Block comments labelled `<NAME>` placed directly above an annotation using `"%NAME%"`**, on a class
   extending `Template.Class` or `Template.Handle`. This is template code embedded in Java, for example
   `@Template.Generated("%NAME%")`. The code inside the comment (lines prefixed with `*`) is the template
   body. See [Embedded in Java Annotations](#embedded-in-java-annotations).
3. **Abstract classes named `*Handle`** (extending `Template.Handle`) that contain a static `T` field built by
   `Template.Class.create(...)`. These are generated from, or described by, templates. Changing their
   behaviour means changing the template (or its annotations), not editing generated Java by hand.
4. **Annotations from `Template`**: `@Template.InstanceType`, `@Template.Generated`, `@Template.Import`,
   `@Template.Require`, `@Template.Package`, `@Template.Optional`, `@Template.Readonly`.
5. A `#resolver` or `#include` line at the top of a text file.

Generated `*Handle.java` files (written by the Gradle plugin) are **output**: edit the `.txt` template and regenerate.
The class Javadoc and the `T` member are the clues that they are generated. The Javadoc looks like this
(the type name varies):

```java
/**
 * Instance wrapper handle for type <b>com.mojang.authlib.GameProfile</b>.
 * To access members without creating a handle type, use the static {@link #T} member.
 * New handles can be created from raw instances using {@link #createHandle(Object)}.
 */
@Template.InstanceType("com.mojang.authlib.GameProfile")
public abstract class GameProfileHandle extends Template.Handle {
```

## File Structure

A template file consists of, in order of typical use:

- Package and import lines: `package a.b.c;` and `import a.b.Name;`. They provide the
  [`ClassResolver`](overview.md#template-context-classresolver) context for the classes that follow. Imports
  can also reference generated handle types, used in casts (see Type Casts).
- Directive lines (macros, below).
- One or more class declarations, each containing member declarations.

`//` line comments and `/* */` block comments are ignored.

```
package net.minecraft;                 // context for the classes below

import java.util.UUID;                 // short names usable in declarations
#include /net/minecraft/other.txt      // directive

class CrashReport {                    // class declaration
    public int getCount();             // member declaration
}
```

## Imports and Type Names

Type names are resolved through the template's package and imports (the
[`ClassResolver`](overview.md#template-context-classresolver)), but several places do not see them. Rules:

1. **Exposed types must be importable by generated code.** The generated `*Handle` classes reuse the template's imports. Every
   type that is exposed outside (the `Type` in a `(Type) InnerType` cast, and any type visible in a handle method or field
   signature) must either be imported at the top of the file or written fully qualified in the declaration.
   If a type is used only once, prefer omitting the import and writing the fully qualified name in that declaration.
2. **Conditions are evaluated without imports.** Preprocessing macros such as `#if`, `#elseif` and `#select` run before and
   independently of `import` statements, so all type names written in a condition (for example in `exists` and
   `assignable`) must be fully qualified.
3. **`#require` honors the file's context.** Its declaration is parsed late, in the context of the file, so it can use the
   file's package and imports and does not need fully qualified names.

**Handle classes are never imported automatically.** A `*Handle` type used in a cast, such as `(CrashReportHandle)`, must
be imported explicitly (or written fully qualified) like any other type, even when the template itself is what generates
that handle. It can look self-referential, because the class does not exist until the template has been processed, but
it works: the generated class lives in the project's generated sources package, and the import only has to name it.

```
package net.minecraft;

import com.bergerkiller.generated.net.minecraft.CrashReportHandle;     // generated from this very file

class CrashReport {
    public static (CrashReportHandle) CrashReport create(Throwable throwable, String message);
}
```

Method bodies are compiled with Javassist, where fully qualified names are always the safe choice (see
[Method Bodies](#method-bodies)).

```
import net.minecraft.world.phys.Vec3;                  // used twice below, so imported
import com.example.generated.Vec3Handle;               // generated handle used in a cast

class Entity {
    public (Vec3Handle) Vec3 getPosition();            // exposed type Vec3Handle: imported
    public (com.example.generated.AABBHandle) net.minecraft.world.phys.AABB getBox();   // used once: fully qualified
#if exists net.minecraft.world.phys.Vec3 public double length();   // condition: always fully qualified
    ...
#endif
}
```

## Class Declarations

A class declaration names the real class of the environment (resolved through the package and imports):

- `class Name { ... }` or `class Name extends Base { ... }`: the base is used so inherited members are
  visible and generated handles extend the base handle. `interface Name { ... }` is also supported.
- `optional class Name { ... }`: the class need not exist in the environment. If it is missing, its members are
  silently unavailable instead of being reported as an error.
- Nested classes are declared with the fully qualified dotted name: `class Outer.Inner { ... }` (also
  `optional class Outer.Inner`, and `extends` works). They are written inside the outer class body and may also appear as
  separate declarations. The nested name must always be qualified with its outer class; a bare `class Inner`
  is not supported, because the name has to resolve to the real class. The same qualified form is used
  when the nested type is referenced in member declarations, such as `Direction.Axis`.

```
class Direction {
    class Direction.Axis {                       // nested: always the qualified name
        public static final Direction.Axis X;
    }
    optional class Direction.Plane { }           // nested and optional
}
class Zombie extends Monster { }                 // base handle is reused
```

## Member Declarations

Members look like Java declarations, ending with `;` (declarations without a body) or a `{ ... }` body (custom code).

| Form | Example shape | Meaning |
|---|---|---|
| Field | `private int count;` | Exposes a (possibly private) field as getter/setter. |
| Static field | `public static final int MAX;` | Exposes a static field. |
| Method | `public String getName();` | Exposes an existing method. |
| Static method | `public static Thing create(int x);` | Exposes an existing static method. |
| Constructor | `public Thing(int x);` | Exposes a constructor. |
| Enum constant | `enum Kind VALUE;` | Exposes an enum constant. |
| Method with body | `public int sum() { ... }` | Custom code, see [Method Bodies](#method-bodies). |

```
class Thing {
    private int count;                            // field: getCount()/setCount()
    public static final int MAX;                  // static field
    public String getName();                      // method
    public static Thing create(int x);            // static method
    public Thing(int x);                          // constructor: createNew(x)
    enum Kind VALUE;                              // enum constant
    public int sum() { return instance.a() + instance.b(); }   // method with body
}
```

### Modifiers

Normal Java modifiers (`public`, `private`, `protected`, `static`, `final`, ...) describe the real member, with
one leniency for **access**: a member declared less accessible than it really is still matches, but not the other
way around. A member declared `private` matches a real private, protected or public member; a member declared
`public` only matches a real public one (in the code, only `public` is verified strictly, in `FieldDeclaration` and
`MethodDeclaration`). Declare a member `public` only when you need it to match (and gate on) a real public member.

This has two consequences:

- **`#require` is declared once, as `private`.** It works whether the real member is private, protected or public, so
  there is no need for a second `public` variant per access level.
- **`exists` with `public` guards direct access.** Inline code that touches a member by name, without `#require`
  (for example `instance.field`), is compiled as a normal access and only works if the member is really public. Gate it
  with a `public` declaration: the `#if` then only passes when the member is actually public, so an "accessing a
  private member" error cannot occur.

```
#if exists path.to.Class public String field;       // passes only if 'field' really is public
    instance.field = "hello";                       // direct access is therefore safe
#endif

#require path.to.Class private String field;        // once; works for any real access level
```

Additional template-only modifiers:

- `optional`: the member may be absent. A missing optional member is not an error. Optional members get **no**
  getter, setter or invoke method in the main `*Handle` class; they are only available through the `Class` (`T`) member, which
  makes them closer to plain reflection (check validity before use). **Avoid defining new optional members.**
  Prefer a normal (non-optional) method with a method body that works on every environment: when the environment lacks
  the underlying member, the body supplies a default that does nothing, returns a constant, or throws an error.
- `readonly`: no setter is generated for a field.
- `unknown`: legacy modifier for a member of unknown purpose, omitted from the exposed API. It existed for
  field-ordering checks: when implementations had heavily obfuscated fields, unknown fields padded the gaps between known
  fields so breakage (added, removed or reordered fields) was easier to identify. No longer in active use; **do not
  declare new ones**. Existing ones can still be seen in older templates.
- `rawtype`: generated code suppresses raw-type warnings for the member.

```
public readonly boolean viewingCredits:wonGame;                  // no setter
protected optional final Map<V, K> inverse:###;                  // only on T; avoid new ones
public rawtype java.util.List items;                             // no raw-type warning
```

### Name Aliases (`alias:realName`)

A name may be written `alias:realName`. The part **before the colon** is the readable name exposed by the
handle; the part **after the colon** is the name that actually exists in the environment, which may be obfuscated
or version specific. A name without a colon uses one name for both. The `Resolver` may translate or add alternative
names, so template names are not necessarily the literal names. Constructors use `<init>` as the real name when
referenced as a method (for example in `#require`). A placeholder real name such as `###` is used by convention
for a member that does not exist on this version. (The `:???` form seen in `ClassHook` subclasses is unrelated
and never appears in templates; see [classhook.md](classhook.md#the-name-convention-legacy).)

```
public (CrashReportHandle) CrashReport create:forThrowable(Throwable throwable, String message);
//                                              ^ exposed as create(...), real method is forThrowable
public int size:###();                                           // no real name on this version
```

Aliases can **chain**: `a:b:c`. The first segment is the exposed name, the last segment is the name that is
matched against the real class, and the segments in between are alternatives. Templates rarely write this by hand;
it usually arises because the `Resolver` remaps names at runtime and appends the translated name. Expect it in
logs: a template member `name:privateName` may be reported as `name:privateName:a` in a "Failed to find ..." warning
(see [Diagnosing Missing Members](#diagnosing-missing-members)), where `a` is the remapped name that was actually
searched. When reading such a log, the template name is the leading part, and the trailing part is what the
current environment calls it. (Source: `NameDeclaration`, which splits at the last `:`.)

### Type Casts (`(Exposed) Real`)

A type may be prefixed with a parenthesised **exposed type**. The type after it is the **real type** in the
environment; the cast type is what the generated handle shows. The [conversion system](conversion.md) translates
between them at runtime. Applies to return types, parameters and field types, including generics.

For example, a method returning an internal `CrashReport` can be exposed as returning `CrashReportHandle`, so
callers never see the internal type. Parameters work the same way in reverse. The handle type must be imported
(see [Imports and Type Names](#imports-and-type-names)).

```
public (CrashReportHandle) CrashReport create(Throwable t);          // return type
public void setOwner((EntityHandle) Entity owner);                   // parameter
public (List<EntityHandle>) List<Entity> getPassengers();            // generics
private final (Holder<MobEffectHandle>) MobEffect effect;           // field
```

The converters must exist when the member is initialized. Writing and registering one: [examples/custom-converter.md](examples/custom-converter.md).

### Private and Inaccessible Types

Some templates describe members whose class is itself non-public (for example a private nested class). Such a type
cannot be named from generated or compiled code. Mountiplex detects this (`Resolver.getMeta(type).isPublic`) and
treats the type as `Object` in the generated API. To operate on values of such a type, use `#require`
in a method body (or class-level) to access their fields and methods, and to construct them via `<init>`,
see [Requirement Syntax](#requirement-syntax-name). Do not rely on the type name in handle-facing declarations.

```
public void use() {
    #require pkg.Outer.Hidden public int value;         // Hidden is a private nested class
    #require pkg.Outer private Outer.Hidden hidden;
    Object h = instance#hidden;                          // typed as Object, not Hidden
    int v = h#value;
}
```

### Method Bodies

A method followed by `{ ... }` is custom code compiled at runtime (with Javassist, so only a limited subset of
Java is available: avoid generics, lambdas and enhanced `for` loops; use raw types and `Outer$Inner` names for nested classes).
Used to mock methods that do not exist or to add behaviour.

```
public boolean isBaby() {
#if version >= 1.9
    return instance.isBaby();
#else
    return instance.getAge() < 0;
#endif
}
public static String describe(Object o) {                // static: no 'instance'
    java.util.Map$Entry entry = (java.util.Map$Entry) o; // nested class via $, no generics
    return String.valueOf(entry.getKey());
}
```

- The variable `instance` refers to the wrapped object in non-static methods. Static methods have no `instance`.
- Types are resolved with the template's package and imports. Fully qualified names are always safe.
- Macros (`#if`, `#select`, ...) may appear inside bodies to adapt the code to the environment.
- Member access uses the **requirement syntax** below, so version-specific names stay in one place.
- Names in bodies are subject to Resolver remapping, and `#remap` rules apply to them.

### Requirement Syntax (`#name`)

A `#require` directive declares a member of a class once; bodies then use it with `#`:

- `instance#field` reads or writes a field; `instance#method(args)` calls a method. `other#name` works on any object.
- A static member is used as `#name` or `#name(args)`.

  ```
  #require Entity protected int tickCount:ah;
  #require Entity public static int nextId:b();
  ...
  int ticks = instance#tickCount;            // read
  instance#tickCount = ticks + 1;            // write
  int id = #nextId();                        // static
  ```
- **Assign the result before using it.** Store the result of a requirement call in a variable declared with
  its type, and only then call methods on it. Chaining directly on the result, such as
  `instance#method(arg).doOtherThing()`, does not work reliably. Write instead:

  ```
  Type value = instance#method(arg);
  value.doOtherThing();
  ```

- **Scope:** a `#require` written inside a method body is only visible in that one method. A `#require` written
  outside a method body (in the class body, above the methods) can be used by every method declared below it.
  Declare a requirement once outside when several methods need it, to avoid repeating it.

  ```
  #require Thing public static int getBonus:b();          // class-level: usable by every method below it

  public int total() {
      #require Thing private int base:a;                  // body-scoped: only visible in total()
      return instance#base + #getBonus();
  }
  public int bonusOnly() {
      return #getBonus();                                 // OK. instance#base would not be visible here
  }
  ```
- `##` is not a requirement; it is left as written.
- **Constructors** are required as a method named `<init>`, given an alias before the colon. The return type is the
  constructed class, and it is called like a static method that returns the new instance:

  ```
  #require pkg.Outer.Inner private pkg.Outer.Inner create:<init>(int a, String b);
  ...
  Object result = #create(1, "x");
  ```

  Casts work on the parameters (`(Packet) packet`), as with other requirements. A mismatching constructor
  is reported as `Failed to find constructor ...` (see Diagnosing Missing Members). Typical use is a private
  or inaccessible class that has no handle, so the constructor can only be reached from a method body.
  The full BKCommonLib version of this is in [the walkthrough](examples/template-walkthrough.md#4-constructor-requirement-code-block-and-bootstrap).
- The name is the alias of the declaration, so the version-specific real name is only defined in the `#require`.

### `<code>` Blocks

Inside a class declaration, `<code> ... </code>` contains ordinary Java placed **verbatim into the generated handle
class** (not compiled by Javassist). Used for helper methods that wrap handle calls.

Types used inside the block are **not** resolved through the template's `import` statements, and the block is plain
Java compiled with the generated file. A type is only usable by its short name if the generated file already imports
it (because the same type is exposed elsewhere in that handle class) or the block has its own import. So:

- If the type is not already an exposed type elsewhere in the class, use its **fully qualified name**. This is the
  safest option, and is preferred when the type is used only once.
- An `import x.y.Z;` line on its own line in the block (not just at the very start) is extracted by
  the parser (`DeclarationParserTypes.CODE_BLOCK`) and added to the generated file's imports, so it works.
  It is not checked against the imports the generator creates itself, so a different type with the same short
  name will give a compile error in the generated class. Fully qualified names avoid this.

```
class SynchedEntityData {
    public static (SynchedEntityDataHandle) SynchedEntityData createNew((EntityHandle) Entity owner);

    <code>
    import org.bukkit.entity.Entity;                    // hoisted to the top of the generated file

    /** Convenience overload taking a Bukkit entity */
    public static SynchedEntityDataHandle createNew(Entity owner) {
        return createHandle(T.createNew.raw.invoke(com.example.Conversion.toHandle(owner)));  // single use: fully qualified
    }
    </code>
}
```

### Custom `createHandle`

Each handle class normally creates handles with a default `createHandle(Object)`. A template can replace it by declaring a
**static method named `createHandle` with exactly one parameter of type `Object`**, with a body. It must have no alias.
Mountiplex then calls it whenever the [automatic handle converter](conversion.md#handle-converters-are-registered-automatically) turns an
internal value into a handle (for example for every `(XHandle) X` cast), instead of the default.

The method decides which handle to create, so it can return a **subclass** of the handle for the given instance. Typical uses are validating the
input, or picking a more specific handle type from the value. BKCommonLib's `net/minecraft/nbt.txt` does this for `Tag`: it checks the instance
and calls the generated `createHandleForData`, which picks the handle subclass (string tag, int tag, ...) for the tag:

```
class Tag {
    public static TagHandle createHandle(Object instance) {
        if (!(instance instanceof Tag)) {
            throw new IllegalArgumentException("Input is not an instance of Tag");
        }
        return com.bergerkiller.generated.net.minecraft.nbt.TagHandle.createHandleForData(instance);
    }
}
```

The body is a normal method body, so the [method body rules](#imports-and-type-names) apply (fully qualified names).

## Conditions (Expressions)

A **condition** is the expression after `#if` / `#elseif`, in a `#select` case (as `variable operator value`), and
in `@ClassHook.HookMethodCondition("...")` on hooks (see [classhook.md](classhook.md)). All of them are evaluated
by `ClassResolver.evaluateExpression` (`reflection/declarations/ClassResolver.java`) against the variables of the
`ClassResolver` in use, and the environment's classes. Read that method for the authoritative behaviour.

| Form | Meaning |
|---|---|
| `name` | True if the variable `name` is set (its value is not inspected). |
| `!name` | True if the variable is not set. A `!` prefix negates any form below. |
| `name <op> value` | Compares the variable with a value. Operators: `==`, `!=`, `<`, `<=`, `>`, `>=`. Example shape: `version >= 1.12`. |
| `exists <class>` | True if the class can be loaded. |
| `exists <class> <member declaration>` | True if the class has that field, method or constructor (declared with the [member syntax](#member-declarations)). A member that does not resolve counts as not existing. |
| `assignable <classA> <classB>` | True if `classA` is assignable from `classB`. If either class is missing: true (false when negated). |
| `true`, `1` | Constant true (only when no such variable is set). |
| `a && b`, `a \|\| b` | Combine conditions. |

Details:

- **Variables** come from the resolver (the consuming project provides things such as `version` through its
  `ClassDeclarationResolver`), from `#set` lines in templates, and from `@HookLoadVariables` for hooks. An unset variable is
  false in every form, so `!name` is true for it.
- **Variable names** may contain only letters and underscores (a digit ends the name).
- **Version-like comparison:** values are split into number and text tokens and compared token by token, with numbers compared
  numerically: `1.9` < `1.10`, which a plain string comparison would get wrong ([`TextValueSequence`](text-value-sequence.md)).
- **Combining:** `&&` and `||` are evaluated strictly left to right with equal precedence. There are no parentheses; keep conditions
  simple or nest `#if` blocks.
- **Class names** in conditions (`exists`, `assignable`) must be fully qualified: conditions are evaluated without imports
  (see [Imports and Type Names](#imports-and-type-names)).
- **Legacy forms:** `classexists`, `methodexists` and `fieldexists` still parse (as synonyms of `exists`) but are no longer in
  use. Write `exists` instead.

```
#if version >= 1.18 && exists com.example.NewApi
    public int size:size();
#elseif version >= 1.9 && !legacy_mode
    public int size:b();
#else
    public int size:a();
#endif
#if assignable java.util.Collection java.util.List
    ...                                 // List is a Collection: true
#endif
```

Typical uses: choose member names per version (`#if version >= 1.18`), include code only when a library class exists
(`#if exists some.Library`), or keep a member only when a method exists in the environment.

## Directives (Macros)

Directives start a line with `#`. Preprocessing directives are resolved first and decide which text is kept.

### Conditionals (preprocessing)

- `#if <condition>` / `#elseif <condition>` / `#else` / `#endif`. Conditions are described in
  [Conditions](#conditions-expressions).
- Indentation of `#if`/`#elseif`/`#else`/`#endif` lines has no effect, and they work at the same indent level as
  the surrounding code. For clarity, still indent nested conditionals following the usual convention (typically
  2 spaces per level, as in `#if` inside `#if`), and keep the contained declarations at their normal indent.
- `#select <variable> <operator>` followed by `#case <value>: <text>` lines and `#case else: <text>` and
  ending with `#endselect`. Each `#case` becomes an `#if`/`#elseif` of the form `<variable> <operator> <value>`; the first match
  wins, so order cases from newest to oldest when using `>=`. Text after the `:` is the content of that case on the same line.
- `#if <condition>: <text>` keeps `<text>` on the same line when true.

```
#if version >= 1.17
  #if exists net.minecraft.core.Holder
    public Holder getHolder();
  #endif
#else
    public int getId();
#endif

#select version >=
#case 1.17: private int ticks:ac;
#case 1.9:  private int ticks:ab;
#case else: private int ticks:a;
#endselect

#if version >= 1.18: public int getSize();
```

### Balancing Conditionals

The Gradle plugin generates the `*Handle` classes **once**, with the variables configured for the build (for example a single
`version`), but the same templates are parsed at runtime on every supported environment. The generated handle API therefore
has to be the same whichever branch the generator happens to take. Rule: **every branch of a conditional must declare the same
set of exposed members** (same names, parameter and return types as seen through the handle), only differing in how they are
implemented or what real name they map to. A member declared in one branch only is missing from the generated handle when the
generator takes another branch, or missing at runtime on environments that take another branch.

When a member does not exist in some branch, still declare it there:

- with a method body supplying a default (do nothing, return a constant, or throw), which is preferred,
- or with a placeholder real name, `public int size:###();`, which declares the member while matching nothing
  (it then reports as missing on environments that take that branch, so only use it where that is acceptable).

```
#if version >= 1.18
    public static (CrashReportHandle) CrashReport create:forThrowable(Throwable throwable, String message);
#else
    public static (CrashReportHandle) CrashReport create:a(Throwable throwable, String message);   // same signature
#endif

#if version >= 1.9
    public boolean isGliding();
#else
    public boolean isGliding() { return false; }          // same member, default body where the real one is missing
#endif
```

The members must match as seen through the handle, so the **real** (internal) type may differ per branch as long as the **exposed**
type is identical. The [conversion system](overview.md) converts between them, so a cast hides the difference. In the example below
the handle always exposes `Holder<MobEffectHandle>`, while the real field is a `Holder<MobEffect>` or a plain `MobEffect`
depending on the version (adapted from BKCommonLib's `mobeffect.txt`, with a closing `#else` so that every branch declares the member):

```
#if version >= 1.20.5
    private final (Holder<MobEffectHandle>) net.minecraft.core.Holder<MobEffect> effectList:effect;
#else
    private final (Holder<MobEffectHandle>) MobEffect effectList:effect;
#endif
```

Without a cast the real type is the exposed type, so it must then be the same in every branch.

Check when adding a branch: does every other branch declare the same members, including the `#else`? Conditions that only
guard a `#require` or code inside one method body do not matter for this, since those are not part of the handle API.
The same applies to `#select` cases and to `#if exists` fallbacks.

### Other directives

| Directive | Purpose |
|---|---|
| `#include path` | Insert another template file. A path starting with `/` or `.` is relative to the current file, otherwise relative to the root source directory (or classpath resource when no directory exists). Requirements, remappings and variables carry over; imports and package do not. |
| `#set name value` | Assign a variable for later `#if`/`#select` use. |
| `#require Class <member declaration>` | Declare a member of `Class` usable in method bodies via `#name`. Often has different declarations per version, selected by conditionals. Parsed late, so it may use the file's imports. |
| `#remap Class <member declaration>` | Declare a name/type remapping rule for a member, applied everywhere including bodies. Not supported for members with bodies. |
| `#bootstrap code` | Code (single line or a `{ }` block) run once, when the Template Handle class is first used (during `Template` initialization, after the handle converter is registered and before the declared fields and methods are initialized). Only runs if the template is valid. |
| `#resolver <path>` | Static field or method that supplies the class declaration resolver (the way templates for a class are first loaded). |
| `#warning text` / `#error text` | Log a message while parsing. |
| `#setpath path` | Internal: sets the current file path (written by `#include`). Not for hand use. |

```
#resolver com.example.Common.TEMPLATE_RESOLVER      // first line of the root file
#include /net/minecraft/entity/entity.txt           // relative to the source root
#include ./helpers.txt                              // relative to this file

#if version >= 1.20.5
    #set storage array                              // later: #if storage == array
#else
    #set storage map
#endif

#require Entity private int tickCount:ah;
#remap net.minecraft.world.phys.Vec3 public final double x:a;   // applies everywhere, also in bodies
#bootstrap com.example.Bootstrap.init();            // inside a class body, runs on first use
#warning Class was not found, errors may occur
```

## Embedded in Java Annotations

Without a template file, the same syntax is placed in annotations on a handle or class extending
`Template.Class`/`Template.Handle`:

- `@Template.InstanceType("full.Name")`: the environment class.
- `@Template.Generated("declaration")`: a member declaration (or full method with body) for the abstract
  method it annotates, using the syntax above (including aliases, casts and `#require` usage).
- `@Template.Import`, `@Template.Package`, `@Template.Require`: the equivalents of `import`, `package` and `#require`.
- `@Template.Optional`, `@Template.Readonly`: the equivalents of those modifiers.

Because Java 8 lacks text blocks, long bodies are written as a block comment labelled with a `<NAME>` line, then
referenced by `"%NAME%"` in the annotation. The Gradle plugin's annotation remapper substitutes it after compilation
(see [overview.md](overview.md#gradle-plugin)). The comment text is the template code; the leading `*` of each line is not
part of it.

```java
@Template.InstanceType("net.minecraft.world.entity.Entity")
@Template.Import("net.minecraft.world.entity.Entity")
public static abstract class EntityLogic extends Template.Class<Template.Handle> {
    /*
     * <GET_TICKS>
     * public static int getTicks(Entity entity) {
     *     #require Entity protected int tickCount:ah;
     *     return entity#tickCount;
     * }
     */
    @Template.Generated("%GET_TICKS%")
    public abstract int getTicks(Object entity);

    @Template.Generated("public static String mapClassName(String className)")   // short declarations can be inline
    public abstract String mapClassName(String className);
}
```

The class is instantiated at runtime with `Template.Class.create`. This is a hand-made class, so it is typically held in a
static field (BKCommonLib: `JOMLConversion`):

```java
private static final EntityLogic LOGIC = Template.Class.create(EntityLogic.class);                  // annotations only
private static final EntityLogic LOGIC2 = Template.Class.create(EntityLogic.class, Common.TEMPLATE_RESOLVER);  // with a resolver

int ticks = LOGIC.getTicks(rawEntity);
```

The optional second argument is a `ClassDeclarationResolver`. Without it, only the annotations are used, and
variables (such as `version`) come from the globally registered `Resolver` logic. With it, the resolver:

- supplies the variables used by `#if` conditions in the annotation code (`resolveClassVariables`);
- can supply the full template `ClassDeclaration` of the instance type (`resolveClassDeclaration`), so members declared
  in the project's template files are known to the class;
- can supply the root `ClassResolver` (`getRootClassResolver`), so global `#remap` rules also apply to the annotation code.

BKCommonLib passes its constant `Common.TEMPLATE_RESOLVER` (the same resolver named by `#resolver` in `init.txt`) when the code needs
to be version-aware or use those shared rules, and omits it for self-contained code like `JOMLConversion`. The class is
generated on first use, so an invalid declaration is reported then, not when the Java class is compiled.

See also [the walkthrough](examples/template-walkthrough.md#5-template-code-embedded-in-java).

## What Gets Generated

For each class declaration the Gradle plugin writes a `*Handle` class (extends `Template.Handle`, or the base class'
handle) with:

- a `static final T` member: a `Template.Class` subclass holding one element per declared member
  (`Template.Method`, `Template.Field`, `Template.StaticMethod`, `Template.Constructor`, ...,
  using `.Converted` variants when casts are involved). Use `T` to access members without a handle instance.
- `createHandle(Object)`: wraps a raw instance; and `createNew(...)` for constructors.
- abstract getters/setters/methods for each exposed member, implemented at runtime by `TemplateHandleBuilder`.
- Getter naming: `getX` / `setX`, and `isX` for boolean fields (a field already starting with `is` keeps its name);
  `readonly` fields have no setter; `unknown` members are omitted; `optional` members are only on `T`, with no method on the handle itself.
- the `<code>` block contents, copied into the generated handle source exactly as written, including comments and
  Javadoc (so Javadoc written there appears on the generated class). `import x.y.Z;` lines inside the block are moved to the top of the file (see `<code>` Blocks).

## Diagnosing Missing Members

When a declared field, method or constructor does not match anything in the real class, Mountiplex logs
warnings through `MountiplexUtil.LOGGER` (`java.util.logging`, level `WARNING`) that list the closest real members
("alternatives"), most similar first and capped at 8. Use them to find the renamed or changed member.
Output is only produced when the resolver has error logging enabled (`ClassResolver.getLogErrors()`).
Alternatives are sorted by `Declaration.sortSimilarity`, and logged by `FieldLCSResolver.logAlternatives`.

A normal member in a class body:

```
WARNING: A class member of java.lang.StringBuilder was not found!
WARNING: Failed to find method public int lengthh(); - Alternatives:
WARNING:   - public int length();
WARNING:   - public int capacity();
WARNING:   - public native int hashCode();
WARNING:   - public boolean isEmpty();
...
```

A `#require` (note the different first line, and that the line is printed once per failed requirement):

```
WARNING: Requirement was not found in java.lang.StringBuilder:
WARNING: Failed to find field public int countt; - Alternatives:
WARNING:   - int count;
WARNING:   - byte coder;
WARNING:   - boolean maybeLatin1;
WARNING:   - byte[] value;
```

Notes:

- The word after "Failed to find" is `field`, `method` or `constructor`. Static members are only compared with
  static ones, instance members with instance ones. Alternatives are shown as the real declaration, so the
  real (possibly obfuscated or renamed) name and signature can be copied straight into the template.
- With nothing to compare against the line ends with `(No alternatives)` instead.
- Fields are matched together by `FieldLCSResolver` (longest common subsequence over field order),
  so when one field is missing, the listed alternatives are the real fields left unmatched around it.
- Other messages: `Requirement declaration could not be resolved for: <class>` (a type in the requirement
  could not be loaded, followed by a `Declaration:` line), `Remapping declaration not found!` for `#remap`,
  and at runtime `Field '<decl>' in class <name> not found; no alternatives available. (Removed?)` from `ClassTemplate`.
- Members marked `optional` do not produce these warnings.

Source of truth for the language: parsers in `reflection/declarations/parsers/` (directives),
`SourcePreprocessor` (conditionals), `ClassResolver` (expressions), and the `*Declaration` classes in
`reflection/declarations/` (members, modifiers, names, types). Output generation: `TemplateGenerator`.
Tests that show the syntax: `ClassDeclarationTest`, `MethodDeclarationTest`, `FieldDeclarationTest`, `SourceDeclarationTest` in
`src/test/java/`.
