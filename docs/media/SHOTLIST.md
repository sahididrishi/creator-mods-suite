# Shot lists

Eight 45-second demo clips, one per feature, written to be followed at the keyboard with the
recorder already running.

Every command on this page was copied out of the command class that registers it — the file is
named at the top of each section — not out of the plan. Where a plan's §10 shot list types a
command that was never implemented, the beat is rewritten around the real one and the difference
is listed under **Plan drift**. Commands that exist only in the plans are collected in
[Appendix B](#appendix-b--commands-that-exist-only-in-the-plans); do not put them on screen.

| Feature | Root | Section |
|---|---|---|
| Director's Toolkit | `/toolkit` | [1](#1--directors-toolkit-toolkit) |
| Ashen Colossus | `/colossus` | [2](#2--ashen-colossus-colossus) |
| Power Kit | `/power` | [3](#3--power-kit-powers) |
| Rule Engine | `/rule`, `/shop` | [4](#4--rule-engine-rules) |
| Evolve | `/evolve` | [5](#5--evolve-evolve) |
| Event Director | `/event` | [6](#6--event-director-events) |
| Arsenal | `/arsenal` | [7](#7--arsenal-arsenal) |
| Cursed Vault | `/vault` | [8](#8--cursed-vault-vault) |

---

## Before every shoot

Do this once per session, before the recorder is running.

1. **Install.** `creatormods-fabric-1.21.1-0.1.0+1.21.1.jar` (Fabric loader 0.19.5+, Fabric API)
   or `creatormods-neoforge-1.21.1-0.1.0+1.21.1.jar` (NeoForge 21.1.250+), Minecraft 1.21.1, plus
   **GeckoLib 4.9.2** — required on both loaders, and the Colossus, Evolve and Vault models do not
   render without it.
2. **Single-feature capture.** For a clean clip, switch the other seven off and restart:

   ```
   /creator features
   /creator feature colossus false
   ```

   A disabled feature registers nothing at all — no commands, no HUD, no payloads — so nothing
   from another feature can appear in frame. The toggle is written to `config/creatormods.json`
   and **takes effect on the next game start**, never immediately.
3. **Permissions.** Everything except `/toolkit take status`, `/toolkit hide hud|chat|nametags`,
   `/power list`, `/power hud`, `/rule list|status|shop`, `/shop`, `/evolve info` and
   `/event list|status` needs permission level 2. Single player with cheats on is level 4; on a
   dedicated server, `op <name>` first.
4. **Go quiet.** `/creator silent true` moves every success line to the action bar and stops the
   `[Creator: …]` broadcast to other ops. Failures stay visible, in red, to you only.
   `/toolkit hide commands true` does the same thing *and* restores `sendCommandFeedback` /
   `logAdminCommands` at shutdown, so use that one if the Toolkit is enabled.
5. **Clean the frame.** `/toolkit hide chat true` (chat stops drawing, you can still type),
   `/toolkit hide nametags true`, and `/toolkit hide hud true` only for stills — the action bar is
   part of the HUD, so with the HUD hidden you get **no** command feedback at all.
6. **Capture settings.** 1080p60 for clips; GUI scale 3 for any HUD or tooltip insert; F3+H
   (advanced tooltips) off; render distance 12+; no music under the beats where the point is that
   chat is silent.
7. **Freeze the clock** where the shot allows: `/gamerule doDaylightCycle false`,
   `/gamerule doWeatherCycle false`. Two exceptions are flagged in §6 (blood moon) and §2
   (`mobGriefing`).

---

## 1 — Director's Toolkit (`toolkit`)

*Verified against* `common/src/main/java/dev/riftal/creator/features/toolkit/command/` —
`ToolkitCommands`, `TakeCommands`, `FreezeCommands`, `WaveCommands`, `ArenaCommands`,
`CamCommands`, `CheatCommands`, `HideCommands`, `TpHereCommands`. *Plan:*
`plans/01-directors-toolkit.md` §10.

**Set:** a flat arena at least 25×25 with a hard floor and a few props (barrels, fences) standing
in it, so the arena reset has something visible to put back.

### Setup — run these before you roll

```
/gamerule doDaylightCycle false
/time set noon
/toolkit take set 1
```

Stand dead centre of the arena, then:

```
/toolkit arena save ring
```

(That quick-saves a 25×9×25 box: ±12 horizontal, −2…+6 vertical. For an exact box:
`/toolkit arena save ring 10 60 10 34 68 34`.)

Walk to the hero angle, then to the high angle, saving each:

```
/toolkit cam save hero
/toolkit cam save top
/toolkit cam list
/toolkit hide commands true
```

The silent-mode notice prints once, in chat, *before* the flip — so it will not be in the take.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:04 | Wide third person (F5), empty arena, HUD top-left in frame | `/toolkit take start` | The grey `--- TAKE 001` badge turns into red `REC TAKE 001 00:00.0` and starts counting. No chat line — the reply is on the action bar. |
| 0:04–0:07 | Hold on the HUD | *(press **M**)* | Yellow `MARK 1` flashes under the timer for two seconds. No chat box opens, nothing is echoed. |
| 0:07–0:13 | Slight high angle across the floor | `/toolkit wave spawn zombie 24 10 ring` | 24 zombies appear on a perfect 10-block ring, all facing inward. |
| 0:13–0:19 | Walk-through b-roll, camera at chest height inside the ring | `/toolkit freeze mobs true` | Everything stops mid-step — AI, gravity, fire, despawn timers. The HUD gains an aqua `FROZEN mobs` line. You can walk between them. |
| 0:19–0:24 | — (the cut *is* the shot) | `/toolkit cam go hero` then `/toolkit cam go top` | An instant snap to each saved position, yaw and pitch. Nothing appears on screen: `cam go` is silent by design. |
| 0:24–0:30 | First person, inside the crowd | `/toolkit freeze mobs false` then `/toolkit cheat god true` | The ring resumes on the same tick and `FROZEN` clears; zombies swing at you and you take no damage and no knockback. |
| 0:30–0:35 | Thumbnail frame, hold still | `/toolkit hide hud true` then `/toolkit hide nametags true` | HUD and floating names both gone. From here you get no feedback at all — that is expected, the action bar went with the HUD. |
| 0:35–0:40 | Wide, whole arena | `/toolkit hide hud false` then `/toolkit arena reset ring` | HUD returns; every wave zombie is swept out and the floor and props snap back block-for-block. |
| 0:40–0:45 | HUD close-up, then cut to a file browser | `/toolkit take stop` | Final time and mark count on the HUD; the action bar names the log. Show `<server dir>/creator-toolkit/takes/<world>_<yyyy-MM-dd>_take-001.log` — one line per mark. |

### Between takes

```
/toolkit wave clear
/toolkit freeze mobs false
/toolkit arena reset ring
/toolkit take set 1
```

### Plan drift

* The plan types `/take start`, `/wave spawn …`, `/freeze mobs on`, `/camgo hero`, `/camgo top 30`,
  `/hide hud on`, `/arena reset ring`. **There are no short aliases** — every verb lives under
  `/toolkit`, and the switches take `true|false`, not `on|off`.
* The plan's first beat presses **M** before the take starts. A mark with no take running is
  refused and the HUD does not flash, so `/toolkit take start` has to come first.
* `/toolkit cam go <name> [glideTicks]` accepts the glide argument and ignores it. The cut is an
  instant snap — do not build a moving-camera beat around it.

---

## 2 — Ashen Colossus (`colossus`)

*Verified against* `features/colossus/command/ColossusCommand.java`. *Plan:*
`plans/02-ashen-colossus.md` §10. Needs GeckoLib 4.9.2.

**Set:** a circular arena about 20 blocks in radius, open sky (lava rain comes down), built out of
basalt, deepslate or polished andesite. Fire patches burn wood.

**Cast:** the boss will not target a creative or spectator player (vanilla
`NearestAttackableTargetGoal` skips them), so the body on camera must be in survival or adventure.
A second operator in creative is never damaged by the slam, the roar, the combo, the ash bombs or
the ring — that is your wide-shot camera.

### Setup — run these before you roll

```
/creator silent true
/gamerule mobGriefing true
/gamemode survival
```

Stand dead centre:

```
/colossus arena set main 20
```

That persists per level — do it once, not once per take. Every boss-affecting subcommand acts on
the nearest living Colossus **within 128 blocks** of the caller.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:05 | Low wide on the empty arena (title card over it) | `/colossus spawn main` | The boss rises out of the floor across 60 ticks, roars, and a yellow `NOTCHED_6` boss bar appears. It is invulnerable for the whole entrance, so you can hold the shot. |
| 0:05–0:13 | Third person (F5), camera low, you 4–6 blocks out | *(fight — no command)* | Walk-in, then the 40-tick slam: impact at tick 20, an eight-tick shockwave ring expanding to 7 blocks, block-crack particles along it, you thrown out and up, screen shake. |
| 0:13–0:21 | Close on the head, then over the shoulder | `/colossus hp 60` | The bar turns red, the phase-2 roar plays (boss invulnerable through it, so nothing can interrupt the take), then twelve ash bombs over 24 ticks leaving 3×3 fire patches that put themselves out after 80 ticks. |
| 0:21–0:29 | Mid shot on the floor between you and the boss | *(wait; `/colossus status` off camera reads the summon timer)* | Three Ashen Minions claw out of the ground — a schedule, not a roll, so it lands when the cooldown says. Boss looms behind them. |
| 0:29–0:38 | Close on the boss, then pull back to a wide | `/colossus hp 30` | Screen darkens, the cracks go red-hot, +50 % speed, and a ring of fire starts closing from 20 blocks to 6 at 0.35 blocks/s. The HUD gauge under the crosshair shows the floor left and turns red with `GET BACK INSIDE THE RING` when you step out. |
| 0:38–0:41 | Hero shot, you mid-swing | `/colossus stagger` | The current attack is cancelled, the 40-tick stagger clip plays and the boss takes double damage — hit it here for the big number. |
| 0:41–0:45 | Wide, end card | `/colossus kill` | A 70-tick collapse; the loot table and an 800-XP burst land on the **last** tick, out of a corpse rather than a standing boss. Every fire patch is put out on the first tick of the collapse. |

### Re-shoot helpers

```
/colossus phase 2          # replays the entrance roar and bar recolour, in either direction
/colossus phase 3
/colossus hp 100
/colossus arena set main 12    # moves the ring under a live boss, mid-take
/colossus arena clear main
/colossus status               # health, phase, current attack, every cooldown, minions alive
```

### Plan drift

* The plan types `/boss arena set main 20`, `/boss spawn main`, `/boss hp 60`, `/boss kill`. The
  root is **`/colossus`**; the verbs and arguments are otherwise identical.
* `/colossus arena set [name] [radius]` takes radius 6–64 and defaults to 20; a bare
  `/colossus spawn` reuses the arena you last worked with.

---

## 3 — Power Kit (`powers`)

*Verified against* `features/powers/command/PowerCommand.java` and
`features/powers/ability/AbilityRegistry.java`. *Plan:* `plans/03-power-kit.md` §10.

**Set:** a row of hay bales to streak past, a fenced pen with five zombies, a stone floor for the
pound, a creeper across a 15-block gap, three skeletons with line of sight, and a mixed crowd
(hostiles plus a cow or a villager, so the freeze visibly spares them).

**Keys:** `R` `F` `G` `V` `C` `X` = slots 1–6, listed in Options > Controls under
*Creator Mods: Power Kit*.

### Setup — run these before you roll

```
/creator silent true
/toolkit hide chat true
/power clear @s
/power hud radial
```

`clear` is what makes the first frame empty: the HUD row draws nothing at all until the player has
at least one ability.

### Beats

| t | Camera is looking at | Type / press | You should see |
|---|---|---|---|
| 0:00–0:04 | Third person from behind, hotbar and the empty row above it in frame | `/power all @s` | Six slots pop in one at a time, four ticks apart, each ready-tinted in its own colour and each with the ready chime. Row width is reserved from frame one, so nothing slides. |
| 0:04–0:09 | First person for the launch, cut to third-person side for the trail | press **R** — Dash | You travel 10–12 blocks along your look (flattened unless sneaking), an end-rod trail for 8 ticks, fall distance zeroed, slot 1 sweeps for 3 s. |
| 0:09–0:15 | First person at the pen | press **F** — Fire Burst | A 7 m / 70° cone paints over 3 ticks; the five zombies take 6 damage, burn for 4 s and are shoved backwards. Slot 2 sweeps for 5 s. |
| 0:15–0:22 | Third person, low, side on | jump **first**, then press **G** mid-air — Ground Pound | Pops up for 6 ticks, rockets straight down, radius-6 shockwave: 8 damage at the epicentre falling to 2 at the rim, everything thrown out and up, block-crack rings. On the ground it is refused and the slot shakes — a usable shot in itself. |
| 0:22–0:28 | First person, crosshair held on the creeper | press **V** — Ender Pull | A purple beam both ways and the creeper lands at your feet. A miss is refused and costs no cooldown, so re-aim and go again. |
| 0:28–0:36 | Third person, front, skeletons already shooting | press **C** — Shield Dome | Four golden hearts, a radius-4 particle shell, inbound arrows voided with a sparkle, a creeper explodes harmlessly — and a zombie can still walk up and chip you, which keeps the shot readable. Ten seconds. |
| 0:36–0:43 | First person inside the mixed crowd, then walk through it | press **X** — Mob Freeze | Every hostile within 12 blocks stops dead for 5 s with the powder-snow shiver and a snowflake coat. The cow and the villager keep walking. They thaw together. |
| 0:43–0:45 | HUD close-up (crop in post) | — | All six slots ready-tinted, corner ticks visible. |

### Shooting the HUD without waiting out a 20-second dome

```
/power cooldown set @s creator_powers:shield_dome 200
/power cooldown set @s creator_powers:dash 40
/power hud linear
/power cooldown reset @s
```

`/power cooldown set <targets> <ability> <ticks>` parks a sweep at any fill from 0 to 72000 ticks.
`/power use <player> creator_powers:ground_pound` fires an ability on cue for a second player,
bypassing grant and cooldown, and prints nothing.

Ability ids: `creator_powers:dash`, `:fire_burst`, `:ground_pound`, `:ender_pull`, `:shield_dome`,
`:mob_freeze`. A bare path (`/power give @s dash`) resolves too — the command retries an
unqualified id in this feature's namespace.

### Between takes

```
/power cooldown reset @s
/power clear @s
```

### Plan drift

* The plan types `/power all`, which is real: with no targets it grants to you. `/power all @s`
  and `/power give @s all` are the same command.
* The plan's `commands.gif` shows `/power all` under silent mode — that still works, but the
  reply now lands on the action bar rather than in chat.
* **Camera shake on the pound impact was never built** (plan 03 lists it under Stretch). Do not
  cut for a shake; the impact reads through the block-crack rings and the anvil hit.

---

## 4 — Rule Engine (`rules`)

*Verified against* `features/rules/command/RuleCommand.java`,
`features/rules/rules/*.java` (rule ids) and
`common/src/main/resources/data/creator_rules/rule_presets/` (preset names). *Plan:*
`plans/04-rule-engine.md` §10.

**Set:** a stone face to mine over the shoulder, a crafting table, a flat patch you can stand
still on, a dirt bank, a spawn egg on the hotbar, a ledge about 6 blocks up, and an **emerald
block** placed on set for the shop beat.

### Setup — run these before you roll

```
/creator silent true
/rule clear
/rule hud on
/rule list
/gamemode survival
```

`lava_floor`, `blocks_explode` and `no_stop_moving` all exempt creative and spectator — frame in
creative, switch to survival before you roll.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:06 | Over the shoulder, stone face | `/rule random_drops on`, then mine three blocks | A `random_drops` row appears in the gold `RULES` list top-right, highlighted yellow for two seconds. Stone drops the same wrong item every time — the mapping is seeded from the world seed. |
| 0:06–0:11 | Close on the crafting table output | `/rule crafts_x10 on`, craft sticks | Four sticks become forty; the overflow drops at your feet. |
| 0:11–0:17 | Third person (F5) | `/rule lava_floor on`, stand still two seconds, then run | The block under you turns to lava; running leaves a glowing trail behind you. |
| 0:17–0:22 | Wide enough to show the build behind you | `/rule blocks_explode on`, mine dirt | A radius-2 explosion that hurts you but does **not** eat the build and does **not** destroy the drop you just made. |
| 0:22–0:27 | Low angle | `/rule giant_mobs on`, then use a zombie spawn egg | The zombie arrives already 3× size with 3× health — it is never seen at normal size. |
| 0:27–0:32 | Side on at the ledge | `/rule gravity_x3 on`, jump off | You drop noticeably fast and a three-block fall now hurts. |
| 0:32–0:36 | HUD, title-card area of frame | `/rule item_roulette on` then `/rule item_roulette fire` | The title card and sound land on the frame you press it, and an item is given or taken. Left alone the rule fires every 60 s, which is unshootable in one take. |
| 0:36–0:41 | Hearts row, then the shop GUI | `/rule hearts_currency on` then `/shop` — or right-click the emerald block | The shop window opens; buying costs two points of max health per heart, and your heart row shrinks on camera. A purchase that would take you under one heart is refused. |
| 0:41–0:45 | HUD, then the chat/action-bar readout | `/rule preset chaos` then `/rule list` | Six rows fill the list at once, **each** highlighted yellow, and the list prints active rules first with their one-line descriptions. |

### Between takes

```
/rule clear
/rule reload          # only after editing rule_presets/*.json, shop.json or the tags
/rule hud off         # thumbnails; writes the gamerule creator_rules.rulesHud
```

Presets that ship: `chaos`, `family_friendly`, `speedrun_hard`. `/rule preset list` prints them.
`/rule inventory_shuffle fire` is the other on-cue rule. `/rule <id> toggle` is the shape to put on
a stream-deck button, because it needs no knowledge of the current state.

Rule ids, all eleven: `random_drops`, `crafts_x10`, `lava_floor`, `blocks_explode`, `giant_mobs`,
`gravity_x3`, `item_roulette`, `hearts_currency`, `one_heart`, `no_stop_moving`,
`inventory_shuffle`.

### Plan drift

None. Plan 04's shot list is real syntax throughout. The only addition is `fire`: the plan's
"interval 100 for the shoot" note is not a thing you can set — `/rule item_roulette fire` is how
the beat lands on cue.

---

## 5 — Evolve (`evolve`)

*Verified against* `features/evolve/command/EvolveCommands.java` and
`features/evolve/stage/Stages.java`. *Plan:* `plans/05-evolve.md` §10. Needs GeckoLib 4.9.2.

**Set:** outdoors with open sky (at 2.6× the third-person camera clips into ceilings), a zombie
penned next to your mark for scale, a two-block staircase, a three-block wall, and something to
fall off for the Stomp.

### Setup — run these before you roll

```
/creator silent true
/evolve reset @s
/gamemode survival
```

F1 off — the stage bar, the `+N EVO` pop-ups and the `STAGE n` title card are the shot.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:05 | Third person, you beside the penned zombie | *(nothing — `/evolve info` off camera for the voiceover numbers)* | Hatchling at 0.60×, waist-high to the zombie. Bottom-left: a green stage bar, `Hatchling`, `0 / 100`. |
| 0:05–0:12 | First person for the kills, cut to third person on the third one | *(kill three zombies)* | `+15 EVO` pops per kill; the third crosses 100 and the transformation fires on its own — 40 ticks of three-armed helix, an amethyst chime ramping in pitch, a white flash and a `STAGE 2 / RUNT` title card. You are pinned for the sequence. |
| 0:12–0:20 | Third person over the shoulder | `/evolve xp @s 400` | **One** sequence straight to Brute (stage 3) rather than two in a row. Then punch a zombie: 1.5× attack one-shots it with a wooden sword, and a sprinting hit throws it (Charge). |
| 0:20–0:30 | Wide side shot along the staircase and the wall | `/evolve set @s 4` | Titan: walks up the two-block step without jumping (step height 2.0), clears the three-block wall, and landing from 3+ blocks deals 6 damage in a 3-block radius (Stomp). |
| 0:30–0:42 | Wide low angle, outdoors, second player in frame for scale | `/evolve set @s 5` then `/evolve roar` | The long 60-tick Apex sequence, then the beast model replaces your body for **everyone** who can see you, at 2.6× and 4.68 blocks tall. The roar clip plays and mobs within 10 blocks scatter with Weakness II. |
| 0:42–0:45 | Hold the same framing | `/evolve set @s 1` | Snaps straight back to Hatchling beside the beast's last footprint — stepping *down* never plays a sequence. |

### Cutaways and gags

```
/evolve fx @s start 120        # six seconds of helix, chime and flash with no stage change
/evolve fx @s stop
/evolve model @s on            # at stage 1: a Hatchling-sized beast
/evolve model @s off           # at stage 5: your own skin at 2.6x
/evolve model @s auto
/summon creator_evolve:apex_beast
/evolve info
```

`/evolve fx … start` is refused on a player who is genuinely mid-transformation, and
`/evolve fx … stop` never touches a real sequence — b-roll cannot break a take.

### Between takes

```
/evolve reset @s
```

### Plan drift

* Plan 05's shot list is real syntax, including the `/evolve set @s 1` button shot.
* **First-person arms are still player arms at Apex.** Film stage 5 in third person, as the plan's
  own beat does. Held items, armour, cape and elytra are not drawn at stage 5 either.
* Stage thresholds are 0 / 100 / 300 / 700 / 1500 — `/evolve xp @s 400` from stage 2 lands on
  Brute, which is what the 0:12 beat depends on.

---

## 6 — Event Director (`events`)

*Verified against* `features/events/command/EventCommand.java`,
`features/events/util/EventOptions.java` and the option reads in `features/events/events/*.java`.
*Plan:* `plans/06-event-director.md` §10.

**Set:** a hill with a clear horizon for the blood moon, a clearing about 20 blocks out to aim the
meteor into, open ground for the siege, and the edge of a ravine for the void rise.

### Setup — run these before you roll

```
/creator silent true
/event list
/event hud true
/gamerule doDaylightCycle true
```

Leave the daylight cycle **on** for this clip: the blood moon ends at dawn, and with the cycle off
it instead runs for a flat 6000 ticks. Only one event runs at a time — starting the next one stops
the previous one first, so you do not need `/event stop` between beats.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:08 | Wide from the hill at dusk | `/event start bloodmoon` | The sky floods red over 2 s, the clock time-lapses to dusk, every monster within 96 blocks glows through walls, the monster cap doubles and a low drone rolls in every 5 s. |
| 0:08–0:11 | Same framing, do not move | `/event skip` | The open `night` phase ends immediately, the 40-tick fade runs and dawn arrives on cue. |
| 0:11–0:22 | Aim at the clearing ~20 blocks out, then cut to the crater | `/event start meteor` | Origin defaults to the block you are looking at within 64 blocks. A 5-second countdown with title cards and a rising bell, a ring of flame on the target, a burning boulder on a straight line, a TNT-grade explosion, a scorched crater with a loot chest in it. |
| 0:22–0:31 | Third person, slow 360° pan | `/event start siege waves=2 radius=30` | Hostiles spawn 24–40 blocks out, out of sight, and converge on you. A red notched boss bar counts the wave down, a `WAVE 2/2` title lands between waves, and victory drops a reward. |
| 0:31–0:38 | Low angle looking up | `/event start luckyrain interval=10 duration=30` | Gold `?` blocks fall around every player every 10 ticks and burst into a random outcome — loot, a mob, an explosion, a command, an effect. |
| 0:38–0:45 | Standing on the ravine edge, HUD line in frame | `/event start voidrise speed=0.3 maxY=80` then `/event stop` | A black kill-plane climbs out of the world floor behind a curtain of ink and ash, with a HUD height readout and a proximity warning. `stop` clears the tint, the HUD line and everything the event made. Void rise destroys nothing, so stopping it restores the world by doing nothing. |

### Timing helpers

```
/event skip            # end the current phase now — countdown straight into impact
/event timer 3         # retime the running phase, in seconds
/event timer 10        # on voidrise this means "reach maxY in 10 s" and recomputes the speed
/event status          # event, phase, time in phase, wave n/N, alive count
/event stop
/event reload          # re-read lucky_outcomes and siege_waves JSON, without disturbing the event
/event hud false       # hides only our HUD line; the siege boss bar is vanilla and stays
```

**Options that exist** (whitespace-separated `key=value` after the name, and after the origin if
you give one; unknown keys are ignored, bad numbers fall back to the default):

| Event | Keys |
|---|---|
| `siege` | `waves` (1…table size), `radius` (6–128), `bossbar` (true/false) |
| `luckyrain` | `interval` (2–200 ticks), `radius` (2–48), `duration` (1–3600 s), `luck` (−5…5) |
| `voidrise` | `speed` (0.001–4 blocks/tick), `maxY`, `minY` |
| `bloodmoon`, `meteor` | none |

An explicit origin is a vanilla coordinate argument, so local coordinates work:
`/event start meteor ^ ^ ^20` drops it 20 blocks ahead of where you are facing.

### Plan drift

* Plan 06's shot list is real syntax. `maxY`, `speed`, `waves`, `radius`, `interval` and `luck` all
  exist; the plan's README option table also lists them for `bloodmoon` and `meteor`, which read
  no options at all.
* **Fog is not tinted, only the sky** — do not sell the blood moon on a fog shot.
* The blood-moon drone is a repeated one-shot, not a loop, so it does not cut off instantly on
  `/event stop`. Trim in the edit.

---

## 7 — Arsenal (`arsenal`)

*Verified against* `features/arsenal/command/ArsenalCommands.java` and
`features/arsenal/command/Weapon.java`. *Plan:* `plans/07-arsenal.md` §10.

**Set:** a ledge with a solid wall face 15–20 blocks away and roughly level (the pull ends about
1.5 blocks short of the anchor, so aim at a wall, not a ledge edge), a mob group on open ground for
the bow, a pen with about eight mobs for the hammer, a pack to sprint into for the scythe, and four
item frames for the tooltip pan.

### Setup — run these before you roll

```
/creator silent true
/arsenal give @s all
/arsenal cooldown reset @s
```

`give … all` hands over all four weapons plus 64 arrows. Single weapons:
`/arsenal give @s grapple_blade` · `storm_bow` · `gravity_hammer` · `soul_scythe`.

### Beats

| t | Camera is looking at | Type / do | You should see |
|---|---|---|---|
| 0:00–0:03 | First person, hotbar close-up | — | The four weapons in the bar, names coloured by vanilla rarity (aqua for the Rare blade, light purple for the three Epics). |
| 0:03–0:12 | Third-person side across the gap, then cut to first person | Right-click the **Grapple Blade** at the wall face; then right-click a zombie and slash | The hook flies up to 24 blocks, bites, a rope is drawn from your hand and you are reeled in within 10 ticks. Fall damage is suppressed for the flight and 10 ticks after. Hooking a mob pulls **you** to it. |
| 0:12–0:20 | First person on the hill | Hold the **Storm Bow** to a full draw and fire into the group; then fire a deliberate half draw | Full draw: a lightning bolt where the arrow lands, 10 damage at the centre falling to 4 at 3 blocks, flash, sparks, thunder — and nothing catches fire. Half draw: an ordinary arrow. That contrast is the beat. |
| 0:20–0:30 | Third person, low, at the pen; then a hotbar insert | Right-click the **Gravity Hammer** | Everything living within 6 blocks lifts on Levitation II — about 1.5 s of hang time, hold the shot — then slams down with a block-crack ring and takes 6 damage at your feet falling to 3 at the edge. The icon wipe in the hotbar is the 8-second cooldown. |
| 0:30–0:42 | Third-person chase cam | Take yourself down to a few hearts, then sprint into the pack with the **Soul Scythe** | A soul-fire trail while sprinting, 25 % of the damage dealt coming back as health on every hit, an always-on sweep clipping the neighbours, and soul wisps arriving as absorption hearts — capped at six golden hearts. |
| 0:42–0:45 | Slow pan over four item frames, GUI scale 3, F3+H off | — | Rarity-coloured name, a flavour line in the weapon's own colour, a grey mechanic line. |

### Panic buttons, mid-take

```
/arsenal hook retract @s      # a hook stuck somewhere it should not be
/arsenal slam cancel @s       # mobs left floating when a take is cut
/arsenal cooldown reset @s    # go again without waiting out the wipe
```

### Plan drift

None — plan 07's shot list is all mouse work and it matches. Two warnings from the code: lightning
comes only from a **full** draw (a short draw is why a take shows no bolt), and one hook per player
— firing again while a hook is live **cuts** it rather than firing a second. All art and audio in
this feature is a procedural placeholder; do not cut a thumbnail from the item sprites.

---

## 8 — Cursed Vault (`vault`)

*Verified against* `features/vault/command/VaultCommands.java` and
`features/vault/worldgen/VaultStructures.java`. *Plan:* `plans/08-cursed-vault.md` §10. Needs
GeckoLib 4.9.2.

**Set:** a generated vault. `/vault tp` searches 100 chunks (1600 blocks); at spacing 24 /
separation 8 there is usually one within 1000 blocks of spawn.

### Setup — run these before you roll

```
/vault tp
/vault status
/vault reset
```

Walk the route once before recording so you know which corridor reaches the treasure room. Then
teleport back to the entrance for the real take. Keep `/creator silent true` **off** for the first
beat only if you want the `/locate` coordinates readable in chat — otherwise shoot them as an
overlay in the edit.

### Beats

| t | Camera is looking at | Type on screen | You should see |
|---|---|---|---|
| 0:00–0:05 | Title card over the command output | `/locate structure creator_vault:cursed_vault` then `/vault tp` | The coordinates print (vanilla command, vanilla output). `/vault tp` drops you into the vault immediately and then, one second later once the chunks have loaded, moves you onto the altar itself. |
| 0:05–0:15 | First-person walk: shaft, corridor, trap room, doorway | — | The capped ladder shaft down, chained corridors, the trap room firing arrows out of its wall dispensers across the tripwire, then the doorway into the treasure room. End on a slow pan across the altar and the sealed chests. |
| 0:15–0:20 | Low, close on the altar | `/vault key`, then right-click the altar with the key | One key is consumed, the altar goes `sealed` → `charging`, the crystal rises and spins up across a 60-tick charge with a shrinking particle ring, and the HUD shows a charge bar and countdown. |
| 0:20–0:32 | Third person for ~3 s, then back to first person | *(fight)* | The Vault Keeper bursts out beside the altar: 80 HP, 8 attack, purple boss bar. Kiting it up the shaft does not work — past 48 blocks it teleports home every tick. |
| 0:32–0:40 | Close on the sealed chest, then the loot | *(kill the Keeper, then open the chest)* | Every recorded Sealed Chest is replaced on the same tick — obsidian particles, a crack — by a vanilla chest that rolls `creator_vault:chests/cursed_vault` the first time it is opened. |
| 0:40–0:45 | Wide on the dais, end card | `/vault reset` | Chests re-seal at their original facing, the Keeper is discarded and the altar re-arms to `sealed`. You can shoot the whole take again immediately — there is no cooldown. |

### Shortcuts while shooting

```
/vault key 16                 # 1..16 per command
/vault unseal                 # the loot beat with no fight; also the recovery path for a mined altar
/vault spawn_keeper           # fight somewhere photogenic; still binds to the nearest altar
/vault reset 64               # 1..128 block search radius, default 32
/vault status                 # altar position and state, charge, keeper countdown, chest and key counts
```

### Plan drift

* Plan 08 types `/locate structure creator:cursed_vault`. The real id is
  **`creator_vault:cursed_vault`**.
* The plan's `/tp` after `/locate` is replaced by `/vault tp`, which lands you on the altar rather
  than at the structure's nominal corner.
* Everything else in the plan's shot list matches, including the `/vault key` → right-click →
  Keeper → `/vault reset` loop.

---

## Appendix A — where each command lives

| Feature | Command classes |
|---|---|
| core | `core/command/CoreCommands.java` (`/creator`), `core/command/CommandHelper.java`, `core/command/SilentMode.java` |
| toolkit | `features/toolkit/command/{ToolkitCommands,TakeCommands,FreezeCommands,WaveCommands,ArenaCommands,CamCommands,CheatCommands,HideCommands,TpHereCommands}.java` |
| colossus | `features/colossus/command/ColossusCommand.java` |
| powers | `features/powers/command/PowerCommand.java` |
| rules | `features/rules/command/RuleCommand.java` |
| evolve | `features/evolve/command/EvolveCommands.java` |
| events | `features/events/command/EventCommand.java` |
| arsenal | `features/arsenal/command/{ArsenalCommands,Weapon}.java` |
| vault | `features/vault/command/VaultCommands.java` |

All paths are relative to `common/src/main/java/dev/riftal/creator/`.

## Appendix B — commands that exist only in the plans

Do not put any of these on screen.

| Written in a plan | Reality |
|---|---|
| `/take`, `/freeze`, `/wave`, `/arena`, `/camgo`, `/cheat`, `/hide` as roots | One root per feature: `/toolkit take …`, `/toolkit freeze …`, and so on |
| `on` / `off` as the toolkit's boolean words | `/toolkit …` takes `true` / `false` |
| `/camgo top 30` (eased glide) | `/toolkit cam go top 30` parses and is an **instant snap**; the glide was never built |
| `/boss …` | `/colossus …` |
| `/power reload`, per-ability config JSON | Not implemented; use `/power cooldown set` |
| Camera shake on Ground Pound impact | Not implemented |
| `item_roulette` interval setting | Not a setting; use `/rule item_roulette fire` |
| `/locate structure creator:cursed_vault` | `/locate structure creator_vault:cursed_vault` |
