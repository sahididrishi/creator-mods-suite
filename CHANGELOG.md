# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Jar versions carry the Minecraft version as build metadata, e.g. `0.1.0+1.21.1`.

## [Unreleased]

## [0.1.0] - 2026-09-12

First release. Minecraft 1.21.1, Java 21, NeoForge 21.1.250 and Fabric Loader 0.19.5
built from one shared source tree.

### Added

- **Multi-loader scaffold** - `common` / `fabric` / `neoforge` modules from
  [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) (branch
  `1.21.1`) with the Forge module removed, rebranded to `creatormods` / `dev.riftal.creator`.
  `common` compiles against vanilla through ModDevGradle NeoForm mode (Mojang mappings +
  Parchment 1.21.1/2024.11.17) and its sources and resources are consumed as *source* by both
  loader modules, so there is no common jar at runtime. Gradle wrapper 9.5.0, Fabric Loom 1.17.20,
  ModDevGradle 2.0.146, GeckoLib 4.9.2 on all three modules.
- **`dev.riftal.creator.core` shared library** - `Feature` / `CreatorMods` with four lifecycle
  phases (`registerContent`, `initCommon`, `initClient`, `initServer`) and per-feature failure
  isolation; `registry.Registrar` / `RegistryEntry` / `EntityAttributes` for loader-neutral
  deferred registration; `command.CommandHelper` / `SilentMode` / `CoreCommands` (`/creator`);
  `hud.HudLayers` / `HudText`; `client.ClientRenderers`; `sched.TickScheduler`;
  `data.PlayerData<T>` over NeoForge and Fabric attachments; `net.Payloads`; and
  `util.MathUtil`, `Fx`, `Selection`, `CooldownTracker`, `Titles`.
- **Director's Toolkit** (`creator_toolkit`) - take recorder with an on-screen clapperboard, freeze
  switches, a wave spawner and block-for-block arena snapshot/restore, all under `/creator` and one
  HUD line.
- **Ashen Colossus** (`creator_colossus`) - five-block, three-phase GeckoLib boss with a
  phase-coloured boss bar, ground shockwave slam, lava rain, minion waves, a ring-of-fire enrage
  and a `/colossus` control kit.
- **Power Kit** (`creator_powers`) - six keybind superpowers (dash, fire burst, ground pound, ender
  pull, shield dome, mob freeze) granted per player, with server-authoritative cooldowns and a
  sweeping cooldown HUD row.
- **Rule Engine** (`creator_rules`) - eleven stackable "Minecraft but..." rules flipped live via
  `/rule`, with presets, a top-right HUD list and persistence across restarts; no resource reload
  required.
- **Evolve** (`creator_evolve`) - five-stage player transformation from Hatchling to 2.6x Apex,
  each rung adding size, stats and a passive, with a locked transition sequence and a full model
  swap at the top.
- **Event Director** (`creator_events`) - five directed world events that can be started, retimed,
  skipped and stopped from one command; each is a relog-surviving phase machine that tints the sky
  via mixin and cleans up after itself.
- **Arsenal** (`creator_arsenal`) - four signature weapons (grapple blade, storm bow, gravity
  hammer, soul scythe), each a server-authoritative click mechanic with no GUI and no keybind.
- **Cursed Vault** (`creator_vault`) - locatable underground jigsaw dungeon with a Cursed Altar
  that consumes a Vault Key to summon a Vault Keeper mini-boss, a Sealed Chest that cracks open on
  its death, and `/vault reset`.
- **Per-feature toggles** - every feature can be switched off in `config/creatormods.json` or with
  `/creator feature <id> false`; a disabled feature registers nothing at all.
- **Test harness** - JUnit 5 unit tests in `:common` (`./gradlew :common:test`) and GameTests that
  run on both loaders (`./gradlew :fabric:runGameTest`, `./gradlew :neoforge:runGameTestServer`).

### Changed

- GameTests moved out of `src/main` into dedicated `gametest` source sets in all three modules, so
  no test class can reach a release jar. `:common` publishes its loader-agnostic test bodies
  through a `commonGametestJava` configuration that each loader compiles into its own `gametest`
  source set. `check` depends on `compileGametestJava` everywhere; the GameTest *runs* stay out of
  `check` so `./gradlew build` never boots a server.
- Every shipped `.ogg` is mono 44.1 kHz Ogg Vorbis, so Minecraft applies 3D positional attenuation
  instead of playing sounds flat. The generators render mono WAV with ffmpeg and encode with
  `oggenc`, because ffmpeg's native Vorbis encoder is stereo-only.

### Fixed

- Packet sends no longer throw because of the receiver: NeoForge's `NetworkRegistry#checkPacket`
  rejects a connection that never negotiated our channel, which escaped into feature code and
  permanently cancelled the scheduled task it ran from. Both network helpers now filter per player
  and broadcast player-by-player.
- A repeating `TickScheduler` task is no longer killed by a single throw; it has a budget of three
  consecutive failures, reset by any run that completes.
- Absorption-granting effects (shield dome, soul wisp) granted nothing, because 1.21 clamps
  `setAbsorptionAmount` to `generic.max_absorption`, which defaults to 0. Both now add a transient
  `MAX_ABSORPTION` modifier before setting the amount, and the dome drops the ceiling only after
  handing the hearts back so it cannot eat a golden apple's absorption.
- `creator_vault:vault_key` used the 1.20 shorthand ingredient form and failed to parse on both
  loaders; the recipe keys are objects now.
- The wave spawner used the world heightmap and put every mob on the roof of an enclosed set; it
  now searches up to eight blocks for a real floor.
- The take log truncates on the header line, so re-shooting a take number no longer concatenates
  the new take onto the discarded one.
- Added the missing Power Kit feature description lang key.
