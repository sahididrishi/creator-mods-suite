# Creator Mods Suite

Multi-loader (NeoForge + Fabric) Minecraft mod skeleton for **Minecraft 1.21.1**, Java 21, built from
one shared source tree. Based on [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)
(branch `1.21.1`), with the Forge module removed.

- Mod id: `creatormods`
- Root package: `dev.riftal.creator`
- Group: `dev.riftal`
- Author: Nasir Idrishi (Riftal Studios)
- License: MIT

## Working on this repo

Eight features are developed **in parallel**, one agent each, on top of a shared `creatorcore`
library. Before writing any feature code read **[CONTRACT.md](CONTRACT.md)** - it owns the file
ownership table, the core API with compiling examples, the naming conventions, and the rule that
feature agents do not run Gradle.

| Feature | id | Namespace | Plan |
|---|---|---|---|
| Director's Toolkit | `toolkit` | `creator_toolkit` | `plans/01-directors-toolkit.md` |
| Ashen Colossus | `colossus` | `creator_colossus` | `plans/02-ashen-colossus.md` |
| Power Kit | `powers` | `creator_powers` | `plans/03-power-kit.md` |
| Rule Engine | `rules` | `creator_rules` | `plans/04-rule-engine.md` |
| Evolve | `evolve` | `creator_evolve` | `plans/05-evolve.md` |
| Event Director | `events` | `creator_events` | `plans/06-event-director.md` |
| Arsenal | `arsenal` | `creator_arsenal` | `plans/07-arsenal.md` |
| Cursed Vault | `vault` | `creator_vault` | `plans/08-cursed-vault.md` |

Every feature can be switched off in `config/creatormods.json` (or with
`/creator feature <id> false`); a disabled feature registers nothing at all, which makes for clean
single-feature recordings.

## Shared library: `dev.riftal.creator.core`

```
core/
├── Feature                  # id + 4 lifecycle phases, one per feature
├── CreatorMods              # holds all 8 features, drives the lifecycle
├── config/CreatorConfig     # config/creatormods.json, one toggle per feature
├── registry/Registrar, RegistryEntry, EntityAttributes
├── command/CommandHelper, SilentMode, CoreCommands   # /creator
├── hud/HudLayer, HudLayers, HudText                  # client only
├── client/ClientRenderers                            # client only
├── sched/TickScheduler, ScheduledTask
├── data/PlayerData                                   # attachments
├── net/Payloads, ServerHandler, ClientHandler
├── util/MathUtil, Fx, Selection, CooldownTracker, Titles
└── platform/CoreServices + services/{IAttachmentHelper, INetworkHelper}
```

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

Core-level tests: body in `common/src/main/java/dev/riftal/creator/gametest/CommonGameTests.java`,
stubs in `fabric/src/gametest/java/.../FabricGameTests.java` (`@GameTest(template = "creatormods:empty")`
— full `namespace:path`, because vanilla has no `templateNamespace` field and Fabric uses
`template()` verbatim) and `neoforge/src/main/java/.../NeoForgeGameTests.java`
(`@GameTest(template = "empty")` inside `@GameTestHolder(MOD_ID) @PrefixGameTestTemplate(false)`).

Feature-level tests follow the same shape in the feature's own `gametest` package - see
[CONTRACT.md §7.2](CONTRACT.md). Every test must end in `succeed()` / `succeedWhen(...)` or it burns
the 100-tick timeout and fails.

Structure templates live in `common/src/main/resources/data/<namespace>/structure/*.nbt`.
`empty.nbt` is a 9x9x9 box with a polished-andesite floor at y=0, and ships in `creatormods` plus
all eight `creator_*` namespaces.

## Adding a mixin

Core mixins go in `dev.riftal.creator.mixin` (common), `dev.riftal.creator.mixin.fabric`, or
`dev.riftal.creator.mixin.neoforge`, listed in the matching `creatormods*.mixins.json`. Feature
mixins go in `dev.riftal.creator.features.<id>.mixin`, listed in `creatormods-<id>.mixins.json`
(already registered in both metadata files). Loom >= 1.17 remaps mixins without an annotation
processor, so there are no refmaps.
