# Evolve

> Five-stage player transformation. Grow from a knee-high **Hatchling** to a 2.6× **Apex** beast,
> gaining size, stats and one passive per rung, with a locked, particle-heavy transformation
> sequence between stages and a full model swap at the top.

Feature id `evolve` · namespace `creator_evolve` · root command `/evolve`

---

## What it does

### The ladder

| # | Stage | XP to reach | Size | Max health | Attack | Step | Jump | Passive |
|---|-------|-------------|------|------------|--------|------|------|---------|
| 1 | Hatchling | 0 | 0.60× | 16 | 0.75× | 0.6 | 0.42 | **Burrow** |
| 2 | Runt | 100 | 0.85× | 20 | 1.00× | 0.6 | 0.47 | **Thick Skin** |
| 3 | Brute | 300 | 1.25× | 26 | 1.50× | 1.0 | 0.50 | **Charge** |
| 4 | Titan | 700 | 1.80× | 35 | 2.20× | 2.0 | 0.62 | **Stomp** |
| 5 | Apex | 1500 | 2.60× | 50 | 3.00× | 2.0 | 0.67 | **Roar** |

Sizes, health, speed and attack are `ADD_MULTIPLIED_TOTAL` modifiers, so armour, potions and
weapons still stack on top of a stage. Step height, jump strength, reach (+1.5 blocks and +2 at
stages 4–5) and safe-fall distance are `ADD_VALUE` on the vanilla base. Every modifier is written
under a fixed `creator_evolve:stage_*` id, so applying a stage twice never stacks and clearing one
never strips another mod's modifier.

The whole table lives in `stage/Stages.java`. There is no config file for balance — change the
table, not a JSON.

### Passives

| Stage | Passive | What it does |
|---|---|---|
| 1 | Burrow | Crouch for one second on dirt, sand or snow → invisibility until you move or stand. |
| 2 | Thick Skin | Resistance I whenever you are below half health. |
| 3 | Charge | Strength I while sprinting. |
| 4 | Stomp | Landing from 3+ blocks deals 6 damage and knockback in a 3-block radius. |
| 5 | Roar | `/evolve roar` scatters mobs within 10 blocks: target dropped, Weakness II, knockback. |

### XP

| Source | Award |
|---|---|
| Boss kill (ender dragon, wither, warden) | 200 |
| Player kill | 50 |
| Hostile mob kill | 15 (+5 if the mob has 40+ max health) |
| Anything else you kill | 3 |
| Eating | nutrition × 2 (bread = 10) |
| `/evolve xp` | whatever you ask for, negative allowed |

Crossing a threshold starts a transformation automatically. A jump of several rungs at once (a big
`/evolve xp`) runs **one** sequence straight to the final stage rather than five in a row.

### The transformation

40 ticks (2 s), or 60 ticks (3 s) into Apex:

* the new stage is banked immediately, so death, a relog or a dimension change mid-sequence cannot
  lose it — the sequence finishes or resumes on the other side;
* the player is pinned in place (transient −100 % movement speed, delta movement zeroed);
* a three-armed portal/end-rod helix climbs the player, scaled to their current size, with an
  amethyst chime ramping in pitch;
* at the end: the modifiers land, a roar and a chime play, an end-rod burst goes off, the screen
  flashes white and a **STAGE n / NAME** title card lands.

### The Apex model swap

At stage 5 the client draws the Apex beast instead of the player's body, scaled to match the
player's real hitbox height, for **everyone** who can see them (third person and other clients).
The name tag is re-emitted; shadow and fire overlay are untouched.

Known limitations, all deliberate for the MVP:

* held items, armour, cape and elytra are not drawn at stage 5 — the beast has none of those;
* **first-person arms are still player arms**. Film Apex in third person;
* at 2.6× the third-person camera clips into ceilings — film Apex outdoors;
* mods that also mixin `PlayerRenderer#render` (3D Skin Layers, Ears) may fight over the body. We
  cancel at HEAD, so we win, but their layers vanish with the body at stage 5 only.

### HUD

Bottom-left: a 182×5 bar in the stage colour, the translated stage name, an `xp / next` counter,
floating `+N EVO` pop-ups (gold for kills, green for food, blue for commands) and an `EVOLVING`
banner plus a full-screen white flash during a transformation. The whole readout honours **F1**.

---

## Commands

Root `/evolve`, usable at permission 0 so `info` works for anyone; every mutating branch requires
permission level 2. All feedback goes through `CommandHelper`, so `/creator silent true` moves it
from chat to the action bar — nothing this feature prints will land in your recording's chat log.

