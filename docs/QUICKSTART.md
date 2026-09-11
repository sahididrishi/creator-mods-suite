# Quickstart

Five minutes from a freshly installed jar to a boss on the floor, a superpower on a key and a blood
moon in the sky.

Every command below was taken from the command classes in `common/src/main/java/dev/riftal/creator/`
and works as written. Deeper reference: one page per feature in [`features/`](features/), and the
recording shot lists in [`media/SHOTLIST.md`](media/SHOTLIST.md).

---

## 0 · Install (about a minute)

**Minecraft 1.21.1**, one of:

| Loader | Jar | Also needs |
|---|---|---|
| Fabric | `creatormods-fabric-1.21.1-0.1.0+1.21.1.jar` | Fabric Loader 0.19.5+, Fabric API, **GeckoLib 4.9.2** |
| NeoForge | `creatormods-neoforge-1.21.1-0.1.0+1.21.1.jar` | NeoForge 21.1.250+, **GeckoLib 4.9.2** |

GeckoLib is a **required** dependency on both loaders — the Ashen Colossus, the Apex beast and the
Vault Keeper are GeckoLib models and the game will refuse to start without it.

Drop the jar in `mods/`, launch once, then quit. That first launch writes
`config/creatormods.json` with all eight features on.

Now make a world: **Creative**, **cheats on**. Single player with cheats is permission level 4, and
almost everything in this suite needs level 2. On a dedicated server, `op <yourname>` first.

---

## 1 · See what is on (30 seconds)

```
/creator features
```

Eight rows, each id in green when it is enabled and dark grey when it is not:

```
 - toolkit Director's Toolkit
 - colossus Ashen Colossus
 - powers Power Kit
 - rules Rule Engine
 - evolve Evolve
 - events Event Director
 - arsenal Arsenal
 - vault Cursed Vault
```

**Turning one off** — the single-feature capture switch. A disabled feature registers *nothing*: no
commands, no items, no HUD, no packets, no mixin side effects.

```
/creator feature colossus false
```

This writes `config/creatormods.json` and **takes effect at the next game start**, never
immediately. Turn it back on the same way:

```
/creator feature colossus true
```

One more core command, and the only one you need before you hit record:

```
/creator silent true
```

Command replies move from chat to the action bar and stop being broadcast to other operators.
Failures stay visible, in red, to you only.

---

## 2 · Spawn the boss (1 minute)

Stand somewhere open with a stone, basalt or deepslate floor — the fight rains fire, and fire
patches burn wood. Then:

```
/colossus arena set main 20
/colossus spawn main
```

`arena set` saves an arena at your feet (name defaults to `main`, radius defaults to 20, range
6–64) and persists per level, so you only do it once. `spawn` raises the boss out of the floor over
three seconds, plays the entrance roar and puts a yellow boss bar on screen.

Now drive the fight from the console. Every one of these acts on the nearest living Colossus within
128 blocks of you:

```
/colossus hp 60        # straight to phase 2: red bar, lava rain, minions
/colossus hp 30        # phase 3: enrage, screen darkens, ring of fire closes in
/colossus phase 2      # replay a phase entrance, in either direction, without respawning
/colossus stagger      # 40 ticks of reeling, double damage taken — the hero-shot window
/colossus status       # health, phase, current attack, every cooldown, minions alive
/colossus kill         # 70-tick collapse, then loot and an 800 XP burst
```

One catch worth knowing immediately: **the boss will not target a creative or spectator player.**
Switch to `/gamemode survival` to be attacked; stay in creative to fly through the arena as a
second camera, where the slam, the roar, the combo, the bombs and the ring all skip you.

---

## 3 · Grant a power (1 minute)

```
/power all @s
```

Six ability slots pop in above the hotbar, one at a time, each with a ready chime. The keys, all
rebindable in Options > Controls under *Creator Mods: Power Kit*:

| Key | Ability | Cooldown | Note |
|---|---|---|---|
| `R` | Dash | 3 s | Sneak for a full 3D dash |
| `F` | Fire Burst | 5 s | 7 m / 70° cone in front of you |
| `G` | Ground Pound | 8 s | **Jump first** — on the ground it is refused |
| `V` | Ender Pull | 6 s | The crosshair has to be on something; a miss costs nothing |
| `C` | Shield Dome | 20 s | 10 s of cover, voids inbound arrows |
| `X` | Mob Freeze | 15 s | Hostiles within 12 blocks, 5 s |

Driving them from the console:

```
/power list                                          # your loadout and each slot's state
/power cooldown reset @s                             # everything ready now
/power cooldown set @s creator_powers:dash 40        # park a sweep at a chosen fill, for a HUD shot
/power use @s creator_powers:ground_pound            # fire on cue, no cooldown, prints nothing
/power give @s creator_powers:shield_dome            # one ability instead of all six
/power clear @s                                      # back to an empty row
/power hud linear                                    # radial | linear | on | off
```

A bare path works too: `/power give @s dash`.

---

## 4 · Start an event (1 minute)

```
/event list
/event start bloodmoon
```

The sky floods red over two seconds, the clock jumps to dusk, every monster within 96 blocks glows
through walls and the monster cap doubles. Then:

