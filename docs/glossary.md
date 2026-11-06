# Glossary

Project terms used throughout the documentation.

- **Environment**: the hidden implementation being accessed, whose classes and members are
  not known at compile time and may change between versions.
- **Template**: a text file or string declaring classes and members of the environment, with
  macros and optional inline code.
- **Declaration**: a parsed element of a template (class, field, method, constructor, type,
  parameter, modifier). Lives in `reflection/declarations/`.
- **Macro**: a `#`-prefixed template directive, for example conditionals (`#if`, `#elseif`,
  `#else`, `#select`/`#case`), variables (`#set`), and declarations such as `#require`,
  `#remap`, `#include`, `#bootstrap`, `#resolver`, `#warning`, `#error`.
- **Preprocessing**: resolution of macros that decide which template text is kept, before parsing.
- **Handle**: a generated type that wraps an instance of an environment class and exposes
  its declared members type-safely.
- **Template.Class**: the per-class singleton describing a templated class: static members,
  handle creation and initialization.
- **Inline code / custom method body**: Java code written inside a template method to mock or
  extend behaviour in the environment.
- **Converter**: object translating a value from one type to another. Managed by the
  conversion registry (`Conversion`). See [conversion.md](conversion.md).
- **Duplex converter**: a converter that also works in the reverse direction.
- **Cost**: the priority of a converter. The registry picks the cheapest path of converters. See [Cost](conversion.md#cost).
- **Chain**: several converters combined to get from an input type to an output type that has no direct converter.
- **Converter provider**: creates converters on demand for a requested output type, used for generic types.
- **Type cast syntax**: the `(Type) InnerType` form in templates that applies a converter
  between the stable API type and the environment type.
- **ClassResolver**: the per-template context (imports, package, variables, requirements,
  remappings, class loader) in which declarations are parsed. Distinct from the global Resolver.
- **Resolver**: the global component that remaps class paths, field names and method names,
  and supplies class declarations, so one template works across environments. See [resolver.md](resolver.md).
- **Remapping**: translating a name from the template to the real name in the environment.
- **Invoker / Accessor**: fast callable objects for methods/constructors and fields.
  May be generated at runtime or reflection-backed.
- **Hook / Interceptor**: a runtime-generated subclass of an existing class that overrides its methods. The
  original class is never altered; only instances of the generated subclass intercept calls. `ClassInterceptor` is the callback-driven base; `ClassHook` adds annotations.
- **Base (hook)**: the `base` field of a `ClassHook`, used to call the original (super) method.
- **Generator class loader**: class loader used to define classes generated at runtime; chosen
  and initialized by `ExtendedClassWriter`.
- **Instance type**: the environment class a `Handle` or `Template.Class` represents,
  declared with `@Template.InstanceType` or in a template.
- **TextValueSequence**: compares strings containing numbers version-aware (`1.9` < `1.10`). See [text-value-sequence.md](text-value-sequence.md).
- **InputTypeMap / OutputTypeMap**: maps by type where a lookup also returns values stored for super types (input) or sub types (output). See [type-maps.md](type-maps.md).
- **Fast* (FastMethod, FastField, FastConstructor)**: lazily initialized reflection wrappers that generate direct-call code for public members. See [fast-access.md](fast-access.md).
- **Invoker**: the interface for calling a method or constructor with an instance and arguments. Implemented by generated, reflection and constant-returning variants.
- **Record changer (`<record_changer>`)**: a requirement method that returns a copy of an immutable record with some fields replaced. See [template-syntax.md](template-syntax.md#record-changer-record_changer).
- **Converting view**: a live wrapper (`ConvertingList`, `ConvertingMap`...) that converts the elements of another collection on access, without copying it. See [converting-collections.md](converting-collections.md).
- **ExtendedClassWriter / MPLType**: the ASM class writer for generating a class that extends or implements a type, and the helper for names and descriptors in bytecode. See [class-generation.md](class-generation.md).
- **ClassTemplate (legacy)**: the pre-template way to describe a class's members in Java code. Do not use in new code. See [legacy-class-template.md](legacy-class-template.md).
