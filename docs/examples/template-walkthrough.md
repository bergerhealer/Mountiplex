# Template Walkthrough (Examples)

Larger examples for [template-syntax.md](../template-syntax.md). Each section there has short snippets; this page
shows complete templates and what is generated from them. The first example is artificial (it targets
`java.lang.String` so it can be tried anywhere), the others are taken from [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)
and show real conventions. Paths are relative to `BKCommonLib/src/main/templates/com/bergerkiller/templates/`
unless noted.

## 1. A Complete Artificial Template

```
package java.lang;

import java.util.Comparator;

class String {
    // Plain members: the shape matches the real declaration
    public int length();
    public static String valueOf(int value);
    public String(char[] chars);
    public static final Comparator CASE_INSENSITIVE_ORDER;

    // Alias: callers see trimmed(), the real method is trim()
    public String trimmed:trim();

    // Use the real member where it exists, otherwise supply a body
#if exists java.lang.String public boolean isBlank();
    public boolean isBlank();
#else
    public boolean isBlank() {
        return instance.trim().length() == 0;
    }
#endif

    // A requirement used in a body, with the result stored in a typed variable
    public int doubleLength() {
        #require java.lang.String public int lengthOf:length();
        int len = instance#lengthOf();
        return len * 2;
    }

    // Constructor required through <init>, called like a static method
    public String makeCopy() {
        #require java.lang.String public String copyOf:<init>(char[] chars);
        char[] chars = instance.toCharArray();
        String copy = #copyOf(chars);
        return copy;
    }
}
```

What to notice:

- `instance` is the wrapped `String` in non-static bodies; there is no `instance` in static ones.
- The `exists` line contains a fully qualified class, because conditions run without imports.
- `isBlank()` is either the real method or a generated body depending on the environment; the handle API is identical.
- Parsing this (with `SourceDeclaration.parse`) resolves every member without warnings.

## 2. A Real Small Template and Its Generated Handle

Template (`net/minecraft/crashreport.txt`, abbreviated):

```
package net.minecraft;

import com.bergerkiller.generated.net.minecraft.CrashReportHandle;
import com.bergerkiller.generated.net.minecraft.CrashReportCategoryHandle;

class CrashReport {
#if version >= 1.18
    public static (CrashReportHandle) CrashReport create:forThrowable(Throwable throwable, String message);
    public (CrashReportCategoryHandle) CrashReportCategory getSystemDetails:addCategory(String message);
#else
    public static (CrashReportHandle) CrashReport create:a(Throwable throwable, String message);
    public (CrashReportCategoryHandle) CrashReportCategory getSystemDetails:a(String message);
#endif
}

class CrashReportCategory {
}
```

Generated `CrashReportHandle` (in `src/main/generated/`, abbreviated):

```java
/**
 * Instance wrapper handle for type <b>net.minecraft.CrashReport</b>.
 * ...
 */
@Template.InstanceType("net.minecraft.CrashReport")
public abstract class CrashReportHandle extends Template.Handle {
    public static final CrashReportClass T = Template.Class.create(CrashReportClass.class, ...TEMPLATE_RESOLVER);

    public static CrashReportHandle createHandle(Object handleInstance) { return T.createHandle(handleInstance); }

    public static CrashReportHandle create(Throwable throwable, String message) {
        return T.create.invoke(throwable, message);
    }

    public abstract CrashReportCategoryHandle getSystemDetails(String message);

    public static final class CrashReportClass extends Template.Class<CrashReportHandle> {
        public final Template.StaticMethod.Converted<CrashReportHandle> create = ...;
        public final Template.Method.Converted<CrashReportCategoryHandle> getSystemDetails = ...;
    }
}
```

Mapping: the alias before the colon (`create`, `getSystemDetails`) became the handle method name, the real name
changes per version, and the `(Handle) RealType` casts became handle types in the signatures. Static methods are
implemented in the handle; instance methods are `abstract` and filled in at runtime. Members using casts
use the `.Converted` element types in `T`.

## 3. Version-Specific Names: `#select`, `#remap` and `###`

`#select` picks a name per version (`net/minecraft/entity/entity.txt`); order cases newest first, since the first
match wins:

```
#select version >=
#case 1.13.1: private (EntityHandle) Entity vehicle;
#case 1.13:   private (EntityHandle) Entity vehicle:ax;
#case 1.9.4:  private (EntityHandle) Entity vehicle:at;
#case else:   private (EntityHandle) Entity vehicle;
#endselect
```

