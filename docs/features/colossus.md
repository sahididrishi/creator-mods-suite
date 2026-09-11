# Ashen Colossus (`colossus`)

> A five-block, three-phase GeckoLib boss with a phase-coloured boss bar, a ground-shockwave slam,
> lava rain, minion waves, an enrage that closes a ring of fire around the arena, and a full
> `/colossus` control kit built for recording.

| | |
|---|---|
| Feature id | `colossus` |
| Resource namespace | `creator_colossus` |
| Java package | `dev.riftal.creator.features.colossus` |
| Root command | `/colossus` (permission level 2) |
| Creative tab | **Ashen Colossus** (`itemGroup.creator_colossus`) |
| Needs | GeckoLib 4.9.2 |

---

## What it adds

### Entities

| Id | What it is |
|---|---|
| `creator_colossus:ashen_colossus` | The boss. 600 HP, 2.6 x 5.2 blocks, knockback-immune, fire-immune, never despawns, persistent. |
| `creator_colossus:ashen_minion` | A 20 HP ash crawler. Bound by UUID to the boss that summoned it. |
| `creator_colossus:ash_bomb` | The projectile the lava rain throws. Renders as a lump of magma cream. |

Plus two spawn eggs, `creator_colossus:ashen_colossus_spawn_egg` and
`creator_colossus:ashen_minion_spawn_egg`, in the feature's own creative tab.

### The fight, phase by phase

Phases are driven purely by health and **never run backwards during a fight** — healing the boss
with `/colossus hp 100` does not undo an enrage. Entering a phase interrupts the current attack,
plays a roar the boss is invulnerable during, and recolours the boss bar.

| Phase | Health | Bar | Moves | Extras |
|---|---|---|---|---|
| **1** | above 66 % | Yellow, `NOTCHED_6` | Slam | — |
| **2** | 66 % – 33 % | Red | Slam, Lava Rain, Summon | Minions, fire patches |
| **3** | below 33 % | Purple, screen darkened | Slam, Lava Rain, Summon, Combo | Enrage, closing ring of fire |

**Slam** (phase 1+) — a 40-tick clip, impact at tick 20, then an eight-tick shockwave ring that
expands to 7 blocks. It deals 10 + 25 % of the boss' attack damage, throws victims out and up,
draws block-crack particles along the ring every tick and cracks the floor audibly — four vanilla
block-break effects, 90 degrees apart — on the impact tick only. One hit per victim per slam,
however many times the band sweeps over them. The boss' own minions are exempt.

**Lava Rain** (phase 2+) — twelve ash bombs over 24 ticks, re-aimed at the live target on every
bomb. A bomb does 6 damage plus a 3-second burn, and leaves a 3x3 fire patch that **puts itself out
after 80 ticks**, so a second take starts on a clean floor. Fire patches respect `mobGriefing`.

**Summon** (phase 2+) — three Ashen Minions claw out of the floor, capped at six alive, every
400 ticks (20 seconds). The summon is a **schedule, not a weighted roll**: the moment its timer is
up and there is room under the cap it is the boss' next attack, so the "minions rise" beat lands
when you expect it instead of several attack cycles late. Minions are never hit by their own boss' shockwave or ring, and crumble to smoke
the moment the boss dies.

**Enrage** (phase 3) — +50 % movement speed, an emissive "enraged" texture swap, and a ring of fire
that closes from the arena radius to 6 blocks at 0.35 blocks/second, redrawn every 5 ticks and
burning anything outside it every 20 ticks.

**Combo** (phase 3) — three hits at ticks 10 / 22 / 38 for 8 / 8 / 12 damage in a 3.5-block frontal
arc, with the boss re-facing its target between swings.

**Death** — a 70-tick collapse. It also puts the arena out: every fire patch still burning from
the last lava-rain volley is removed on the first tick of the collapse, so take two starts on a
clean floor even if `/colossus kill` lands mid-volley. The loot table and an 800 XP orb burst land on the *last* tick, not
the moment the health bar empties, so the drop bursts out of a corpse rather than out of a boss that
is still standing.

### On-screen

* A vanilla `ServerBossEvent` boss bar, recoloured per phase, pushed every 2 ticks from `tick()` —
  so it keeps updating even while the AI is frozen for a take.
