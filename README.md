# Creator Mods Suite

Multi-loader (NeoForge + Fabric) Minecraft mod skeleton for **Minecraft 1.21.1**, Java 21, built from
one shared source tree. Based on [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)
(branch `1.21.1`), with the Forge module removed.

- Mod id: `creatormods`
- Root package: `dev.riftal.creator`
- Group: `dev.riftal`
- Author: Nasir Idrishi (Riftal Studios)
- License: MIT

## Modules

| Module | Compiles against | Purpose |
|---|---|---|
| `common` | Vanilla via ModDevGradle NeoForm mode (Mojang mappings + Parchment) | All loader-agnostic code. Its `src/main/java` and `src/main/resources` are consumed as *source* by both loader modules, so there is no separate common jar at runtime. |
| `fabric` | Fabric Loom (Mojang mappings + Parchment) | `ModInitializer` entry point, Fabric-only mixins, `FabricPlatformHelper`, GameTest stubs. |
| `neoforge` | ModDevGradle | `@Mod` entry point, NeoForge-only mixins, `NeoForgePlatformHelper`, GameTest stubs. |

Loader-specific behaviour reachable from `common` goes through the ServiceLoader pattern:
`dev.riftal.creator.platform.Services` + `platform/services/IPlatformHelper`, with the implementation
named in each loader's `META-INF/services/dev.riftal.creator.platform.services.IPlatformHelper`.

## Commands

```bash
# Full build: compiles all three modules, runs the JUnit suite, produces the jars.
./gradlew build

# Unit tests only (:common, JUnit 5)
./gradlew :common:test

# GameTests (each boots a headless dedicated server, ~1 min each; NOT wired into `check`)
./gradlew :fabric:runGameTest            # report: fabric/build/junit.xml
./gradlew :neoforge:runGameTestServer    # exit code = number of failed required tests

# Dev runs
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runServer

# Datagen (NeoForge only; writes into common/src/generated/resources, shared by both loaders)
./gradlew :neoforge:runData
```

Build with JDK 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

## Output jars

```
common/build/libs/creatormods-common-1.21.1-<version>.jar
fabric/build/libs/creatormods-fabric-1.21.1-<version>.jar     # remapped, shippable
fabric/build/devlibs/creatormods-fabric-1.21.1-<version>-dev.jar
neoforge/build/libs/creatormods-neoforge-1.21.1-<version>.jar # shippable
```

Only the `fabric` and `neoforge` jars ship. `common` is a build-time artifact.

## Adding a GameTest

1. Write the body as a `public static void name(GameTestHelper helper)` in
   `common/src/main/java/dev/riftal/creator/gametest/CommonGameTests.java` (vanilla API only).
2. Add a delegating stub in **both**:
   - `fabric/src/gametest/java/dev/riftal/creator/gametest/FabricGameTests.java`
     — `@GameTest(template = "creatormods:empty")` (full `namespace:path`; vanilla has no
     `templateNamespace` field, and Fabric uses `template()` verbatim).
   - `neoforge/src/main/java/dev/riftal/creator/gametest/NeoForgeGameTests.java`
     — `@GameTest(template = "empty")` inside the `@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)` class.
3. Every test must end in `succeed()` / `succeedWhen(...)` or it burns the 100-tick timeout and fails.

Structure templates live in `common/src/main/resources/data/creatormods/structure/*.nbt`.
`empty.nbt` is a 9x9x9 box with a polished-andesite floor at y=0.

## Adding a mixin

Drop the class into `dev.riftal.creator.mixin` (common), `dev.riftal.creator.mixin.fabric`, or
`dev.riftal.creator.mixin.neoforge`, then list it in the matching `creatormods*.mixins.json`.
Loom >= 1.17 remaps mixins without an annotation processor, so there are no refmaps.
