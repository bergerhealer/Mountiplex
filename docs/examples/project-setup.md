# Project Setup Example

How a project that uses Mountiplex wires everything together: the Gradle plugin that generates handles, the template
files, a `ClassDeclarationResolver` that loads them at runtime with a `version` variable, and a basic remapper.
It follows the setup of [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) (`build.gradle.kts`,
`TemplateResolver`, `CommonBootstrap`), reduced to the essentials. Background: [overview.md](../overview.md),
[template-syntax.md](../template-syntax.md).

The Java and template parts of this page were run against Mountiplex (a `Greeter` class, version `2` selected the
`a()` member, version `1` the `b()` member, and the remapper turned `greet` into `hello`). The Gradle part is taken from
BKCommonLib's configuration and was not run separately.

## Why BKCommonLib Uses All of This

[BKCommonLib](https://github.com/bergerhealer/BKCommonLib) is a library for Minecraft server plugins (Bukkit, Spigot, Paper and
forks). Its plugins have to run on **many Minecraft versions and server implementations with a single build**, but the server
internals they need change constantly: classes and methods are renamed, moved, merged or removed between releases, some servers
ship obfuscated names and others (Forge-based hybrids) translate names themselves, and parts of the internals are private. Calling
that code directly would tie a build to one version, and plain reflection would scatter string names through the code and fail
silently when a name changes.

Each mechanism on this page answers one part of that problem:

