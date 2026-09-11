# Event Director (`events` · `creator_events`)

Five directed world events you can start, retime, skip and stop from one command, on camera, with
no mods-menu detour and no leftovers. Every event is a phase machine that survives a relog, tints
the sky and the fog through mixins on the vanilla render path instead of a resource pack, and
cleans up everything it created when you stop it.

> Part of the Creator Mods Suite. Turn the whole feature off with
> `/creator feature events false` (or `"events": false` in `config/creatormods.json`) and it
> registers nothing at all — no block, no entity, no command, no HUD, no mixin side effects.

---

## The five events

| id | what the camera sees | phases | ends when |
|---|---|---|---|
| `bloodmoon` | The sky **and the fog** flood red over 2 s, the clock time-lapses to dusk, every monster within 96 blocks glows through walls, the monster spawn cap doubles (global **and** per-chunk), and a low drone fades in as a client-side loop. | `rise` (40t) → `night` (open) → `fade` (40t) | dawn — or after 6000 ticks if `doDaylightCycle` is off |
| `meteor` | A 5-second countdown with title cards and a rising bell, a ring of flame on the target, then a burning boulder on a straight line, a TNT-grade explosion, a scorched crater and a loot chest in the middle of it. | `countdown` (100t) → `flight` (60t) → `impact` (1t) → `aftermath` (400t) | the aftermath runs out |
| `siege` | Waves of hostiles spawn out of sight 24–40 blocks out and converge on you, counted off on a red notched boss bar, with a `WAVE n/N` title between waves and a reward drop at the end. | `prepare` (60t) → `waves` (open) → `victory` (100t) | the last wave is cleared |
| `luckyrain` | Gold `?` blocks fall around every player and burst into a random outcome — loot, mobs, an explosion, a command, a potion effect. | `rain` (`duration` seconds) | the duration runs out |
| `voidrise` | A black kill-plane climbs out of the world floor behind a curtain of ink and ash, with a HUD height readout and a proximity warning. Nothing is destroyed. | `rise` (open) → `hold` (200t) | the plane reaches `maxY` |

Only **one** event runs at a time. Starting a second one stops the first with `STOPPED` first, so
you can cut between takes without `/event stop` in between.

---

## Commands

Root: `/event`. Permission 2 is the "cheats" level a creator has in single player.

| command | perm | what it does |
|---|---|---|
| `/event start <name> [origin] [key=value …]` | 2 | Starts an event. `origin` defaults to the block you are looking at within 64 blocks, and to 20 blocks ahead of you if you are aiming at sky. Replaces a running event. |
| `/event stop` | 2 | Tears the running event down and clears the client tint and HUD line. |
| `/event skip` | 2 | Ends the current phase immediately. Past the last phase it finishes the event. This is what makes `countdown → impact` and `bloodmoon → dawn` land on cue. |
| `/event timer <1..86400>` | 2 | Retimes the current phase in seconds. `voidrise` reinterprets it as "reach the ceiling in this long" and recomputes its speed instead. |
| `/event list` | 0 | `Events: bloodmoon, meteor, siege, luckyrain, voidrise` |
| `/event status` | 0 | `Siege · waves · 0:35 in phase · wave 2/5 · 7 alive`, or the last event that ran while idle. |
| `/event reload` | 2 | Drops the lucky-outcome and siege-wave caches so the data-pack JSON is read again. Does not disturb the running event. |
| `/event hud <true\|false>` | 2 | Hides **only** our HUD line, for a clean thumbnail. The siege boss bar is a vanilla boss bar and stays. |

All feedback goes through the suite's silent mode (`/creator silent true` → action bar instead of
chat). **Titles are packets, not chat**, so the meteor countdown and the wave cards are never
suppressed — they are the shot.

### Options

`key=value` pairs, whitespace separated, after the event name (and after the origin if you gave
one). Unknown keys are ignored; a malformed number falls back to the default. The raw string is
saved with the event, so a resumed event keeps the options it started with.

| event | key | default | range | meaning |
|---|---|---|---|---|
| `siege` | `waves` | all five | 1 … table size | how many waves to run |
| `siege` | `radius` | 40 | 6 … 128 | outer spawn radius; the inner radius is 60 % of it, floor 4 |
| `siege` | `bossbar` | `true` | boolean | `false` runs the waves without the vanilla boss bar |
| `luckyrain` | `interval` | 20 | 2 … 200 | ticks between drops, per player |
| `luckyrain` | `radius` | 12 | 2 … 48 | drop radius around each player |
| `luckyrain` | `duration` | 60 | 1 … 3600 | seconds of rain |
| `luckyrain` | `luck` | 0 | -5 … 5 | player luck fed to the outcome weighting; 0 is the plain table |
| `voidrise` | `speed` | 0.05 | 0.001 … 4 | blocks per tick (0.05 = one block a second) |
| `voidrise` | `maxY` | 40 | above `minY` | where the plane stops |
| `voidrise` | `minY` | world floor | build range | where the plane starts |