| Command | Perm | Effect |
|---|---|---|
| `/evolve info` | 0 | Your stage, XP/threshold, kills, live scale, max health and passive. |
| `/evolve info <player>` | 0 | The same for someone else. |
| `/evolve set <targets> <1-5>` | 2 | Set the stage, **running the full transformation sequence**. |
| `/evolve set <targets> <1-5> instant` | 2 | Set the stage with no sequence — snaps instantly. |
| `/evolve xp <targets> <amount>` | 2 | Add evolution XP (negative allowed), auto-evolving on a threshold. |
| `/evolve reset <targets>` | 2 | Back to Hatchling: stage 1, 0 XP, modifiers and passives cleared, model override released, health refilled. |
| `/evolve roar [<targets>]` | 2 | Play the Apex roar and its particle ring on cue. At stage 5 it also scatters mobs within 10 blocks. Defaults to you. |
| `/evolve fx <targets> start [<ticks>]` | 2 | Play the transformation particles, chime and flash **without changing stage** — b-roll only. 1–400 ticks, default 40. |
| `/evolve fx <targets> stop` | 2 | Stop a running sequence's fx and release the movement lock. |
| `/evolve model <targets> on\|off\|auto` | 2 | Force the beast model on, force the player skin on, or go back to following the stage. |

Stepping *down* a stage never plays a sequence; stepping up with `set` does unless you add
`instant`. `/evolve xp` with a negative amount keeps the XP you are left holding — it does not snap
you to the lower stage's floor.

`/evolve set` pins XP to the new stage's threshold, so the HUD bar starts empty at the new rung.

---

## Config

`config/creatormods.json`, written on first run:

```json
{ "features": { "evolve": true } }
```

| Key | Values | Effect |
|---|---|---|
| `features.evolve` | `true` / `false` | `false` registers **nothing**: no entity type, no sounds, no attachment, no commands, no HUD, no payloads, and every mixin is inert. One key, one restart, a clean capture of the other seven features. |

`/creator features` lists the state; `/creator feature evolve false` writes the key. **That is the
only config surface this feature has** — the stage table, the XP awards and the sequence lengths
are code (`stage/Stages.java`, `stage/StageMath.java`, `progression/Transformation.java`), because
a config value that changes what gets registered is a startup crash on NeoForge.

Evolution state is per-player and persists in the player's data: stage, XP, lifetime kills, the
transformation flag and its end tick, and the model override. It survives death (`copyOnDeath`),
relogs and dimension changes.

---

## Recording with it

The 45-second clip the feature was built for, and how to drive it:

1. **Set up.** `/creator silent true`, `/evolve reset @s`, F1 off so the bar is visible. Stand a
   zombie next to you for scale — at 0.6× a Hatchling is waist-high to it.
2. **Earn stage 2 on camera.** Kill three zombies. Each pops `+15 EVO`; the third crosses 100 and
   the sequence fires on its own. Shoot this in third person — the helix is around the body, not
   in front of the eyes.
3. **Jump to Brute.** `/evolve xp @s 400` off camera. One sequence, then punch a zombie: 1.5×
   attack one-shots it with a wooden sword.
4. **Titan.** `/evolve set @s 4`. Wide side shot: walk up a two-block staircase without jumping
   (step height 2.0), jump a three-block wall (jump strength 0.62, safe fall 5), and land from
   height to trigger Stomp.
5. **Apex.** `/evolve set @s 5` — the long 3-second sequence — then `/evolve roar` on the beat for
   the roar pose and the scatter. Bring a second player in for scale. Film outdoors.
6. **The button shot.** `/evolve set @s 1` snaps you back to Hatchling beside the beast's last
   footprint.

Useful while shooting:

* `/evolve fx @s start 120` — six seconds of helix and flash with no stage change, for a cutaway.
* `/evolve model @s on` at stage 1 — a Hatchling-sized beast; `/evolve model @s off` at stage 5 —
  your own skin at 2.6×. Either is a one-line gag shot.
* `/summon creator_evolve:apex_beast` — a real, walking, fighting beast for b-roll that does not
  need anyone to be stage 5.
* `/evolve info` — the exact numbers to read out in a voiceover.

Multiplayer: every stage is synced to everyone who can see you, and the whole roster is re-sent
every five seconds, so a second player joining mid-take sees the right sizes and the beast without
anyone relogging.

---

## Tests

* `common/src/test/java/dev/riftal/creator/features/evolve/` — 54 JUnit tests: the threshold
  ladder, progress and XP arithmetic, the stage table, the attachment record and its codec, the
  three payload stream codecs, the perk table, the transformation/HUD timing curves, and an asset
  test that every lang key, sound file and texture the code names actually ships.
* `EvolveGameTests` (+ the two loader stubs) — 14 GameTests: the modifiers for every rung including
  hitbox and eye height, health clamping, the movement lock, the attachment round trip, registered
  ids vs. shipped assets, the Apex beast entity, and four end-to-end runs with a real
  `ServerPlayer` — kill XP auto-evolving through a full sequence, a kill by something else awarding
  nothing, eating bread, `reset`, and a multi-stage jump landing on Apex.