| Problem | Mechanism | Where |
|---|---|---|
| Internal members differ per version, and a single source tree must cover all of them | **Templates** with `#if version` conditions, declaring each member once with its per-version name | [section 2](#2-template-files) |
| Plugin code needs a stable API that compiles without the server internals | **Generated `*Handle` classes**, created at build time from the templates, so code compiles against a fixed API | [section 1](#1-gradle-configuration) |
| The handles must exist for every version, while being generated for only one | Declaring the **same members in every branch**, which is the rule in [Balancing Conditionals](../template-syntax.md#balancing-conditionals) | [section 2](#2-template-files) |
| Templates must be checked against the real server when the plugin runs | A **runtime resolver** that parses the templates with the actual `version` variable and supplies the declarations | [section 3](#3-the-resolver) |
| Names differ per server (obfuscated, renamed, relocated) | **Name resolvers** that translate template names into real names | [section 4](#4-a-basic-remapper) |
| Whole classes were renamed or moved between versions | **Version-gated class name remappings**, so templates are written once against the newest names | [section 5](#5-version-gated-class-name-remappings) |
| Everything must be in place before anything uses it | A fixed **bootstrapping order** | [section 6](#6-bootstrapping-order) |

Following the same structure in another project gives the same benefits: one set of templates, type-safe code, and one place per
version difference. A project that only supports one environment version needs far less of it (often just the Gradle plugin and a
few templates), so treat the sections below as a menu, with the BKCommonLib setup as the full-scale example. The user-facing
mechanisms are described in [overview.md](../overview.md).

## What You Need

| Piece | Purpose |
|---|---|
| Gradle plugin `com.bergerkiller.mountiplex` | Generates `*Handle` classes from templates at build time and rewrites `%NAME%` annotation strings. |
| Mountiplex library dependency | Runtime: parsing, resolving, handle and class generation. Same version as the plugin. |
| Template files | The declarations (`init.txt` as root), packaged as resources so they can be loaded at runtime. |
| A `ClassDeclarationResolver` | Loads the templates at runtime, supplies variables such as `version`, and hands class declarations to Mountiplex. |
| Optional name resolvers | Translate names for the current environment (the "remapper"). |

## 1. Gradle Configuration

`settings.gradle.kts`: make the plugin resolvable (BKCommonLib also lists its own Maven server and `mavenLocal` for
development builds of Mountiplex):

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://ci.mg-dev.eu/plugin/repository/everything/")
    }
}
```

`build.gradle.kts`:

```kotlin
plugins {
    id("java-library")
    id("com.bergerkiller.mountiplex") version "3.43"        // BKCommonLib: version catalog alias
}

repositories {
    mavenCentral()
    maven("https://ci.mg-dev.eu/plugin/repository/everything/")
}

dependencies {
    // Same version as the plugin. BKCommonLib shades and relocates this and its dependencies.
    implementation("com.bergerkiller.mountiplex:Mountiplex:3.43")
}

mountiplex {
    generateTemplateHandles()      // adds the generated sources directory to every source set
    remapAnnotationStrings()       // only needed when using "%NAME%" annotation strings
}

tasks {
    generateTemplateHandles {
        source.set("com/example/templates/init.txt")        // root template, relative to src/main/templates
        target.set("com/example/generated")                  // package root of the generated handles
        variables.put("version", "1.21.11")                  // build-time variables used by #if in templates
    }

    processResources {
        from("src/main/templates")                           // package the templates so they can be loaded at runtime
    }

    withType<JavaCompile>().configureEach {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}
```

Notes (from `MountiplexPlugin`, `MountiplexExtension` and `GenerateTemplateHandles` in `mountiplex-gradle-plugin/`):

- Defaults: templates are read from `src/main/templates`, handles are written to `src/main/generated`. Both can be
  changed on the task (`sourceDirectory`, `outputDirectory`). The generated directory is added to the source sets by
  `generateTemplateHandles()`, so it compiles with the rest. Whether to commit it is a project choice (BKCommonLib does).
- `source` is a path relative to the template source directory. It is the first file parsed and normally `#include`s the others.
- `variables` are used only while generating (evaluating `#if` and `#select`). Because of this the generated API depends on the branch
  taken, so templates must declare the same members in every branch ([Balancing Conditionals](../template-syntax.md#balancing-conditionals)). The runtime resolver below supplies its own
  variables, so keep the two consistent. BKCommonLib generates with one fixed Minecraft version, and resolves per server at runtime.
- `remapAnnotationStrings()` changes where `compileJava` writes classes (`build/mountiplex-classes/<sourceSet>`) and runs a task
  afterwards that rewrites `%NAME%` strings in the compiled bytecode. Anything reading the compiled classes
  must use that output (the plugin wires the source sets for you).
- Handle generation runs before compilation as `generateTemplateHandles`. Run it manually to regenerate.

Layout:

```
src/main/templates/com/example/templates/init.txt
src/main/templates/com/example/templates/greeter.txt
src/main/generated/com/example/generated/...         (output)
src/main/java/com/example/...
```

## 2. Template Files

`init.txt` (root). `#resolver` names the static field that supplies the resolver used by the generated handles, so it must
match the Java class below:

```
#resolver com.example.ExampleTemplates.RESOLVER

#include com/example/templates/greeter.txt
```

`greeter.txt`:

```
package com.example;

class Greeter {
    public String greet();                 // resolved by the remapper below

#if version >= 2
    public String hello:a();               // `version` comes from the resolver
#else
    public String hello:b();
#endif
}
```

Include paths: a path without a leading `/` or `.` is relative to the template root (the source directory when generating, the
classpath root when loading at runtime), so write the full path from the root, as above. A path starting with `/` or `.`
is instead relative to the folder of the including file. (Verified: `/pkg/file.txt` written inside `pkg/init.txt` looks for
`pkg/pkg/file.txt`.)

## 3. The Resolver

One class holds the loaded declarations and the variables. Static field so `#resolver` can reference it:

```java
package com.example;

public final class ExampleTemplates implements ClassDeclarationResolver {
    public static final ExampleTemplates RESOLVER = new ExampleTemplates();

    private final Map<String, String> variables = new HashMap<String, String>();
    private final Map<Class<?>, ClassDeclaration> classes = new HashMap<Class<?>, ClassDeclaration>();
    private ClassResolver rootResolver = ClassResolver.DEFAULT;

    /** Parses all templates for the given version. Call once, after the name resolvers are registered. */
    public void load(String version) {
        this.variables.put("version", version);              // the variable used by '#if version >= 2'

        SourceDeclaration source = SourceDeclaration.parseFromResources(
                ExampleTemplates.class.getClassLoader(),
                "com/example/templates/init.txt",            // classpath resource, no leading slash
                this.variables);
        this.rootResolver = source.getResolver();            // carries the global #remap rules
        for (ClassDeclaration dec : source.classes) {
            register(dec);
        }
    }

    private void register(ClassDeclaration dec) {
        this.classes.put(dec.type.type, dec);
        for (ClassDeclaration sub : dec.subclasses) {        // nested classes
            register(sub);
        }
    }

    @Override
    public ClassDeclaration resolveClassDeclaration(String classPath, Class<?> classType) {
        return this.classes.get(classType);                  // null when there is no template for the class
    }

    @Override
    public void resolveClassVariables(String classPath, Class<?> classType, Map<String, String> variables) {
        variables.putAll(this.variables);                    // used for #if in annotation code and class hooks
    }

    @Override
    public ClassResolver getRootClassResolver(String classPath, Class<?> classType) {
        return this.rootResolver;
    }
}
```

The two pieces that make `version` available: the map passed to `parseFromResources` (templates in files), and
`resolveClassVariables` (annotation-embedded templates and `@HookLoadVariables`). Set both from the same map.
BKCommonLib fills it through `CommonServer.addVariables`, which puts `version` (for example `1.21.11`), and `pre` for pre-releases.
Values are compared version-aware (`1.9` < `1.10`), see [Conditions](../template-syntax.md#conditions-expressions).

## 4. A Basic Remapper

A name resolver translates names declared in templates into the real names of the running environment. Register it **before**
loading the templates:

```java
Resolver.registerMethodResolver(new MethodNameResolver() {
    @Override
    public String resolveMethodName(Class<?> declaringClass, String methodName, Class<?>[] parameterTypes) {
        if (declaringClass == Greeter.class && methodName.equals("greet")) {
            return "hello";                                   // template says greet(), the real method is hello()
        }
        return methodName;                                    // must return the input when not resolved
    }
});
```

With this, the declaration `public String greet();` resolves to `Greeter.hello()`, and logs/prints as `greet:hello` (the
[alias form](../template-syntax.md#name-aliases-aliasrealname)). The equivalents for fields and classes are
`FieldNameResolver` (`Resolver.registerFieldResolver`) and `ClassPathResolver` (`Resolver.registerClassResolver`, which can also
veto reading `.class` files with `canLoadClassPath`, see [the internals page](../internals/remapping-and-javassist.md)).
BKCommonLib implements all of these in its server classes and registers them in `CommonBootstrap`. For simple renames of one
class, a `#remap` in the templates is often easier than a Java resolver.

## 5. Version-Gated Class Name Remappings

A core feature BKCommonLib relies on: templates and code are written against the **newest** class names, and on older
environments a table of class name remappings translates those names to what exists there. The table is built once in
`CommonBootstrap.initResolvers` as a `Map<String, String>` (name as written in templates and code to real class name) with
the version checks done in plain Java, then registered as a class path resolver:

```java
Map<String, String> remappings = new HashMap<String, String>();

// evaluateMCVersion is BKCommonLib's helper: compares the running server version
// Names are written as the newest version knows them; older versions map them back
if (evaluateMCVersion("<", "1.21.11")) {
    remappings.put("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation");
    remappings.put("net.minecraft.util.Util", "net.minecraft.Util");
}
if (evaluateMCVersion("<", "26.2")) {
    remappings.put("net.minecraft.world.entity.EntityTypes", "net.minecraft.world.entity.EntityType");
}

// A ClassPathResolver is a single-method interface, so a lambda is enough
Resolver.registerClassResolver(classPath -> {
    String remapped = remappings.get(classPath);
    return (remapped != null) ? remapped : classPath;      // must return the input when not remapped
});
```

BKCommonLib groups the entries in blocks per version ("Mojang remapping changes for 1.21.11" and so on, newest first), each acting
as a migration of one release to the next, and adds some static ones for its own proxy classes (a class that does not exist on
a version is mapped to a replacement class shipped with the library). On its `CraftBukkitServer` implementation the table is passed
to `setEarlyRemappings` instead, so it is applied before the server's own Mojang to Spigot translation. Other servers get the resolver
shown above.

This is why template files can be written once against the newest names, and why `#if version` is then only needed for
real differences in members, not for renamed classes. Class names are translated by the registered class resolvers
(`Resolver.resolveClassPath`), so this applies wherever Mountiplex looks a class up by name.

**Set this up as early as possible.** Register the remappings, and every other name resolver, **before** any template is loaded and
before any converter is registered. Loading a template, creating a handle or registering a converter looks up classes by name, and a lookup
that happens before the remapping is registered can fail or resolve the wrong class, with the result possibly kept for later use. BKCommonLib enforces this
by doing it in the server detection step, before `initTemplates()`, and sets a flag (`WARN_WHEN_INIT_TEMPLATES`) that logs a stack trace
when a template is initialized while the resolvers are still being set up.

## 6. Bootstrapping Order

```java
public static void initTemplates(String serverVersion) {
    // 1. Name resolvers first, including the version-gated class remappings of the previous section.
    //    Nothing may load templates or register converters before this is done.
    Resolver.registerMethodResolver(/* ... */);
    Resolver.registerFieldResolver(/* ... */);
    Resolver.registerClassResolver(/* ... */);

    // 2. Parse the templates (declares the version variable)
    ExampleTemplates.RESOLVER.load(serverVersion);

    // 3. Make the declarations available to Mountiplex (Resolver.resolveClassDeclaration etc.)
    Resolver.registerClassDeclarationResolver(ExampleTemplates.RESOLVER);
}
```

BKCommonLib's `CommonBootstrap.initTemplates` does exactly this order (with a comment that the class path resolvers must be registered
before the load), and also registers an unloader (`MountiplexUtil.registerUnloader`) that drops the declarations when the plugin is
disabled.

Generated handles trigger the whole chain by themselves: their `T` member is created with the resolver named by `#resolver`, e.g.
`Template.Class.create(GreeterClass.class, com.example.ExampleTemplates.RESOLVER)`. Make sure the bootstrapping above has run
before the first handle is used, since `RESOLVER.load(...)` is not called by the handle.

## 7. Verifying

- Run `./gradlew generateTemplateHandles` and check `src/main/generated`. Warnings during generation list members that were not found,
  with alternatives (see [Diagnosing Missing Members](../template-syntax.md#diagnosing-missing-members)).
- At runtime, `Resolver.resolveClassDeclaration(Greeter.class)` returns the declaration and each member reports `isResolved()`. A member
  that matched has its real `method` or `field` set.
