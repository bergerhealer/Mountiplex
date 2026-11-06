# ClassHook

Reference for hooking methods by extending (subclassing) existing classes with `ClassInterceptor` and `ClassHook`
(`reflection/`). Read this page before reading, writing or changing any class that **extends `ClassHook`**
(recognizable by `@ClassHook.HookMethod(...)` annotations and uses of a `base` field).
See [overview.md](overview.md#class-generation-handles-and-hooking) for where hooking fits in the project.

## Purpose

Create a generated subclass of an environment class, override some of its methods with methods of your own, and
still be able to call the original method. Used when the implementation must be extended with different behaviour
rather than only read or called.

**Existing classes are never altered.** No bytecode of the original class is changed, patched or redefined, and
no instrumentation or agent is used. Hooking only generates a new subclass; only instances of that subclass behave
differently. Existing objects keep their behaviour unless they are replaced by a hooked copy.

## Key Files

| File | Role |
|---|---|
| `reflection/ClassInterceptor.java` | Callback-driven base: decides per method which `Invoker` callback to run, creates and manages hooked instances. |
| `reflection/ClassHook.java` | Annotation-based layer on top: finds `@HookMethod` methods, matches them to real methods and provides `base`. |
| `reflection/util/fast/GeneratedHook.java` | Generates the subclass and the invokers that call the original (super) method. |
| `reflection/util/ExtendedClassWriter.java` | Underlying class generation (see [overview.md](overview.md)). |
| `src/test/java/.../ClassHookTest.java` and `types/*Hook.java` | Working examples (`Dog*Hook`, `RemappedTestObjectHook`). |

## Writing a Hook

1. Create a class that extends `ClassHook<Self>`, where the type parameter is the hook class itself
   (`class DogHook extends ClassHook<DogHook>`). This self-referential generic is known as the *curiously recurring template
   pattern* (CRTP), or *F-bounded polymorphism*. It is required: it makes the type of the inherited `base` field the hook class
   itself, so every hook method you define also exists on `base`. Calling `base.theWoofMethod()` then invokes the
   original (super) method that the hook method replaces. With a different type parameter, `base` would not have your hook
   methods and the original could not be called.
2. For each method to override, declare a method in your class, annotated with
   `@HookMethod("<declaration>")`. The declaration uses the same member syntax as
   [template-syntax.md](template-syntax.md) (modifiers, return type, name, parameter types) and must match the
   real method's signature. Your method must be callable with the same arguments and return a compatible value.
3. Inside the hook method, `base.<method>(...)` calls the original method (the `base` field has the hook's own type, so
   you call your hook method name on it, not the real name). Not calling `base` fully replaces the behaviour.
4. `instance()` (protected) returns the object currently being handled. It is tracked per thread (a `ThreadLocal`), so a hook shared
   by several threads still sees the right object in each call; no extra synchronization is needed for this.
5. Optional attributes: `@HookMethod(value=..., optional=true)` suppresses the warning when the method does
   not exist in the hooked class.

Hooked classes are also given a version-specific resolver context through these annotations (class or method level):

| Annotation | Effect |
|---|---|
| `@HookPackage("a.b")` | Package used to resolve simple class names in declarations. |
| `@HookImport("a.b.Name")` | Import used to resolve names (repeatable). On the class it applies to all methods. |
| `@HookLoadVariables("<expression>")` | Java expression returning a `ClassDeclarationResolver`. Supplies variables (such as the version) and the `#remap` rules that were loaded for it. |
| `@HookMethodCondition("<expr>")` | Hook method is only active if the condition evaluates true, using the variables above. The expression is the same as after `#if` in templates, see [Conditions](template-syntax.md#conditions-expressions) (variable comparisons such as a version check, `exists`, `assignable`, `&&`, `\|\|`). |

These feed the same [`ClassResolver`](overview.md#template-context-classresolver) used for templates.

## Using a Hook

- `hook(obj)` creates a hooked copy of an existing object (the object's fields are copied over) and returns it. Use the return value; the
  original is not modified.
- `createInstance(type)` creates a hooked instance without calling a constructor.
  `constructInstance(type, paramTypes, params)` calls a constructor.
- `ClassInterceptor.unhook(obj)` converts back to the base type, copying state.
- `ClassInterceptor.get(obj, hookType)` returns the hook that owns a hooked object, or null.
- `mock(obj)` makes the hook usable for an object without actually intercepting its methods.
- Declarations are parsed and checked only when an object of a type is actually hooked (the first time that type is hooked, when its
  class is known and the hook class is generated). Nothing is verified when the hook class is defined or constructed.
  Methods that cannot be found are logged as warnings in `onClassGenerated` unless the hook is `optional`.
- `ClassInterceptor` can be used directly when annotation matching is not wanted: implement `getCallback(Method)` and
  return an `Invoker` or null. `getCallback` must give the same result for the same method across all instances unless
  `setUseGlobalCallbacks(false)` is set.
- `invokeSuperMethod(method, instance, args)` calls the original method.

## Examples

Replace a method, and a variant that wraps the original (adapted from `ClassHookTest`, with a `Dog` class that has
`public String woof()`):

```java
public class DogHook extends ClassHook<DogHook> {   // the type argument is the class itself, so 'base' is a DogHook
    @HookMethod("public String woof()")
    public String theWoofMethod() {
        return "DogHook::theWoofMethod()";           // replaces the behaviour
    }
}

public class DogSuperHook extends ClassHook<DogSuperHook> {
    @HookMethod("public String woof()")
    public String theWoofMethod() {
        return "wrapped: " + base.theWoofMethod();   // base calls the original woof()
    }
}

Dog dog = new Dog();
DogSuperHook hook = new DogSuperHook();
Dog hooked = hook.hook(dog);                         // a copy; keep using 'hooked', not 'dog'
hooked.woof();                                       // "wrapped: Dog::woof()"
```

Version-aware hook with imports, a resolver and a condition (adapted from BKCommonLib's `IInventoryProxyHook`, which
redirects an internal inventory interface to a Bukkit inventory):

```java
@ClassHook.HookPackage("net.minecraft.server")                      // simple names resolve in this package
@ClassHook.HookImport("net.minecraft.world.item.ItemStack")
@ClassHook.HookLoadVariables("com.bergerkiller.bukkit.common.Common.TEMPLATE_RESOLVER")
public class IInventoryProxyHook extends ClassHook<IInventoryProxyHook> {
    private final Inventory inventory;                              // normal state in the hook object

    public IInventoryProxyHook(Inventory inventory) {
        this.inventory = inventory;
    }

    @HookMethod("public abstract int getMaxStackSize()")
    public int getMaxStackSize() {
        return this.inventory.getMaxStackSize();                    // no base call: fully replaced
    }

    @HookMethodCondition("version >= 1.11")                         // only one of these two is active
    @HookMethod("public abstract List<ItemStack> getContents()")
    public List<?> getContents() { ... }

    @HookMethodCondition("version <= 1.10.2")
    @HookMethod("public abstract ItemStack[] getContents()")
    public Object[] getContents_old() { ... }
}
```

When there is no existing object to copy, `createInstance(type)` makes a new hooked instance of the type without calling a
constructor: `Object proxy = new IInventoryProxyHook(inventory).createInstance(ContainerHandle.T.getType());`.
Hooking an existing object uses `hook(obj)` as above.

## The `name:???` Convention (legacy)

In `@HookMethod` declarations you will often see names written like `getSize:???` or `trackEntity:???`.

- This is an alias-only name: the readable name before the colon, and `???` where the real name would be.
  It means "match whatever method the resolvers know under this alias".
- Matching works through the resolver mechanism: the hooked class's methods are queried through
  `Resolver.resolveMethodAlias`, which asks the globally registered resolvers (and, when `@HookLoadVariables` is used,
  the `#remap` rules of that class declaration resolver). A declaration like `getName:???` therefore matches a method
  whose real (possibly obfuscated) name was remapped to alias `getName`. The matching logic is
  `NameDeclaration.isAliasOnly` / `match`, used from `ClassHook.getCallback`.
- This is a mechanism being phased out. **Do not use `:???` in new hooks.** Use the real method name instead, and if
  it differs per version, split into version-specific hooks or use `@HookMethodCondition`.
- It occurs only in code that extends `ClassHook` (and some legacy code). It does **not** appear in template code.
  Template code uses `name:###` as a placeholder real name for a member that does not exist, which is a different
  convention (see [template-syntax.md](template-syntax.md#name-aliases-aliasrealname)).

## Gotchas

- **Replace every reference to the original object.** Hooking creates a copy; the pre-hook object stays alive and unhooked.
  If anything still references it (fields, collections, caches, other objects), it keeps running the original behaviour
  and its state diverges from the hooked copy, which can propagate and corrupt data badly. After `hook(obj)`, replace
  all references to `obj` with the returned object, and likewise after `unhook`. To find leftover references, BKCommonLib's
  `DebugUtil.logInstances(...)` / `logInstancesMatching(...)` (`com.bergerkiller.bukkit.common.utils.DebugUtil`) walk the object graph from a
  starting object or class (the whole server by default) and log every location holding the instance. It is very slow:
  use it only for debugging, never in production.
- **No static analysis.** Because declarations are only parsed and matched when a real object is hooked, a typo, wrong import or
  missing method is not noticed at compile time, at class load or at hook construction. It only shows up (as a logged warning)
  when the first object of that type is hooked, and only for the types that get hooked. Test every hook against every
  environment it must support.
- Hook method signatures are text; a mismatch means the method silently is not hooked (only a logged warning).
  When hooking fails, check the declaration, the imports and the variables resolver first.
- `instance()` returns the object being handled by the current thread. Outside a callback it falls back to the last hooked object
  (or throws if none exists), so only rely on it inside hook methods. `mock` supports one object at a time per interceptor.
- Generated classes are created per hooked class and per class loader; the hook class loader can be set with the
  constructor argument `ClassLoader hookClassLoader` or by overriding `getHookClassLoader()`.