```
/event start meteor ~ ~ ~20
/event start siege waves=2 radius=30
/event start luckyrain interval=10 duration=30
/event start voidrise speed=0.3 maxY=80
```

---

## Configuration and data files

There is no per-feature config file: the only switch is `features.events` in
`config/creatormods.json`. Everything else is a data pack, so it reloads without a restart.

| file | what it controls |
|---|---|
| `data/creator_events/lucky_outcomes/default.json` | the lucky-rain drop table |
| `data/creator_events/siege_waves/default.json` | the siege wave table |
| `data/creator_events/loot_table/meteor.json` | what is in the crater chest |
| `data/creator_events/loot_table/siege_reward.json` | what victory drops |
| `data/creator_events/loot_table/blocks/lucky_rain.json` | what the `lucky_rain` block drops when mined |

Both tables fall back to an identical in-code default if the file is missing or malformed, so a
typo mid-recording degrades to the shipped behaviour instead of breaking the take. Run
`/event reload` after editing either one.

### Lucky outcome schema

```json
{
  "outcomes": [
    { "type": "items",     "weight": 10, "luck":  1, "loot_table": "minecraft:chests/simple_dungeon", "rolls": 3 },
    { "type": "entity",    "weight":  6, "luck": -1, "id": "minecraft:creeper", "count": 1 },
    { "type": "explosion", "weight":  4, "luck": -2, "radius": 3.0, "fire": false },
    { "type": "command",   "weight":  8, "run": "summon minecraft:pig ~ ~ ~ {Saddle:1b}" },
    { "type": "effect",    "weight":  5, "effect": "minecraft:levitation", "duration": 100, "radius": 6.0 }
  ]
}
```

`weight` is the relative pick weight. `luck` is how much player luck should favour the outcome:
the effective weight is `weight × (1 / (1 − |playerLuck| × 0.77 / 100)) ^ luck`, so at player luck
0 — the default, and what you get unless you pass `luck=` on the command — it collapses to the
plain weight. Entries with an unknown `type` or a non-positive `weight` are dropped and the rest
still load.

### Siege wave schema

```json
{ "waves": [ { "spawns": [ { "entity": "minecraft:zombie", "count": 8 } ] } ] }
```

Counts are Normal-difficulty counts; Easy scales by 0.6 and Hard by 1.4, rounded, never below one.
Any entity id in the registry works. Raider-family mobs are spawned with `setCanJoinRaid(false)`
so they never turn into a vanilla raid, and every mob is tagged `creator_events_siege` and made
persistent, so `/event stop` can take all of them back.

---

## Recording with it

1. **Frame first, then start.** `origin` defaults to the block you are looking at, so aim at the
   clearing you want the meteor in and type `/event start meteor` — no coordinates.
2. **`/event timer` makes a phase fit the edit.** `timer 3` on the meteor countdown turns five
   title cards into three. On `voidrise` it means "be at the ceiling in this long", which is how
   you make the plane arrive exactly as you finish pillaring up.
3. **`/event skip` is the cut.** Skip out of the countdown straight into the impact; skip out of
   the blood moon's night straight into dawn. Both are one keypress on a macro pad.
4. **`/event hud false` for thumbnails**, `F1` for everything (the HUD line honours F1 on its own).
5. **`/creator silent true`** puts command feedback on the action bar, so chat stays empty for the
   whole take while titles and boss bars still play.
6. **Relog safe.** The active event, its phase, its phase tick, its timer override and its own
   state are written to the world's saved data on every phase change and once a second. A server
   restart mid-siege comes back on the same wave with the boss bar rebuilt; a restart mid-flight
   drops the meteor straight into its impact.
7. **`/event stop` is the reset.** Siege mobs are discarded, blood-moon glow and the raised spawn
   cap are released, the boulder is taken back, the tint fades. `voidrise` destroyed nothing, so
   stopping it restores the world by doing nothing at all.

### Shot list that works (about 45 s)

| t | shot | command |
|---|---|---|
| 0–8 s | hill at dusk, wide | `/event start bloodmoon` |
| 8–11 s | same | `/event skip` |
| 11–22 s | aim at a clearing 20 blocks out, cut to the crater | `/event start meteor` |
| 22–31 s | third person, slow pan | `/event start siege waves=2` |
| 31–38 s | low angle looking up | `/event start luckyrain interval=10` |
| 38–45 s | edge of a ravine, HUD visible | `/event start voidrise speed=0.3` then `/event stop` |

---

## Content this feature registers

| kind | id |
|---|---|
| block + item | `creator_events:lucky_rain` (the falling gold `?`) |
| entity type | `creator_events:meteor` |
| sounds | `creator_events:bloodmoon.drone`, `meteor.whistle`, `meteor.impact`, `siege.horn`, `lucky.pop`, `void.hum` |
| creative tab | `creator_events` |
| payload | `creator_events:event_state` (server → client, cosmetic only) |
| saved data | `creator_events_director` in the overworld's data storage |

