# Contributing

This repo is a multi-loader (NeoForge + Fabric) Minecraft 1.21.1 mod suite built from one shared
source tree. **[CONTRACT.md](CONTRACT.md) is the authority** on how the code is organised; this
file is the short version plus the mechanics.

## Building

Java 21 is required - nothing older, nothing newer, the Gradle toolchain pins it.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS; use your own JDK 21 path elsewhere
./gradlew build
```

A clean build takes about 10 seconds once the Gradle caches are warm. The *first* build on a fresh
machine is much slower: NeoForm downloads and decompiles Minecraft and Loom remaps it, once.

Shipping jars land in `fabric/build/libs/` and `neoforge/build/libs/`. `common/build/libs/` is
build-time only and is never shipped.

Dev runs: `:fabric:runClient`, `:fabric:runServer`, `:neoforge:runClient`, `:neoforge:runServer`.

`:neoforge:runData` exists but is dead weight - see "No datagen" below.

## Running the tests

```bash
./gradlew :common:test                 # JUnit 5 unit tests
./gradlew :fabric:runGameTest          # Fabric GameTest server
./gradlew :neoforge:runGameTestServer  # NeoForge GameTest server
```

The GameTest runs each boot a dedicated server and take about a minute, so they are deliberately
**not** wired into `check`. `./gradlew build` compiles the GameTest sources but never boots a
server - a green build proves your code compiles, not that it works. Run both GameTest tasks
before opening a PR.

Unit tests run without the game bootstrapped: Minecraft classes are on the classpath, but every
registry is empty. Anything touching `BuiltInRegistries`, `ItemStack` or a live level belongs in a
GameTest, not in JUnit.

GameTests are written in three pieces: the body in
`common/src/gametest/java/dev/riftal/creator/features/<id>/gametest/<Cap>GameTests.java`, and a
one-line stub per loader. **The stubs must be new methods on the existing `<Cap>FabricGameTests` /
`<Cap>NeoForgeGameTests` classes.** Fabric discovers tests only through the hard-coded
`fabric-gametest` entrypoint list in `fabric/src/gametest/resources/fabric.mod.json`; a sibling
class is silently never run, with no error and a green build.

## File ownership

Eight features are developed in parallel and the tree is partitioned so that no two people ever
touch the same file. Pick your feature id `<id>` (one of `toolkit`, `colossus`, `powers`, `rules`,
`evolve`, `events`, `arsenal`, `vault`) and stay inside these paths:

```
common/src/main/java/dev/riftal/creator/features/<id>/**
common/src/test/java/dev/riftal/creator/features/<id>/**
common/src/gametest/java/dev/riftal/creator/features/<id>/**
common/src/main/resources/creatormods-<id>.mixins.json          # append class names only
common/src/main/resources/assets/creator_<id>/**
common/src/main/resources/data/creator_<id>/**
fabric/src/main/java/dev/riftal/creator/features/<id>/**
fabric/src/gametest/java/dev/riftal/creator/features/<id>/**
neoforge/src/main/java/dev/riftal/creator/features/<id>/**
```

Two hard rules on top of that:

- **Loader resource directories are off limits entirely.** `fabric/src/main/resources/**`,
  `neoforge/src/main/resources/**` and `fabric/src/gametest/resources/**` hold the mod metadata,
  the loader mixin configs and the `META-INF/services` files, and every one of those is shared by
  all eight features. Only `common/src/main/resources/**` is merged into *both* loader jars, so a
  texture or loot table placed in a loader tree ships on one loader and is missing on the other.
  Every resource goes under `common/src/main/resources/{assets,data}/creator_<id>/`.
- **Nobody edits the build or the core library on their own.** That means the Gradle files
  (`build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradle/**`, `buildSrc/**`,
  the three module `build.gradle` files), `dev/riftal/creator/core/**`, `platform/**`, `mixin/**`,
  `Constants.java`, the shared mixin config, `pack.mcmeta`, the `creatormods` asset/data
  namespaces, and `CONTRACT.md` / `README.md` / `LICENSE` / `.gitignore` / `.gitattributes`. Open
  an issue instead.

Registry ids, package names, translation keys and file layouts follow the naming table in
CONTRACT.md section 6. 1.21 data folders are singular: `recipe`, `loot_table`, `advancement`,
`structure`, `predicate` (`tags` stays plural, the registry under it is singular).

## No datagen

**Datagen is banned on this project.** Do not write a `DataProvider`, `BlockStateProvider`,
`RecipeProvider`, `ItemModelProvider` or `LootTableProvider`, and do not create
`common/src/generated/`. Every JSON is hand-written and committed. No provider is registered
anywhere, so `:neoforge:runData` generates nothing; a half-wired datagen setup that silently
produces an empty tree is worse than no datagen at all. CONTRACT.md section 7 lists exactly which
files a block, item, entity type, sound, recipe and tag each need, with 1.21.1 examples.

## Code style

- 4 spaces, never tabs. UTF-8, LF line endings (`.gitattributes` enforces this).
- Lines stay under 120 characters.
- Imports are explicit - no wildcards - with static imports first, then sorted alphabetically.
- K&R braces: opening brace on the same line, and always braces, even for a one-line `if`.
- Extract a method rather than going past three levels of nesting.
- No Hungarian notation, no `m_` / `_` field prefixes. `UPPER_SNAKE_CASE` for constants,
  `lowerCamelCase` for everything else, `<Cap>Feature` for a feature entry point and
  `<Cap><Target>Mixin` for a mixin class.
- `Constants.LOG` is the only logger. Never call `LoggerFactory.getLogger(...)`, use SLF4J `{}`
  placeholders rather than concatenation, and never log inside a tick loop, an entity tick or a
  render call - not even at debug.
- Never call a vanilla API you have not read in the decompiled 1.21.1 source, and spell mixin
  method descriptors from `javap -s` rather than guessing. CONTRACT.md section 3 has the commands.
- `@Overwrite` and `@Redirect` are forbidden in mixins. Every cancellable injection is guarded by
  the feature's enabled check.

## Pull requests

One feature per PR. Before opening it: `./gradlew build` is green, `:common:test` passes, and both
GameTest tasks pass. Say which loader(s) you actually ran the game on.

By contributing you agree that your contribution is licensed under the MIT License, the same terms
as the rest of this repository (see [LICENSE](LICENSE)).
