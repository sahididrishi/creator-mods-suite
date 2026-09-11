# Creator Mods Suite

**Eight independently toggleable Minecraft features, built for people who record Minecraft.**
One multi-loader codebase, NeoForge and Fabric, Minecraft 1.21.1.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?style=for-the-badge)
![NeoForge 21.1.250](https://img.shields.io/badge/NeoForge-21.1.250-orange?style=for-the-badge)
![Fabric Loader 0.19.5](https://img.shields.io/badge/Fabric_Loader-0.19.5-blue?style=for-the-badge)
![Java 21](https://img.shields.io/badge/Java-21-red?style=for-the-badge)
![License MIT](https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge)

---

## What this is

A production suite for YouTube and stream recording: a director's toolkit, a three-phase boss, six
keybind superpowers, eleven stackable "Minecraft but…" rules, a five-stage player transformation,
five directed world events, four signature weapons, and a dungeon with a mini-boss and a resettable
altar.

It is one Gradle project. `common/` holds every line of gameplay code; the `fabric/` and
`neoforge/` modules are entry points, platform glue and mixin configs. Both loaders ship the same
behaviour from the same source, and the tests below run on both.

**Who it is for:** a creator or a creator's editor who needs a beat to land on cue, take after take,
without leaving the game to fiddle with a config screen — and a developer who has to maintain that
afterwards.

## Why a creator cares

* **Everything is a command, mid-recording.** Every beat in every feature has a trigger you can type
  or bind: `/colossus hp 30` drops the boss into phase 3 on cue, `/power use @s dash` fires an
  ability without the cooldown, `/event skip` lands the meteor exactly when the shot is framed,
  `/toolkit arena reset ring` puts the set back between takes. No pausing, no mods menu, no restart.
* **Nothing spams chat.** `/creator silent true` moves all command feedback to the action bar and
  switches off the vanilla op broadcast, so nothing in this suite ever writes a line into your
  footage. Failures stay visible, in red, to you only. Titles and boss bars are packets, not chat —
  those are the shot, so they are never suppressed.
* **Features toggle independently.** Eight keys in one file. A feature that is off registers
  *nothing* — no blocks, no entities, no commands, no HUD layers, no network payloads, and its
  mixins return on the first line. That is a genuinely clean single-feature capture, not a hidden
  one.
* **The HUD gets out of the way.** Every HUD layer honours F1, and `/toolkit hide hud|chat|nametags`
  cleans a frame per player with no permission needed, so a second camera operator can clean their
  own shot.
* **The camera is never a target.** A player in creative or spectator is skipped by the boss, the
  slam, the ring of fire, the ash bombs and the hammer lift, so you can fly through a fight for the
  wide shot.

## The eight features

| Feature | Id · root command | What it adds | Docs |
|---|---|---|---|
| **Director's Toolkit** | `toolkit` · `/toolkit` | Take recorder with an on-screen clapperboard and a mark key, freeze switches, wave spawner, block-for-block arena snapshots, camera bookmarks, clean-frame switches, creator cheats | [toolkit.md](docs/features/toolkit.md) |
| **Ashen Colossus** | `colossus` · `/colossus` | A five-block, three-phase GeckoLib boss: shockwave slam, lava rain, minion waves, an enrage that closes a ring of fire on the arena | [colossus.md](docs/features/colossus.md) |
| **Power Kit** | `powers` · `/power` | Six keybind abilities — dash, fire burst, ground pound, ender pull, shield dome, mob freeze — with server-authoritative cooldowns and a cooldown row above the hotbar | [powers.md](docs/features/powers.md) |
| **Rule Engine** | `rules` · `/rule`, `/shop` | Eleven stackable rules (random drops, ×10 crafts, lava floor, giant mobs, ×3 gravity, item roulette, hearts-as-currency…), flipped live, with data-pack presets | [rules.md](docs/features/rules.md) |
| **Evolve** | `evolve` · `/evolve` | A five-stage player transformation ladder — size, stats and one passive per rung, with a locked particle sequence between stages and a model swap at the top | [evolve.md](docs/features/evolve.md) |
| **Event Director** | `events` · `/event` | Five directed world events — blood moon, meteor, siege, lucky rain, void rise — each a phase machine you can retime, skip and stop | [events.md](docs/features/events.md) |
| **Arsenal** | `arsenal` · `/arsenal` | Four weapons with one unmistakable mechanic each: Grapple Blade, Storm Bow, Gravity Hammer, Soul Scythe | [arsenal.md](docs/features/arsenal.md) |
| **Cursed Vault** | `vault` · `/vault` | A jigsaw dungeon you can `/locate`, a Cursed Altar that summons a Vault Keeper mini-boss, Sealed Chests that crack open on its death, and a one-command reset | [vault.md](docs/features/vault.md) |

## Command reference

Every command node in the suite. Effects, arguments, feedback strings and permission notes per
command are in the feature doc linked in the last column.

### Suite

| Command | Perm | What it does |
|---|---|---|
| `/creator silent <true\|false>` | 2 | Recording mode: feedback to the action bar, vanilla command echo off |
| `/creator features` | 2 | Lists all eight features and whether each is enabled |
| `/creator feature <id> <true\|false>` | 2 | Writes the config toggle; takes effect on the next game start |
| `/creator tasks` | 2 | Number of tasks queued on the shared tick scheduler (diagnostics) |

### Features

| Root | Perm | Nodes | Detail |
|---|---|---|---|
| `/toolkit` | root open; nodes 2 except where marked | `take start` · `take stop` · `take mark [label]` · `take status` **(0)** · `take set <1..999>` · `freeze mobs\|players\|all <true\|false>` · `freeze status` · `wave spawn <entity> <1..200> <0..64> [ring\|random [centre]]` · `wave clear` · `arena save <name> [<from> <to>]` · `arena reset <name>` · `arena list` · `arena delete <name>` · `cam save <name>` · `cam go <name> [glideTicks]` · `cam list` · `cam del <name>` · `cheat god [true\|false]` · `cheat fly [true\|false]` · `cheat heal` · `cheat clear` · `hide hud\|chat\|nametags <true\|false>` **(0)** · `hide nametags <true\|false> <targets>` · `hide commands <true\|false>` · `tphere <players>` | [toolkit.md](docs/features/toolkit.md#commands) |
| `/colossus` | 2 | `spawn [arena]` · `phase <1-3>` · `hp <0.0-100.0>` · `stagger` · `kill` · `arena set [name] [radius]` · `arena clear [name]` · `status` | [colossus.md](docs/features/colossus.md#commands) |
| `/power` | 2, reads 0 | `give <targets> <ability>` · `give <targets> all` · `all [targets]` · `clear <targets>` · `remove <targets> <ability>` · `cooldown reset [targets] [ability]` · `cooldown set <targets> <ability> <ticks>` · `use <targets> <ability>` · `list [target]` **(0)** · `hud <on\|off\|radial\|linear>` **(0)** | [powers.md](docs/features/powers.md#commands) |
| `/rule`, `/shop` | reads 0, writes 2 | `/rule` **(0)** · `list` **(0)** · `status` **(0)** · `shop` **(0)** · `/shop` **(0)** · `preset list` **(0)** · `<name> on\|off\|toggle\|fire` · `preset <name>` · `clear` · `reload` · `hud on\|off` | [rules.md](docs/features/rules.md#commands) |
| `/evolve` | root 0; mutating nodes 2 | `info [player]` **(0)** · `set <targets> <1-5> [instant]` · `xp <targets> <amount>` · `reset <targets>` · `roar [targets]` · `fx <targets> start [ticks]` · `fx <targets> stop` · `model <targets> on\|off\|auto` | [evolve.md](docs/features/evolve.md#commands) |
| `/event` | 2, reads 0 | `start <name> [origin] [key=value …]` · `stop` · `skip` · `timer <1..86400>` · `list` **(0)** · `status` **(0)** · `reload` · `hud <true\|false>` | [events.md](docs/features/events.md#commands) |
| `/arsenal` | 2 | `give <targets> <weapon>` · `give <targets> all` · `cooldown reset [targets]` · `hook retract [targets]` · `slam cancel [targets]` | [arsenal.md](docs/features/arsenal.md#commands) |
| `/vault` | 2 | `key [1..16]` · `reset [radius]` · `spawn_keeper` · `unseal` · `tp` · `status` | [vault.md](docs/features/vault.md#commands) |

Permission 2 is the "cheats" level a single-player creator already has. Nodes marked **(0)** are open
to any player, and all of them are read-only or affect only the caller's own screen — so a camera
operator who is not an op can still read the clapperboard and clean their own frame.

Key bindings: `M` marks a take (Director's Toolkit); `R` `F` `G` `V` `C` `X` fire Power Kit slots
1-6. Both are real `KeyMapping`s, listed under **Options → Controls** and rebindable.

## Configuration

One file, `config/creatormods.json`, written on first run. It has exactly one kind of key:

```json
{
  "_comment": "Set a feature to false to keep it out of the game entirely.",
  "features": {
    "toolkit": true,
    "colossus": true,
    "powers": true,
    "rules": true,
    "evolve": true,
    "events": true,
    "arsenal": true,
    "vault": true
  }
}
```

| Key | Type | Default | Effect |
|---|---|---|---|
| `features.<id>` | boolean | `true` | `false` keeps that feature out of the game entirely — no registration, no commands, no HUD, no payloads. Takes effect on the next game start. |

Unknown keys are dropped when the file is rewritten; an unreadable file is logged and every feature
stays enabled. The same switch from in game is `/creator feature <id> <true|false>`, and
`/creator features` prints the current state.

There is **no other config file and no other config key.** Balance numbers (cooldowns, damage,
radii, the Evolve stage table) are compiled-in constants, deliberately: a config value that changes
what gets registered is a startup crash on NeoForge. Where a feature needs data that changes per
world, it uses a **data pack** instead, so it reloads without a restart — the Rule Engine's presets,
shop and item pool (`/rule reload`) and the Event Director's lucky-outcome, siege-wave and loot
tables (`/event reload`).

Two runtime switches live outside that file: the vanilla gamerule `creator_rules.rulesHud` (written
by `/rule hud`), and the vanilla gamerules `sendCommandFeedback` / `logAdminCommands`, which silent
mode borrows and restores when you switch it off — including at server shutdown, so closing a world
mid-shoot never saves them into `level.dat`.

## Installation

Both loader jars are built from the same source and behave identically. Install **one**.

**NeoForge**

1. Minecraft **1.21.1** with **NeoForge 21.1.250** or newer.
2. [GeckoLib 4.9.2](https://modrinth.com/mod/geckolib) for NeoForge 1.21.1 — **required**.
3. Drop `creatormods-neoforge-1.21.1-0.1.0+1.21.1.jar` into `mods/`.

**Fabric**

1. Minecraft **1.21.1** with **Fabric Loader 0.19.5** or newer.
2. [Fabric API](https://modrinth.com/mod/fabric-api) — **required** (built and tested against
   `0.116.17+1.21.1`).
3. [GeckoLib 4.9.2](https://modrinth.com/mod/geckolib) for Fabric 1.21.1 — **required**.
4. Drop `creatormods-fabric-1.21.1-0.1.0+1.21.1.jar` into `mods/`.

Java 21 is required on both. The mod is needed on the **client and the server** — the HUD layers,
the custom entity renderers and the key bindings are client-side, so a vanilla client on a server
running this will not see them (with one exception: `/toolkit hide nametags <true|false> <targets>`
works through a scoreboard team, which a vanilla client does honour).

No release has been published yet; build the jars from source with the commands below.

## Version support

| Minecraft | Loaders | Branch | Status |
|---|---|---|---|
| 1.21.1 | NeoForge 21.1.250+, Fabric Loader 0.19.5+ | `main` | **Primary** — active development |

Nothing else is supported. There is no 1.20.x backport and no 1.21.2+ port; both would be new
branches. The dependency range declared in the mod metadata is `[1.21.1, 1.22)`, but only 1.21.1 is
built and tested.

## Building from source

Requires **JDK 21** (developed on Temurin 21.0.8). The Gradle wrapper pins Gradle 9.5.

```bash
git clone <repository-url> creator-mods
cd creator-mods
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS; set it however your OS does

./gradlew build            # all three modules + the JUnit suite + both shippable jars
./gradlew clean build      # also green from scratch
```

Jars land in:

```
fabric/build/libs/creatormods-fabric-1.21.1-0.1.0+1.21.1.jar        # shippable
neoforge/build/libs/creatormods-neoforge-1.21.1-0.1.0+1.21.1.jar    # shippable
```

Sources and javadoc jars are produced alongside them. `common/build/libs/` is a build-time artifact
only and is never shipped — the loader modules consume `common`'s **sources**, so there is no common
jar at runtime.

Dev runs and tests:

```bash
./gradlew :fabric:runClient      :fabric:runServer
./gradlew :neoforge:runClient    :neoforge:runServer

./gradlew :common:test                 # JUnit 5 unit suite
./gradlew :fabric:runGameTest          # in-world GameTests on a headless Fabric server
./gradlew :neoforge:runGameTestServer  # in-world GameTests on a headless NeoForge server
```

A full `clean build` takes about 8 seconds on an M-series Mac with warm Gradle caches. The first
build on a fresh machine is much slower — NeoForm decompiles Minecraft and Loom remaps it, once.

## Tests

Two layers, both real, both run on demand. Last measured on 2026-09-12 against commit `39ac17f`:

| Suite | Command | Count | Result |
|---|---|---|---|
| Unit (JUnit 5, pure logic) | `./gradlew :common:test` | **486 tests** across 62 classes | all passing, 0 skipped |
| In-world GameTests, Fabric | `./gradlew :fabric:runGameTest` | **134 tests** | all passing (`fabric/build/junit.xml`) |
| In-world GameTests, NeoForge | `./gradlew :neoforge:runGameTestServer` | **134 tests** | `All 134 required tests passed` |

The unit suite is pure logic — ring and shockwave maths, timer formatting, cooldown tracking, NBT
round-trips, network payload codecs, the stage table, the command argument parsers, asset invariants
such as "every GeckoLib clip length matches the server-side attack timeline". The Minecraft classes
are on the classpath but the game is **not** bootstrapped, so anything touching a registry belongs
in the other layer.

The GameTests are the other layer: each one boots a headless dedicated server, builds a 9×9×9
structure and asserts against a live world — a wave spawning in a ring, a frozen mob released
intact, an arena restored block-for-block, a shield dome voiding arrows, a siege spawning its first
wave, a Vault altar re-sealing its chest. The bodies are loader-agnostic and live in
`common/src/gametest/`; each loader has a thin stub per feature so the **same 134 assertions run on
both loaders**. GameTests are not wired into `check` — they boot a server, so they are run
deliberately, not on every compile.

Test sources are never packaged: `gametest` is its own source set on all three modules, so no test
class can reach a release jar.

## Media

**There is no captured footage in this repository yet.** See
[`docs/media/SHOTLIST.md`](docs/media/SHOTLIST.md) — it specifies every planned shot: the exact
command sequence, world and seed, camera, HUD state, length, output format and the pass condition
that decides whether the take counts. It doubles as the capture-day checklist.

**All art and audio currently in the mod are placeholders, and are meant to look like it.** Every
texture is generated by a committed Python script, every sound is synthesised with ffmpeg, and every
model and animation is a hand-blocked-out shape. They exist at exactly the paths the code asks for,
so the mod runs with no missing textures, models or sounds, and a placeholder take still reads on
camera — but the Ashen Colossus is a banded grey box figure, not a sculpt. Every feature that ships
assets carries an `ASSETS.md` next to its source listing each file, its status, and what a real
artist should do with it:

```
common/src/main/java/dev/riftal/creator/features/<id>/ASSETS.md
```

Audio is mono 44.1 kHz Ogg Vorbis, which is the format Minecraft needs to play a sound
positionally — placeholder or not, distance and direction work. Nothing on a store page will be
AI-generated imagery; that is a Modrinth content-rules violation.

## Project layout

```
common/     all gameplay code: the creatorcore library + features/<id>/ ×8, plus assets and data
  src/main/java       dev.riftal.creator.core.*   and  dev.riftal.creator.features.<id>.*
  src/test/java       JUnit 5 unit suite
  src/gametest/java   loader-agnostic GameTest bodies
fabric/     ModInitializer entry points, Fabric mixins, platform helper, GameTest stubs
neoforge/   @Mod entry point, NeoForge mixins, platform helper, GameTest stubs
buildSrc/   the two convention plugins that wire common's sources into both loaders
docs/       per-feature documentation and the media shot list
```

Loader-specific behaviour reachable from `common` goes through a ServiceLoader:
`dev.riftal.creator.platform.Services`, with one implementation per loader. Resources live only in
`common/src/main/resources` and are merged into both jars, so nothing can ship on one loader and go
missing on the other.

[`CONTRACT.md`](CONTRACT.md) is the engineering contract the suite was built under: file ownership,
the core API, naming conventions, the mixin and GameTest rules, and the measured build commands.

## Licence

MIT — see [LICENSE](LICENSE). Code, assets and documentation alike. You may fork it, modify it, ship
it, and run a modified build on a private server without publishing anything.

## Credits

Built by **Nasir Idrishi** — [Riftal Studios](https://github.com/riftal-studios).

The multi-loader build is based on [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template)
(branch `1.21.1`, MIT), with the Forge module removed. Animated models use
[GeckoLib](https://github.com/bernie-g/geckolib) by Bernie/AzureDoom. Mappings are Mojang official
plus [ParchmentMC](https://parchmentmc.org/) `2024.11.17`.
