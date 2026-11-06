# Overview

Mountiplex is a Java library for safely accessing a hidden, frequently changing implementation
(an "environment" whose classes are unknown at compile time) without writing raw
`java.lang.reflect` code. This page describes the big picture and the components;
each component is expected to get its own page. Terms are defined in [glossary.md](glossary.md).

## Problem and Approach

Plain reflection is not type-safe: member references must be stored in fields throughout the
code, nothing is checked until the call is made, and any rename in the target breaks things
silently. Mountiplex addresses this by:

1. Describing the target's classes, fields, methods and constructors in text **templates**
   that are parsed and fully type-checked against the real classes.
2. Using **macros** in those templates so that one template can adapt to the environment
   (different versions, different names).
3. Allowing **inline method bodies** in templates for members that must be mocked or need
   extra logic, written against the environment.
4. **Converting** types between the hidden internals and a stable, project-defined API
   (wrapper classes) through a registry of converters, applied with a cast syntax in templates.
5. **Resolving** moved or renamed classes, fields and methods automatically, so a single
   template works across environments.
6. **Generating** fast access code at runtime (bytecode / compiled source) instead of using
   reflection per call.

Everything else in the repository supports this core.

## Read Before Changing Specific Code

Some code can only be understood with a dedicated page. Read the page first when you meet one of these:

### Template code: [template-syntax.md](template-syntax.md)

Read before interpreting, writing or changing template code. It is recognizable by:

- `.txt` files containing Java-like code with `#macros` that normal Java does not have (`#if`, `#require`,
  `#remap`, `#include`, `#select`...).
- Handle or class types extending `Template.Handle`/`Template.Class` whose annotations contain `"%NAME%"` strings,
  with a matching `<NAME>` block comment above them holding the template code.
- An abstract `*Handle` class with a static `T` field: these are generated from templates, so changes belong in the
  template. Reading or changing how such a handle behaves requires the syntax page.

### Type conversion: [conversion.md](conversion.md)

Read before reading, writing or changing code that uses `Conversion`, `Converter`, `@ConverterMethod`, or `(Type) RealType` casts in
templates (including `Template.*.Converted` members and `.Converted` element types in handles).

### Writing a class with ASM: [class-generation.md](class-generation.md)

Read before using `ExtendedClassWriter` or `MPLType`, or when code compiled against ASM fails to compile or to link at runtime (the ASM dependency and relocation requirements are on that page).

### Class hooks: [classhook.md](classhook.md)

Read before reading, writing or changing a class that extends `ClassHook`, recognizable by `@HookMethod`
annotations and a `base` field, or any code using `ClassInterceptor`.

## Components

