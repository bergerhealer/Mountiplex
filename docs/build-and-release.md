# Building, Testing and Releasing

For people changing this project itself. Projects that only depend on it need [examples/project-setup.md](examples/project-setup.md) instead.

## Repository Layout

| Path | Contents |
|---|---|
| `src/main/java/com/bergerkiller/mountiplex/` | The library. See [overview.md](overview.md) for the components. |
| `src/test/java/com/bergerkiller/mountiplex/` | JUnit 4 tests, one class per area (`ConversionTest`, `ClassHookTest`, `TypeMapLookupTest`...). Plain `*Test` classes, no resources. |
| `mountiplex-gradle-plugin/` | A second Gradle project (`:mountiplex-gradle-plugin`) containing the Gradle plugin `com.bergerkiller.mountiplex`. It depends on the root project, so it always uses the same Mountiplex code. |
| `build.gradle.kts`, `settings.gradle.kts` | Root build. The version is set in `build.gradle.kts`. |

## Toolchain Facts

- Build tool: the Gradle wrapper (`./gradlew`). The library targets **Java 8** (`sourceCompatibility`/`targetCompatibility` are `1.8` for both projects), so do not use newer language features or APIs.
- Runtime dependencies (published as `api`, so consumers get them): ASM, Objenesis and Javassist. Their versions are pinned in `build.gradle.kts`. Projects that shade the library relocate them,
  see [class-generation.md](class-generation.md#dependencies-asm-must-be-added-and-relocated-by-you).
- Test dependencies: JUnit 4 and `asm-util` (bytecode checking in tests).
- The build produces the jar, a sources jar and a Javadoc jar for both projects. Javadoc errors do not fail the build (`isFailOnError = false`), but the doclint warnings are real: keep Javadoc valid.
- `build/` directories are ignored by git. Old versioned jars may accumulate in `build/libs`; they can be deleted.

## Commands

| Goal | Command |
|---|---|
| Compile, test and package everything | `./gradlew build` |
| Run all tests | `./gradlew test` |
| Run one test class | `./gradlew test --tests '*ClassHookTest'` (add `-i` to see test output) |
| Work without network access | add `--offline` (works once dependencies are cached) |
| Publish to the Maven repository | `./gradlew publish`, see below |

A full `./gradlew build` finishes in seconds. At the time of writing the test suite has about 180 tests; all pass, one is skipped.

### Writing Tests

- One test class per feature, named `<Feature>Test`, in the `com.bergerkiller.mountiplex` package (or a sub package that mirrors the source).
- Many tests define small interfaces and classes inside the test class and generate code for them, then assert on the result. Take the closest existing test as the pattern, for example `ExtendedClassWriterTest` for class generation,
  `ClassHookTest` for hooks, `ConversionTest` for converters and `GeneratedTemplateTest` for templates.
- A bug fix should come with a test that fails without it.

## The Gradle Plugin

`mountiplex-gradle-plugin/` registers the extension `mountiplex { ... }` and two kinds of task. How a consuming project configures them is described in
[project-setup.md](examples/project-setup.md#1-gradle-configuration). What the code does:

| Class | Role |
|---|---|
| `MountiplexPlugin` | Applies the plugin: creates the `mountiplex` extension, registers `generateTemplateHandles` (default input `src/main/templates`, default output `src/main/generated`) and one annotation-remap task per source set. |
| `MountiplexExtension` | The methods called from the build script: `generateTemplateHandles()` adds the generated directory to every source set, `remapAnnotationStrings()` moves the compile output to `build/mountiplex-classes/<sourceSet>` and runs the remap task after compilation. |
| `GenerateTemplateHandles` | Task inputs: `sourceDirectory`, `source` (first template file), `target` (package root), `variables`; output: `outputDirectory`. It loads the templates with `SourceDeclaration.loadFromDisk` and writes handle classes through `TemplateGenerator`. |
| `RemapAnnotationStrings` | Task that rewrites `%NAME%` strings in annotations in the compiled class files. It works incrementally on the changed classes. |

The plugin has no tests of its own (`test NO-SOURCE`); changes to it are checked by building a project that uses it, for example a local BKCommonLib build against a locally published Mountiplex.

## Releasing

1. Update the `version` in the root `build.gradle.kts`. The library and the plugin share it, and a project must use the **same version** of both.
2. `./gradlew build`, and make sure all tests pass.
3. `./gradlew publish` publishes the library (`com.bergerkiller.mountiplex:Mountiplex`) and the plugin to the `MGDev` Maven repository at `https://ci.mg-dev.eu/plugin/repository/everything`. The repository requires
   credentials, taken from the usual Gradle properties for a `PasswordCredentials` repository named `MGDev` (`MGDevUsername` and `MGDevPassword` in `~/.gradle/gradle.properties`).
   Development builds are produced by the project's Jenkins job, linked from the README.
4. Projects that consume it then raise their Mountiplex dependency and plugin version.

To test an unreleased change in a consuming project, run `./gradlew publishToMavenLocal` and let the consumer resolve from `mavenLocal()`.

## Where to Look

| Task | Files |
|---|---|
| Version, dependencies, publishing | `build.gradle.kts`, `settings.gradle.kts` |
| Gradle plugin | `mountiplex-gradle-plugin/src/main/java/com/bergerkiller/mountiplex/gradle/` |
| Existing tests to copy from | `src/test/java/com/bergerkiller/mountiplex/` |