Mixins — every one of them returns on its first statement when the feature is disabled or no event
is running:

| Class | Target | Why |
|---|---|---|
| `EventsClientLevelMixin` | `ClientLevel#getSkyColor` (`@ModifyReturnValue`) | sky-dome tint (client) |
| `EventsFogRendererMixin` | `FogRenderer#setupColor` (`@Inject`) | the matching fog tint (client); skipped in water, lava and powder snow |
| `EventsMinecraftMixin` | `Minecraft#tick`, `#disconnect` | drives the client-side ambient loop and drops it on disconnect |
| `EventsSpawnStateMixin` | `NaturalSpawner.SpawnState#canSpawnForCategory` | raises the **global** monster cap during a blood moon |
| `EventsLocalMobCapCalculatorMixin` | `LocalMobCapCalculator$MobCounts#canSpawn` | raises the **per-chunk** cap by the same multiplier |
| `EventsNaturalSpawnerMixin` | `NaturalSpawner#spawnForChunk` | arms the multiplier for the spawn pass |
| `EventsEntityCallbacksMixin` | `ServerLevel$EntityCallbacks#onTrackingStart` | tags newly tracked mobs for the running event |
| `EventsPlayerListMixin` | `PlayerList#placeNewPlayer` | pushes the event state to a player who joins mid-event |

Both spawn-cap mixins carry `priority = 900`, which is what keeps them out of the way of other mods
(Enhanced Celestials) that inject at the same two places.

## Known limits

* **Sky and fog are tinted; nothing else is.** Both go through one mixin each on the vanilla methods,
  on both loaders, so the two look identical — but underwater, lava and powder-snow fog are left
  alone on purpose, and there is no biome, water or cloud tint.
* **The ambient bed is a client-side looping sound instance** (`EventAmbientSound`, 40-tick fade in
  and out), started once per client rather than re-sent per player. It fades rather than cutting on
  `/event stop`, which is deliberate; a hard cut is what a `stop` on the sound manager would give.
* **Spawn cap**: both the global and the per-chunk monster caps are raised by the event's
  multiplier (×2 for the blood moon), so the horde thickens around the camera rather than only in
  the band between the vanilla global cap and the raised one.
* **Shader packs** may override the sky tint; `getSkyColor` return-modification is respected by
  Iris but a pack that computes its own sky will win.
* Placeholder art and audio — see `common/src/main/java/dev/riftal/creator/features/events/ASSETS.md`.

## Tests

| Kind | Where | What it covers |
|---|---|---|
| JUnit | `common/src/test/java/dev/riftal/creator/features/events/` | `PhaseMachineTest` (timed phases completing exactly on their duration, open phases waiting on the world, progress clamping, every shipped event declaring its planned phases, the blood-moon constants), `EventLogicTest` (option parsing, clamping and garbage, the crater shape, the void plane rising and clamping, `/event timer` recomputing the rise speed, siege waves scaling with difficulty and round-tripping through JSON), `LuckyOutcomeWeightingTest` (plain weights at luck 0, the documented luck formula, seeded shares, a broken data pack never emptying the table), `MeteorTrajectoryTest` (aim velocity, the flight arriving inside its phase, crater size and clamping), `SpawnRingDistributionTest` (area-uniform radius, uniform angle, repaired bounds), `SkyTintTest` (the ramp, the fade, clamping, per-channel mixing), `ClientEventStateTest` (the mirror resetting between worlds, the tick-driven fade, other dimensions getting neither HUD nor tint, and the ambient-loop id a running event names), `EventStatePayloadTest` (payload round trip, the idle payload, per-dimension gating), `EventAssetCoverageTest` (every sound event has a definition, a subtitle and an `.ogg`; the lucky-rain block has its whole file set; the loot tables the code names exist; the shipped data-pack files parse with the game's own parsers; every lang key resolves) |
| GameTest | `common/src/gametest/java/dev/riftal/creator/features/events/gametest/EventsGameTests.java` | 10 bodies: feature enabled; all five events registered; the blood moon raising and restoring the spawn cap and its skip bringing dawn; starting an event replacing the active one; `/event timer` retiming a phase and `/event skip` ending the event; the meteor skipping from countdown to flight and its impact building the crater chest; a lucky `entity` outcome spawning its mob; the void plane clamping at `maxY` and handing over to `hold`; the siege spawning its first wave |
| Loader stubs | `fabric/src/gametest/java/.../EventsFabricGameTests.java`, `neoforge/src/gametest/java/.../EventsNeoForgeGameTests.java` | — |

Assets: see [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/events/ASSETS.md)
— the one texture and the six sounds are procedural placeholders.