* A **ring gauge** under the crosshair in phase 3 showing how much floor is left. It turns red with
  a `GET BACK INSIDE THE RING` warning when the local player steps outside. Honours F1 and rescans
  for a boss at most every 10 ticks.
* **Camera shake** on slam impact (0.6 / 12 t), phase roars (0.4 / 20 t), spawn (0.35 / 40 t),
  death (0.5 / 40 t) and footsteps (0.15 / 4 t), falling off with distance.
* **The camera is never a target.** A player in creative or spectator mode is skipped by the slam,
  the roar, the combo, the ash bombs and the ring of fire alike — no damage, no knockback, no fire
  overlay — so a second operator can fly through the arena for the wide shot. Note that the boss
  will not *target* a creative player either (vanilla `NearestAttackableTargetGoal` skips them), so
  the fight needs a body in survival or adventure mode on camera, or a spawned target mob.

### Registered content

| Registry / mechanism | Ids (namespace `creator_colossus`) |
|---|---|
| Entity type | `ashen_colossus`, `ashen_minion`, `ash_bomb` |
| Item | `ashen_colossus_spawn_egg`, `ashen_minion_spawn_egg` |
| Sound event | `colossus.roar`, `colossus.swing`, `colossus.slam`, `colossus.step`, `colossus.hurt`, `colossus.death`, `minion.hurt`, `minion.death` |
| Creative tab | `creator_colossus` — **Ashen Colossus** |
| Player data attachment | `creator_colossus:last_arena` (string, survives death) |
| Payload (S2C) | `creator_colossus:screen_shake` — cosmetic only |
| Saved data | `creator_colossus_arenas` per level |
| Loot tables | `creator_colossus:entities/ashen_colossus`, `creator_colossus:entities/ashen_minion` |

No blocks, no block entities, no particle types, no gamerules and no recipes.

---

## Commands

Root: `/colossus`. **Every subcommand requires permission level 2** (op, or a command block).
All feedback goes through the shared `CommandHelper`, so `/creator silent true` moves the green
success lines off camera and leaves only failures visible — to the executor only, in red.

Every boss-affecting subcommand acts on **the nearest living Ashen Colossus within 128 blocks** of
the caller. From the console or a command block that means the nearest to the level spawn. If there
is none you get `No Ashen Colossus within 128 blocks`, which is shown even under silent mode.

| Command | Arguments | Perm | What it does |
|---|---|---|---|
| `/colossus spawn` | `[arena]` | 2 | Spawns the boss at the named arena's centre, bound to that arena's ring. With no argument it reuses the arena you last worked with, falling back to `main` and to your own feet. The 60-tick rise animation plays and the boss is invulnerable during it. |
| `/colossus phase` | `<1-3>` | 2 | Forces health into the band (1 -> 100 %, 2 -> 65 %, 3 -> 32 %) and replays the entrance roar, **in either direction** — so you can re-shoot the phase 3 entrance without killing and respawning the boss. |
| `/colossus hp` | `<0.0-100.0>` | 2 | Sets health to that percentage; the phase machine picks it up on the next tick. `0` starts the death sequence. |
| `/colossus stagger` | — | 2 | Cancels the running attack, plays the 40-tick stagger clip, and makes the boss take double damage while it lasts. The window for a hero shot. |
| `/colossus kill` | — | 2 | Starts the collapse now. Loot and XP still drop. Bypasses the roar invulnerability. |
| `/colossus arena set` | `[name] [radius]` | 2 | Saves an arena at your feet (default name `main`, default radius 20, range 6–64) and **re-binds any live boss already bound to that name**, so the ring moves mid-take. |
| `/colossus arena clear` | `[name]` | 2 | Removes one arena, or all of them. |
| `/colossus status` | — | 2 | Health, phase, current attack, every cooldown, arena name and radius, live ring radius, minions alive. |

Arena names tab-complete from the ones saved in this level, and are case- and
whitespace-insensitive (`Main` and `main` are the same arena).

### Arenas

An arena is a name, a centre and a radius. They are stored per level in
`creator_colossus_arenas.dat` and survive a restart. The arena is what the phase 3 ring closes on
and what the boss is leashed to when it loses its target. Each creator's last-used arena name is
remembered on the player (`creator_colossus:last_arena`, kept across death), so a bare
`/colossus spawn` does the obvious thing on the second take.

---

## Config