`#remap` renames a member globally, including inside bodies, so each template does not repeat the rule
(`net/minecraft/remapping/vector_remappings.txt`):

```
#if version >= 1.9
    #remap net.minecraft.world.phys.Vec3 public final double x;
#else
    #remap net.minecraft.world.phys.Vec3 public final double x:a;
#endif
```

An optional member that has no real name on this version uses `###` (`net/minecraft/core_registry.txt`):

```
class MappedRegistry {
#if version >= 1.13
    protected optional final Map<V, K> opt_inverseLookupField:###;
#else
    protected optional final Map<V, K> opt_inverseLookupField:b;
#endif
```

## 4. Constructor Requirement, `<code>` Block and `#bootstrap`

A private class created through `<init>` and a `<code>` helper (`net/minecraft/network/datawatcher.txt`, abbreviated):

```
public static (SynchedEntityDataHandle) SynchedEntityData createNew((EntityHandle) Entity owner) {
#if version >= 1.20.5
    #require net.minecraft.network.syncher.SynchedEntityData SynchedEntityData createDataWatcher:<init>(SyncedDataHolder owner, SynchedEntityData.DataItem<?>[] items);

    return #createDataWatcher((SyncedDataHolder) owner, new SynchedEntityData$DataItem[0]);
#else
    return new SynchedEntityData(owner);
#endif
}

<code>
public static SynchedEntityDataHandle createNew(org.bukkit.entity.Entity owner) {
    return createHandle(T.createNew.raw.invoke(com.bergerkiller.bukkit.common.conversion.type.HandleConversion.toEntityHandle(owner)));
}
</code>
```

The `<code>` method is copied into `SynchedEntityDataHandle` as written, and uses fully qualified names because
those types are not otherwise imported by the generated file. Note `SynchedEntityData$DataItem` in the body: Javassist
needs the `$` form for nested classes.

`#bootstrap` runs code when the handle class is first used, for example to make sure the server is initialized
(`net/minecraft/core_particles.txt`):

```
public class ParticleType {
    #bootstrap com.bergerkiller.bukkit.common.internal.CommonBootstrap.initServer();
    ...
```

## 5. Template Code Embedded in Java

Instead of a `.txt` file, code can live in annotations. Block comments labelled `<NAME>` hold the template body and
the annotation refers to them with `%NAME%`
(`src/main/java/com/bergerkiller/bukkit/common/conversion/type/JOMLConversion.java`, abbreviated):

```java
@Template.Import("org.joml.Vector3f")
@Template.Import("org.bukkit.util.Vector")
public static abstract class ConversionLogic extends Template.Class<Template.Handle> {
    /*
     * <ENCODE_VECTOR3F_V>
     * public static Vector3f encode(Vector v) {
     *     return new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ());
     * }
     */
    @Template.Generated("%ENCODE_VECTOR3F_V%")
    public abstract Object encodeVector3f(Vector vector);
}
```

The class is instantiated by hand, with no generated `*Handle`. Keep the instance in a static field and call
the abstract methods on it:

```java
private static final ConversionLogic LOGIC = (JOML_VECTOR3F_TYPE != null)      // only create if the types exist
        ? Template.Class.create(ConversionLogic.class) : null;

public static Object fromVector(Vector vector) {
    return LOGIC.encodeVector3f(vector);
}
```

`JOMLConversion` passes no resolver, since its code needs nothing from the project's templates. Pass one as the second
argument when the code uses version variables, other `#remap` rules, or members declared in the project's templates:

```java
Template.Class.create(HandlerLogic.class, Common.TEMPLATE_RESOLVER);    // BKCommonLib's constant resolver
```

See [Embedded in Java Annotations](../template-syntax.md#embedded-in-java-annotations) for what the resolver provides.

At build time, the Gradle plugin replaces `%ENCODE_VECTOR3F_V%` with the comment text during compilation.

## 6. Include Structure

The root file lists everything (`init.txt`, abbreviated):

```
#resolver com.bergerkiller.bukkit.common.Common.TEMPLATE_RESOLVER

// Remappings available everywhere, including method bodies
#include /net/minecraft/remapping/vector_remappings.txt

// Global macros
#include /global/global.txt

#include /com/mojang/authlib/gameprofile.txt
```

Paths beginning with `/` or `.` are relative to the including file's folder, which here is the source root because `init.txt` sits at the top of the template tree. Included files inherit requirements, remappings
and variables, but need their own `package` and `import` lines.
