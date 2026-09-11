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
| 1 | Burrow | Crouch for one second on dirt, sand or snow → 5 s of invisibility, then a 20 s cooldown. |
| 2 | Thick Skin | Resistance I whenever you are below half health. |
| 3 | Charge | A sprinting hit lands for +4 damage and throws the target (knockback 1.5). |
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

The beast is a **GeckoLib** model — `assets/creator_evolve/geo/entity/apex_beast.geo.json` plus
`animations/entity/apex_beast.animation.json` (`idle`, `walk`, `roar`, `attack`) — and the model a
player wears is the same model, renderer and animations you get from
`/summon creator_evolve:apex_beast`. To animate it, the client keeps one never-spawned `ApexBeast`
per Apex player and copies that player's position, rotations, walk speed and swing onto it each
frame; nothing is ever spawned server side. The roar clip plays once when an Apex transformation
lands and once per `/evolve roar`, for every client that can see you.

The mesh is authored to exactly 4.68 blocks — a stage-5 player's hitbox height — so the beast fills
the box it is standing in for instead of overshooting it.

Known limitations, all deliberate for the MVP:

* held items, armour, cape and elytra are not drawn at stage 5 — the beast has none of those;
* **first-person arms are still player arms**. Film Apex in third person;
* at 2.6× the third-person camera clips into ceilings — film Apex outdoors;
* mods that also mixin `PlayerRenderer#render` (3D Skin Layers, Ears) keep working: we wrap the one
  `LivingEntityRenderer.render` call inside it with a condition rather than cancelling the whole
  method, so their own injections still run. Their *layers* still vanish with the body at stage 5,
  because layers are drawn by the call we are skipping.

### HUD

Bottom-left: a 182×5 bar in the stage colour, the translated stage name, an `xp / next` counter,
floating `+N EVO` pop-ups (gold for kills, green for food, blue for commands) and an `EVOLVING`
banner plus a full-screen white flash during a transformation. The whole readout honours **F1**.

---

## Registered content

Evolve registers **no blocks, no items, no spawn egg and no creative tab** — the beast is reachable
only through `/summon`. In full:

| Registry / mechanism | Id |
|---|---|
| Entity type | `creator_evolve:apex_beast` (the GeckoLib beast; also the model a stage-5 player wears) |
| Sound events | `creator_evolve:evolve.roar_small`, `evolve.roar_apex`, `evolve.complete` |
| Player data attachment | `creator_evolve:evolution` — stage, XP, kills, the transformation flag and end tick, the model override; `copyOnDeath` |
| Payloads (S2C) | `creator_evolve:sync`, `creator_evolve:transform_fx`, `creator_evolve:xp_popup` |
| Attribute modifier ids | `creator_evolve:stage_scale`, `stage_health`, `stage_speed`, `stage_attack`, `stage_step`, `stage_jump`, `stage_reach_block`, `stage_reach_entity`, `stage_safe_fall`, `transform_lock` |

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
| `/evolve xp <targets> <amount>` | 2 | Add evolution XP, `-1000000 … 1000000` (negative allowed), auto-evolving on a threshold. |
| `/evolve reset <targets>` | 2 | Back to Hatchling: stage 1, 0 XP, modifiers and passives cleared, model override released, health refilled. |
| `/evolve roar [<targets>]` | 2 | Play the Apex roar and its particle ring on cue. At stage 5 it also scatters mobs within 10 blocks. Defaults to you. |
| `/evolve fx <targets> start [<ticks>]` | 2 | Play the transformation particles, chime and flash **without changing stage** — b-roll only. 1–400 ticks, default 40. Refused on a player who is genuinely mid-transformation. |
| `/evolve fx <targets> stop` | 2 | Stop a b-roll fx take. A real transformation is left strictly alone — its spiral, its lock and its finish all keep running. |
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

**Switching it off is reversible.** Stage modifiers are written as *permanent* attribute modifiers
so they survive in the player's vanilla NBT — without that, an Apex player would be clamped from 50
hp back to 20 on every relog, because vanilla loads attributes before health. The cost of permanent
modifiers is that they would otherwise outlive the feature: a world where `evolve` was turned off
would keep every player shrunk with nothing left running to undo it. So one hook stays live while
the feature is off — `EvolveAttributeMapMixin` strips the ten fixed `creator_evolve:*` modifier ids
as the entity's attributes load. Nothing else is touched; the stage data stays in the attachment, so
turning the feature back on and rejoining puts you back on the rung you were standing on.

If you ever need to do it by hand (an old world, the mod uninstalled entirely), the ids are
`creator_evolve:stage_scale`, `stage_health`, `stage_speed`, `stage_attack`, `stage_step`,
`stage_jump`, `stage_reach_block`, `stage_reach_entity`, `stage_safe_fall` and `transform_lock`:

```
/attribute <player> minecraft:generic.scale modifier remove creator_evolve:stage_scale
/attribute <player> minecraft:generic.max_health modifier remove creator_evolve:stage_health
```

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

Multiplayer: your stage is sent to a player the moment you come into their tracking range (Fabric
`EntityTrackingEvents.START_TRACKING`, NeoForge `PlayerEvent.StartTracking`), so someone walking up
for the scale shot sees the beast on the first frame rather than up to five seconds later. The whole
roster is still re-sent every five seconds as a backstop, and again on join, respawn and every
dimension change.

---

## What it deliberately does not do

* **No balance config.** The ladder, the XP awards and the sequence lengths are code
  (`stage/Stages.java`, `stage/StageMath.java`, `progression/Transformation.java`). A config value
  that changes what gets registered is a start-up crash on NeoForge, so there is none.
* **No items, no blocks, no spawn egg and no creative tab.** The beast is reachable through
  `/summon creator_evolve:apex_beast` and through being stage 5; nothing else.
* **No first-person beast arms**, and no held items, armour, cape or elytra at stage 5 — the beast
  model has none of those. Film Apex in third person, outdoors.
* **No XP for damage dealt, mining, or time played.** Kills, food and `/evolve xp` are the only
  three sources.
* **Stepping down never plays a sequence**, and `/evolve xp` with a negative amount leaves you
  holding the XP you are left with rather than snapping to the lower stage's floor.
* **Placeholder art.** The geometry and animations are a hand-authored blockout and the texture is
  procedural; see `ASSETS.md`.

## Tests

* `common/src/test/java/dev/riftal/creator/features/evolve/` — JUnit: the threshold ladder,
  progress and XP arithmetic, the stage table, the attachment record and its codec, the three
  payload stream codecs (including the roar packet), the perk table and the plan's Burrow/Charge
  numbers, the transformation and HUD timing curves (including the flash's fade-out surviving its
  own STOP packet), and an asset test that every lang key, sound file, texture, GeckoLib bone and
  animation clip the code names actually ships — and that the mesh is exactly as tall as
  `ApexBeast.MODEL_HEIGHT`.
* `EvolveGameTests` (+ the two loader stubs) — 20 GameTests: the modifiers for every rung including
  hitbox and eye height, health clamping, the movement lock, the attachment round trip, registered
  ids vs. shipped assets, the Apex beast entity and its hitbox, stripping every modifier back off
  (the feature-disabled path), and end-to-end runs with a real `ServerPlayer` — kill XP
  auto-evolving through a full sequence, a kill by something else awarding nothing, eating bread,
  `reset`, a multi-stage jump landing on Apex, `/evolve fx` never stepping on a live
  transformation in either direction, a sprint hit carrying the Charge bonus, and death + respawn
  keeping both the stage and the body.
