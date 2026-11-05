# Documentation Index

Architecture documentation for this project, intended for both humans and AI assistants.
Read the relevant pages here before opening source files.

## Meta

- [about-documentation.md](about-documentation.md) - How this documentation is written and maintained.

## Architecture

- [overview.md](overview.md) - Purpose, components and how they fit together. Read first.
- [template-syntax.md](template-syntax.md) - The template language. Read before interpreting, writing or changing template code (`.txt` templates, `*Handle` classes with a `T` field, `%NAME%` annotations).
- [examples/template-walkthrough.md](examples/template-walkthrough.md) - Complete template examples and the handles generated from them. Companion to template-syntax.md.
- [examples/project-setup.md](examples/project-setup.md) - Wiring a project: Gradle plugin, templates, runtime resolver with a `version` variable, and a basic remapper.
- [examples/inline-methods.md](examples/inline-methods.md) - Compiling and calling inline source strings at runtime with `MethodDeclaration`, `ClassResolver` and `FastMethod`, without handles.
- [conversion.md](conversion.md) - Converting values between types: converters, cost, chaining, sub-classing and generics, built-in converters. Read before using or changing `Conversion`, `@ConverterMethod`, or `(Exposed) Real` casts in templates.
- [examples/custom-converter.md](examples/custom-converter.md) - Registering a converter, using it in a template cast, and getting the `Converter` object.
- [text-value-sequence.md](text-value-sequence.md) - Version-aware string comparison (`TextValueSequence`), used by template conditions. Read when comparing versions or debugging a `#if version ...` result.
- [type-maps.md](type-maps.md) - Sub-class aware lookups by type: `InputTypeMap`, `OutputTypeMap`, `InputTypeSet`. Read when using or changing them, or when a lookup by type returns values for other types.
- [resolver.md](resolver.md) - The global `Resolver`: loading classes, name translation, finding members by name or declaration, class metadata, and registering translators.
- [class-generation.md](class-generation.md) - Writing a class with ASM through `ExtendedClassWriter` and `MPLType`, including the ASM dependency and relocation a project must set up. Read before generating classes.
- [examples/generate-class.md](examples/generate-class.md) - Implementing an interface with `ExtendedClassWriter`: a field, a hand-written method and a constant method.
- [converting-collections.md](converting-collections.md) - Creating `ConvertingList`/`ConvertingMap` views by hand for a given element converter, and when to copy instead.
- [utilities.md](utilities.md) - `MountiplexUtil`, `ReflectionUtil`, `BoxedType`, `NullInstantiator`, `LambdaBuilder`, `UniqueHash`.
- [build-and-release.md](build-and-release.md) - Building, testing and publishing this project; the Gradle plugin's tasks.
- [fast-access.md](fast-access.md) - `FastMethod`, `FastField`, `FastConstructor` and `Invoker`: lazy, low-overhead replacements for `java.lang.reflect`. Read when calling members by reflection or reading code that uses `Fast*` types.
- [legacy-class-template.md](legacy-class-template.md) - **Legacy.** `ClassTemplate` and the `FieldAccessor`/`MethodAccessor` family that predate templates. For reading and migrating old code only; no new code should use them.
- [classhook.md](classhook.md) - Hooking methods of existing classes (`ClassInterceptor`, `ClassHook`, `@HookMethod`). Read before reading or changing a class that extends `ClassHook`.
- [glossary.md](glossary.md) - Definitions of project terms.

## Internals (for maintainers of this project only)

Implementation details that are not needed to use the project as a dependency.

- [internals/remapping-and-javassist.md](internals/remapping-and-javassist.md) - `MPLType`, class loader remapping toggle, and the Javassist remapping glue.
