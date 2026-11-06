# Mountiplex

Java library for type-safe access to classes/members unknown at compile time (version-dependent internals), and for
runtime class generation. Package: `com.bergerkiller.mountiplex`.
Docs are in the repository's `docs/` folder (paths below are relative to the repository root); start with `docs/overview.md`, index in `docs/README.md`.

Read the docs when you:
- need reflection on types, fields or methods unknown at compile time (instead of `java.lang.reflect`);
- extend or generate classes with ASM or Javassist, or hook methods via a generated subclass (`ClassHook`, `@HookMethod`);
  existing classes are never altered or patched, only extended;
- meet template code or generated handles (see below), or conversion code (`Conversion`, `@ConverterMethod`).

Recognize template code (read `docs/template-syntax.md`; never edit generated `*Handle.java`):
- `.txt` files with `#if`, `#require`, `#remap`, `#include`; `name:realName` aliases; `(Exposed) Real` casts.
- `Template.Handle` / `Template.Class` types with `@Template.*` annotations (`InstanceType`, `Generated("%NAME%")`,
  `Import`, `Require`) or a static `T` field.

Conversion: `docs/conversion.md`. ASM/Javassist: `docs/class-generation.md`. Hooks: `docs/classhook.md`.