This feature has no config surface of its own. The one key that matters is the suite-wide toggle in
`config/creatormods.json`:

```json
{ "features": { "colossus": true } }
```

Set it to `false` and restart, and the feature registers **nothing at all** — no entities, no spawn
eggs, no creative tab, no command, no HUD layer, no payload. The single camera mixin re-checks the
toggle at runtime, so vanilla camera behaviour is untouched too. `/creator features` lists the
state; `/creator feature colossus false` writes the key.

Two vanilla game rules do affect the fight:

| Game rule | Effect |
|---|---|
| `mobGriefing` | `false` stops ash bombs lighting fire patches. The bombs still damage and ignite. |
| `doMobLoot` | Standard vanilla behaviour on the death drop. |

---

## Recording with it

A 45-second run-through, in the order the fight was tuned for:

1. **Set up, off camera.** `/creator silent true` to move command feedback to the action bar, then
   stand in the middle of your arena and run `/colossus arena set main 20`. Do this once; it
   persists.
2. **Roll.** `/colossus spawn main`. The boss rises out of the floor over three seconds, roars, and
   the yellow bar appears. It is invulnerable for the whole entrance, so you can hold the shot.
3. **Phase 1.** Stand 4–6 blocks out. The boss walks in and slams; the shockwave reads best in
   third person (F5) with the camera low, because the ring travels across the floor.
4. **Phase 2.** `/colossus hp 60`. The bar turns red, the boss roars, and lava rain starts. Minions
   claw out of the ground a few seconds later. Shoot the roar close-up — the boss cannot be hurt
   during it, so nothing can interrupt the take.
5. **Phase 3.** `/colossus hp 30`. The screen darkens, the cracks go red-hot, the boss speeds up and
   the ring starts closing. Pull the camera back to show the ring; the HUD gauge tells you how much
   floor is left, and turns red the moment you are outside it.
6. **Finish.** `/colossus kill`. Three and a half seconds of collapse, then the loot and the XP
   burst. Cut on the explosion.

Handy while shooting:

* **Re-shoot a phase entrance** without respawning: `/colossus phase 2` replays the roar and the
  bar recolour from wherever you are, and works downward as well as upward.
* **Freeze the fight** for a framing pass: the boss bar, the phase machine and the ring all run from
  `tick()`, not the AI step, so they keep working while the AI is frozen.
* **Move the ring mid-take**: `/colossus arena set main 12` re-binds the live boss, and the ring
  jumps to the new circle without touching anything else.
* **Stagger for the money shot**: `/colossus stagger` freezes the boss reeling for two seconds and
  doubles the damage it takes, which is how you get a big number on screen on cue.
* **`/colossus status`** prints every cooldown, so you know how long until the next slam.
* Two bosses at once get separate bars, and each one's minions stay bound to the right parent.

### Gotchas worth knowing before you roll

* Fire patches burn wood. Basalt, deepslate or polished andesite arenas are the safe choice.
* The ring never closes tighter than 6 blocks, and an arena set smaller than that keeps its own
  radius rather than expanding.
* A player who logs out and back in mid-fight gets the right texture, ring radius and boss bar
  immediately: phase, ring radius and arena centre are all synched entity data, not client guesses.
* The audio that ships today is **procedural placeholder**, but it is **mono 44.1 kHz Ogg Vorbis**,
  so Minecraft plays it positionally — distance attenuation and direction both work. See
  `ASSETS.md` next to the feature source.

---

## Where things live

```
common/src/main/java/dev/riftal/creator/features/colossus/
├── ColossusFeature.java                  registration, creative tab, lifecycle
├── BossPhase / AttackKind / AttackSelector / Shockwave / ArenaRing / FirePatches / Combatants
│                                         pure logic (Combatants is the creative/spectator filter)
├── entity/       AshenColossusEntity, AshenMinionEntity, AshBombEntity
├── entity/ai/    one goal per attack + the chooser + the approach goal
├── arena/        Arena, ArenaSavedData
├── command/      ColossusCommand
├── net/          ScreenShakePayload                     (S2C, cosmetic)
├── client/       renderers, models, HUD, screen shake   (client only)
├── mixin/        ColossusCameraMixin                    (client only, toggle-guarded)
└── tools/        the placeholder art, audio and GameTest-arena generators

common/src/gametest/java/dev/riftal/creator/features/colossus/gametest/ColossusGameTests.java

common/src/main/resources/assets/creator_colossus/    geo, animations, textures, sounds, lang, models
common/src/main/resources/data/creator_colossus/      loot tables, GameTest structures
common/src/test/java/dev/riftal/creator/features/colossus/    JUnit
```