```
/event skip            # end the current phase now — here, straight to dawn
/event status          # event, phase, time in phase, wave n/N, alive count
/event stop            # tear it down; everything it made goes with it
```

The other four, with the options they actually read:

```
/event start meteor                                  # origin = the block you are looking at
/event start siege waves=2 radius=30
/event start luckyrain interval=10 duration=30
/event start voidrise speed=0.3 maxY=80
```

Only one event runs at a time — starting another stops the first — so you can cut between them
without `/event stop` in between. `/event timer <seconds>` retimes the running phase; on `voidrise`
it means "reach the ceiling in this long".

---

## 5 · One command from each of the rest (1 minute)

**Rule Engine** — eleven stackable "Minecraft but…" rules, all live, no restart:

```
/rule preset chaos          # six rules at once; also: family_friendly, speedrun_hard
/rule random_drops on
/rule item_roulette fire    # fire a timer rule on cue instead of waiting 60 s
/rule list
/rule clear                 # one key back to vanilla
```

**Arsenal** — four weapons, each one right-click or left-click, nothing to explain:

```
/arsenal give @s all        # all four plus 64 arrows
```

Right-click the Grapple Blade at a wall 15–20 blocks off; hold the Storm Bow to a **full** draw for
the lightning; right-click the Gravity Hammer in a crowd. `/arsenal cooldown reset @s` to go again,
`/arsenal hook retract @s` and `/arsenal slam cancel @s` are the panic buttons.

**Evolve** — five stages from a knee-high Hatchling to a 2.6× Apex beast:

```
/evolve info
/evolve set @s 5            # runs the full 3-second transformation; add `instant` to skip it
/evolve roar
/evolve reset @s
```

Film stage 5 in third person and outdoors — first-person arms are still player arms, and the
camera clips into ceilings at 2.6×.

**Cursed Vault** — an underground jigsaw dungeon with an altar, a mini-boss and re-sealing chests:

```
/vault tp                   # nearest generated vault within 1600 blocks; lands you on the altar
/vault key                  # right-click the altar with it: 3-second charge, then the Keeper
/vault status
/vault unseal               # skip the fight, open the chests now
/vault reset                # re-seal everything and shoot the take again
```

**Director's Toolkit** — the crew's console; nothing it does ends up in the footage:

```
/toolkit take start                          # red REC clapperboard, top left
/toolkit wave spawn zombie 24 10 ring        # 24 zombies on a 10-block ring, facing in
/toolkit freeze mobs true                    # a real pause: AI, gravity, fire, despawn timers
/toolkit arena save ring                     # 25x9x25 snapshot of blocks *and* props
/toolkit cam save hero                       # then /toolkit cam go hero to cut back to it
/toolkit hide hud true                       # clean frame for a thumbnail
/toolkit take stop                           # writes creator-toolkit/takes/<world>_<date>_take-NNN.log
```

Tap **`M`** during a running take to drop a timestamped mark without opening chat.

---

## Recording mode, in three commands

```
/creator feature <id> false    # x7, for the features you are not filming — then restart
/creator silent true           # replies to the action bar, no op broadcast
/toolkit hide chat true        # chat stops drawing; you can still type
```

Then follow the feature's section in [`media/SHOTLIST.md`](media/SHOTLIST.md).

---

## If something does not work

| Symptom | Cause |
|---|---|
| `/creator feature x true` changed nothing | It is written to disk and applied on the **next** game start. Restart. |
| A feature's command does not exist at all | That feature is off in `config/creatormods.json`, or you are not permission level 2. `/creator features` answers the first; `op <name>` the second. |
| Game will not start | GeckoLib 4.9.2 is missing, or Fabric API on Fabric. |
| **`M`** does nothing | Marks need a running take — `/toolkit take start` first. Also check the binding: Xaero's Minimap and JourneyMap both default to `M` too. |
| Ground Pound refused, slot shakes | It is airborne-only. Jump, then press `G`. |
| Ender Pull refused | Nothing was under the crosshair within 20 blocks. A miss deliberately costs no cooldown. Bosses are exempt. |
| The Colossus ignores you | It will not target a creative or spectator player. `/gamemode survival`. |
| No command feedback anywhere | `/toolkit hide hud true` hides the action bar along with the HUD. `/toolkit hide hud false`. |
| `No Cursed Vault found within 1600 blocks` | None generated nearby. Fly out and retry, or `/vault spawn_keeper` and `/vault key` work on any altar you place by hand. |
| Chat is silent and you want it back | `/creator silent false` (or `/toolkit hide commands false`). Both restore `sendCommandFeedback` and `logAdminCommands`. |

---

## Where to go next

| | |
|---|---|
| Per-feature reference | [`features/toolkit.md`](features/toolkit.md) · [`colossus`](features/colossus.md) · [`powers`](features/powers.md) · [`rules`](features/rules.md) · [`evolve`](features/evolve.md) · [`events`](features/events.md) · [`arsenal`](features/arsenal.md) · [`vault`](features/vault.md) |
| Recording | [`media/SHOTLIST.md`](media/SHOTLIST.md), [`media/README.md`](media/README.md) |
| Building it yourself | [`../README.md`](../README.md) |
| Writing code against the shared core | [`../CONTRACT.md`](../CONTRACT.md) |