| Component | Responsibility | Location |
|---|---|---|
| Declarations and template engine | Parse templates (macros, classes, members, types) into declaration objects, and build the runtime model | `reflection/declarations/` |
| Template runtime model | Runtime objects (`Template.Class`, `Handle`, fields, methods, constructors) created from declarations | `reflection/declarations/Template.java` and the `Template*Builder` classes |
| Macro preprocessing and parsers | Resolve `#if`/`#select`/`#set` and related directives; parse `#require`, `#remap`, `#include`, etc. | `SourcePreprocessor`, `reflection/declarations/parsers/` |
| Template context | `ClassResolver`: imports, packages, variables, requirements and remappings that templates are parsed in (see [Template Context](#template-context-classresolver)) | `reflection/declarations/ClassResolver.java` |
| Resolver system | Remap class, field and method names and class paths; supply templates for classes; custom classpath for code compilation (see [resolver.md](resolver.md)) | `reflection/resolver/` |
| Type conversion | Converter registry, converter types, built-in conversions, converting collection wrappers (see [conversion.md](conversion.md), [converting-collections.md](converting-collections.md)) | `conversion/` |
| Fast access and invocation | Generated or reflection-backed invokers, accessors, constructors, copiers (see [fast-access.md](fast-access.md)) | `reflection/util/fast/`, `reflection/util/Fast*.java` |
| Class generation | Runtime generation of classes (ASM plus Javassist), generator class loaders; the base of all other generation (see [class-generation.md](class-generation.md) and [Class Generation, Handles and Hooking](#class-generation-handles-and-hooking)) | `reflection/util/ExtendedClassWriter.java`, `reflection/util/GeneratorClassLoader.java`, `reflection/util/asm/` |
| Handle building | Compiles abstract `Template.Handle` (and `Template.Class`) types into runtime implementations | `reflection/declarations/TemplateHandleBuilder.java`, `TemplateClassBuilder.java` |
| Hooking and interception | Overriding methods in generated subclasses of existing classes (which are not altered), with calls to the original method | `reflection/ClassInterceptor.java`, `reflection/ClassHook.java`, `reflection/util/fast/GeneratedHook.java` |
| Legacy reflection wrappers | Deprecated: `ClassTemplate`, `FieldAccessor`/`MethodAccessor`, and the `Safe*` wrappers that predate templates. Not for new code; see [legacy-class-template.md](legacy-class-template.md) and prefer [fast-access.md](fast-access.md) | `reflection/Safe*.java`, `reflection/ClassTemplate.java`, `reflection/*Accessor.java` |
| Shared utilities | General helpers (see [utilities.md](utilities.md)), version-string comparison (see [text-value-sequence.md](text-value-sequence.md)), type maps (see [type-maps.md](type-maps.md)) | `MountiplexUtil.java`, `reflection/ReflectionUtil.java`, `logic/`, `reflection/util/` |
| Gradle plugin | Loads the template bundle and generates `*Handle` classes at build time; supports multi-line annotation strings on JDK 8 (see [Gradle Plugin](#gradle-plugin)) | `mountiplex-gradle-plugin/` |
| Tests | Unit tests per component | `src/test/java/` |

## How the Pieces Fit Together

1. A template text is loaded (from disk, resources or a string). The **preprocessor** resolves
   macro conditionals using variables supplied by the environment.
2. The **declaration parsers** turn the remaining text into `ClassDeclaration`, `FieldDeclaration`,
   `MethodDeclaration`, `ConstructorDeclaration` and `TypeDeclaration` objects. While doing so
   they consult the **Resolver** to map class paths and member names to what exists, and match
   declarations to real members.
3. The **template runtime** (`Template.Class`, `Template.Handle` and element types) binds each
   valid declaration to a real member. Handle implementations are generated by the builder
   classes using **code generation**. Members with inline bodies are compiled into generated code.
4. Calls through a handle use **fast invokers/accessors** and pass values through **converters**
   when a declaration uses a type cast, so callers only see the stable API types.
5. Optionally, the **Gradle plugin** parses the same templates at build time and generates the
   handle classes as Java source, so projects can compile against them.

## Template Context: ClassResolver

`ClassResolver` (`reflection/declarations/`) is the context every template is parsed and compiled
in. Every declaration (`Declaration` subclasses) holds one. It is not the same as the global
`Resolver` (`reflection/resolver/`), which remaps names across the whole environment;
a `ClassResolver` is per template/class and answers "what does this name mean here?".
It holds:

- **Class loader**: used to load all classes by name. Defaults to the library's loader.
- **Package and declared class**: `package` makes every class in a package visible by simple name.
  The declared class makes its nested classes visible.
- **Imports**: exact (`a.b.Name`) and wildcard (`a.b.*`) imports. `java.lang.*` and `java.util.*`
  are always implied.
- **Variables**: the environment values (for example a version) that macro conditions test.
  `evaluateExpression` supports `&&` and `||` (evaluated left to right), `!` negation, a bare variable
  name (true if set), `variable <operator> value` comparisons (version-aware, via [`TextValueSequence`](text-value-sequence.md)),
  `exists` (whether a class, method or field is present) and `assignable` (class assignability).
- **Requirements and remappings**: the `#require` and `#remap` rules found in the template,
  later used when generating code and looking up members.
- **Bootstrap code**: runnables or code snippets (`#bootstrap`) executed once when the
  Template Handle class is first used.
- **Class declaration resolver name**: how the class's template is initially loaded (`#resolver`).
- **Flags**: whether errors are logged, and whether the resolver is being used for source
  generation (in which case some parsing work is skipped).

Class names are resolved (`resolve`, `resolveClass`, `resolvePath`) in this order:
single-letter generics map to `Object`; array types resolve their component; then absolute names;
then exact imports (also for `Name.Nested` or `Name$Nested` forms); then wildcard imports
(manually added ones, most recent first, then the declared class's nested classes, the package, then the defaults);
and finally the package path. A name that cannot be loaded still resolves to an assumed path, with no
class attached, so code generation can continue and report the error.

Usage notes:
- `ClassResolver.DEFAULT` is an immutable resolver with only the default imports, for
  declarations that need no template context.
- Create a fresh `new ClassResolver()` (or `clone()`) before adding imports or variables.
  Immutable instances throw `UnsupportedOperationException` on changes; `immutable()` makes a frozen copy.
- `#include` passes on only part of the context: requirements, remappings and variables are
  inherited, while imports, package and class name are not (`includeSourceDetails`).
- Resolution only finds a class if it exists; use `Resolver` registrations to cover renamed or moved classes.
- The same context is what `@Template.Import`, `@Template.Package` and `@ClassHook.HookImport`
  annotations feed into when no template file is used.

## Class Generation, Handles and Hooking

Besides accessing members, the library must be able to generate classes against an unknown
implementation, including subclasses that override (hook) methods while still being able to call the
original method. An earlier version used CGLib, which was too slow; this was replaced by in-house
code on pure ASM. Four layered features provide it:

1. **`ExtendedClassWriter`** (`reflection/util/`, usage in [class-generation.md](class-generation.md)): the foundation. Works like an ASM class writer but
   also accepts Javassist-compiled methods, and offers many helpers (pushing constants, invoking
   methods, calling the super method, static and singleton fields, naming of generated classes).
   It chooses and initializes the correct `GeneratorClassLoader` to define the class. All other
   generation in the library ends up using it.
2. **`TemplateHandleBuilder`** (`reflection/declarations/`): compiles abstract classes extending
   `Template.Handle` into runtime implementations (`TemplateClassBuilder` does the same for
   `Template.Class`). Works in two modes:
   - With the Gradle plugin: handle classes are generated from the template source bundle.
   - Without it: the handle declares its members through annotations such as `@Template.InstanceType`,
     `@Template.Generated("code")`, `@Template.Import`, `@Template.Require` and `@Template.Package`.
     Generated handle classes in [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) show the pattern.
3. **`ClassInterceptor`** (`reflection/`): callback-driven method hooking. For a class, a callback
   (`Invoker`) is looked up per method; hooked objects are instances of a generated subclass.
   Supports hooking existing objects, creating hooked instances, unhooking, and calling the original
   method (`invokeSuperMethod`).
4. **`ClassHook`** (`reflection/`; see [classhook.md](classhook.md) for usage and the legacy `:???` name convention): extends `ClassInterceptor` with an annotation-based API.
   Hook methods are declared with `@HookMethod("signature")` (plus `@HookPackage`, `@HookImport`,
   `@HookLoadVariables`, `@HookMethodCondition`), and the `base` field gives access to calling the
   original superclass method.

## Gradle Plugin

`mountiplex-gradle-plugin/` is a separate Gradle subproject that projects apply to use
Mountiplex templates at build time. It does two things:

1. **Handle generation** (`GenerateTemplateHandles` task, enabled through the `mountiplex`
   extension in `MountiplexExtension`):
   - Reads an entry template file (conventionally `init.txt`) from a configured source directory.
     That file uses `#include` directives to pull in the rest of the template bundle.
   - Parses the full bundle with the same engine the library uses at runtime
     (`SourceDeclaration`) and generates a `*Handle` Java source class per templated class
     (via `TemplateGenerator`) into a configured output directory and target package.
   - Optional variables can be passed in for macro evaluation during parsing.
   - The output directory is added to the source set so the generated handles compile with the project.
2. **Annotation string remapping** (`RemapAnnotationStrings` task, one per source set):
   - Makes multi-line strings in annotation values less awkward when targeting JDK 1.8,
     which has no text blocks.
   - A `%NAME%` placeholder in an annotation value is replaced with the text of the block comment
     labelled `<NAME>` placed above it. `SourceFileProcessor` reads the comments from source files,
     and `AnnotationReplacer` rewrites the annotation values in the compiled class bytecode
     (both in `reflection/util/asm/` of the main library).
   - Runs between compilation and packaging: compiled output is redirected through the task, so the
     remapped classes are what gets packaged. Source files are never modified, and no second
     source tree has to be maintained.

Plugin entry point: `MountiplexPlugin`, which registers the `mountiplex` extension and the tasks.

## Dependencies

- [Javassist](https://www.javassist.org/): parses and compiles generated code (template method bodies, generated classes).
- [ASM](https://asm.ow2.io/): bytecode generation for invokers, accessors, handles and hooks.
- [Objenesis](https://objenesis.org/): instantiation without calling constructors.
- No Minecraft dependency: the library is generic. Its main consumer is [BKCommonLib](https://github.com/bergerhealer/BKCommonLib), which
  supplies the environment-specific resolvers, converters and templates.

Dependency versions are declared in `build.gradle.kts`.

## Repository Layout

- `src/main/java/com/bergerkiller/mountiplex/` - the library (see components above).
- `src/test/java/com/bergerkiller/mountiplex/` - tests, with helper types under `types/`. Build, test and release steps are in [build-and-release.md](build-and-release.md).
- `mountiplex-gradle-plugin/` - separate Gradle subproject for the plugin.
- `build.gradle.kts`, `settings.gradle.kts` - build configuration.