## What it deliberately does not do

* **No worldgen and no natural spawning.** The boss exists because you typed `/colossus spawn` or
  used a spawn egg; the entity types are not in any biome's spawn list and there is no structure.
* **No custom config file**, and no `/colossus reload`. The tuning table below is compiled in —
  change `AttackKind`, `AshenColossusEntity` or `ArenaRing` and rebuild.
* **No GeckoLib sound or particle keyframes.** Every sound and particle in the fight is emitted from
  the server on the ticks in `AttackKind`, because keyframe handlers run on the client render thread
  only and would double up what the server already plays.
* **The camera shake is cosmetic and one-way.** It is an S2C payload with no client acknowledgement;
  a client without the mod simply does not shake.
* **The ring never closes tighter than 6 blocks**, and an arena smaller than that keeps its own
  radius rather than being expanded to fit.
* **All art and audio is placeholder.** See `ASSETS.md`; the model is a banded grey box figure and
  is meant to look like one.

## Tuning reference

| Number | Value |
|---|---|
| Max health / attack damage / armor | 600 / 14 / 8 |
| Movement speed (phase 3) | 0.24 (0.36) |
| Knockback resistance / follow range | 1.0 / 48 |
| Slam damage / knockback / radius | 10 + 25 % attack damage / 1.6 / 7 blocks |
| Combo damage | 8, 8, 12 over 3.5 blocks |
| Ash bomb | 6 damage + 3 s burn, 3x3 fire for 80 ticks |
| Minions | 3 per summon, cap 6, 400-tick cooldown, 20 HP each |
| Ring | arena radius down to 6 blocks at 0.35 blocks/s, 3 fire damage every 20 ticks |
| Global attack cooldown | 40 ticks |
| Death | 70 ticks, then loot + 800 XP |

## Tests

| Kind | Where | What it covers |
|---|---|---|
| JUnit | `common/src/test/java/dev/riftal/creator/features/colossus/` | `BossPhaseTest` (thresholds, bar colours, no backwards phases), `AttackKindTest` (durations, hit ticks, cooldowns, the invulnerable and choosable sets, NBT name fallback), `AttackSelectorTest` (phase gating, the minion cap, the summon schedule beating the weighted roll), `ShockwaveTest` (band maths, one hit per victim), `ArenaRingTest` (closing speed, the 6-block floor, inside/outside), `ArenaTest` (radius clamping, name normalisation, NBT), `ColossusAssetsTest` (every triggered clip exists, every animated bone exists in the geo, clip lengths match `AttackKind`, UVs stay on the sheet, `sounds.json` resolves to real `.ogg` files, the effect locators survive) |
| GameTest | `common/src/gametest/java/dev/riftal/creator/features/colossus/gametest/ColossusGameTests.java` | 23 bodies: feature enabled; spawn at full health in phase 1; phase thresholds from health and never running backwards on a heal; enrage applied entering phase 3 and removed leaving it; the boss bar tracking seen players; the slam damaging and launching, hitting each victim once and sparing the boss' own minions; summon bringing three minions and stopping at the cap; minions crumbling on the boss' death; the ring burning only what is outside; stagger interrupting the current attack and doubling incoming damage; the roar being invulnerable while `/colossus kill` is not; kill dropping loot and XP after the collapse; the NBT round trip keeping arena and phase; the chooser driving a real attack end to end; commands resolving the nearest boss; a creative camera being left alone by the whole fight; rebinding the arena restarting the ring; death extinguishing the fire patches |
| Loader stubs | `fabric/src/gametest/java/.../ColossusFabricGameTests.java`, `neoforge/src/gametest/java/.../ColossusNeoForgeGameTests.java` | The 15 fight tests run in this feature's own `data/creator_colossus/structure/arena_24.nbt` (24×12×24), because the shared 9×9×9 `empty.nbt` is smaller than the boss' 7-block shockwave; the eight that only read state use `empty`. |

Assets: see [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/colossus/ASSETS.md)
— every texture, sound and GeckoLib file in this feature is a procedural or hand-authored
placeholder.
