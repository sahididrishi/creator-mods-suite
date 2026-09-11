# Power Kit (`powers`)

> Six keybind superpowers - dash, fire burst, ground pound, ender pull, shield dome, mob freeze -
> granted per player by command, with server-authoritative cooldowns and a cooldown row above the
> hotbar that sweeps, flashes and chimes on camera.

| | |
|---|---|
| Feature id | `powers` |
| Resource namespace | `creator_powers` |
| Root command | `/power` (op level 2 to mutate, level 0 to read) |
| Default keys | `R` `F` `G` `V` `C` `X` (slots 1-6, rebindable) |
| Plan | [`plans/03-power-kit.md`](../../../plans/03-power-kit.md) |
| Code | `common/src/main/java/dev/riftal/creator/features/powers/` |
| Assets inventory | [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/powers/ASSETS.md) |

---

## What it does

A player starts with **nothing**: no HUD, no keys that do anything. `/power all @s` hands them the
six abilities in canonical slot order, the row of six slots appears above the hotbar, and each key
fires its slot.

Everything that matters is decided on the server. The client predicts a cooldown start the instant
you press a key so the sweep begins on that frame, and the server's reply either confirms it or
silently snaps the sweep back to the truth - a refused press never writes a line of chat, because
chat noise mid-take is exactly what this suite exists to remove.

### The six abilities

All numbers are server-authoritative and hard-coded (see the cooldown table in
`ability/impl/*.java`; every one of them is asserted in `AbilityRegistryTest`).

| Slot | Key | Ability | Cooldown | What it does |
|---|---|---|---|---|
| 1 | `R` | **Dash** | 60 t (3 s) | Throws you ~10-12 blocks along your look direction (flattened unless you are sneaking, which makes it a full 3D dash). Opens an **8-tick melee i-frame window** so you can dash through a mob pile untouched (arrows, explosions and fire still land), and zeroes fall distance. End-rod trail for 8 ticks. Refused in water, in lava or in a vehicle. |
| 2 | `F` | **Fire Burst** | 100 t (5 s) | A **7 m / 70°** cone in front of you: 6 damage, 4 s of burning and a shove backwards for everything living inside it. Damage is `indirectMagic` attributed to you, so kills still credit the creator. 50 flame particles painted over 3 ticks so it reads as a sweep. Refused underwater. |
| 3 | `G` | **Ground Pound** | 160 t (8 s) | **Airborne only.** Pops you up for 6 ticks, then rockets you straight down. The landing is a radius-6 shockwave: 8 damage at the epicentre falling off to 2 at the rim, everything thrown outwards and up, block-crack rings in whatever you landed on. Cancels elytra flight first. If you never land (water, void) the state expires after 40 ticks. |
| 4 | `V` | **Ender Pull** | 120 t (6 s) | Ray-casts 20 blocks along the crosshair and yanks the first living thing it finds to your feet - pull speed capped at 2.2 with `+0.35` of lift so the target clears one-block lips instead of overshooting. Purple beam both ways. **Refused if nothing is under the crosshair**, so a miss never burns the cooldown. Bosses are exempt. |
| 5 | `C` | **Shield Dome** | 400 t (20 s) | 10 seconds of cover: 8 absorption (4 golden hearts), Resistance II, +10 armour and +4 armour toughness as *transient* modifiers, and a particle shell on a radius-4 Fibonacci sphere. Hostile projectiles flying **inward** inside radius 4.5 are voided with a sparkle. Projectile and explosion damage to you is cancelled outright; **melee still lands** (reduced) so the shot stays readable. Everything it gave is handed back exactly on expiry - including only the absorption it added, so a golden apple's hearts survive. |
| 6 | `X` | **Mob Freeze** | 300 t (15 s) | Every hostile (`Enemy`) mob within 12 blocks stops dead for 5 seconds, pinned in place with the vanilla powder-snow shiver and a snowflake coat. Cows, villagers and your horse carry on. Bosses exempt, 64-mob cap. Each mob's original `NoAI` / `NoGravity` flags are recorded - in the session and as entity tags on the mob itself - and restored on thaw, on the caster's logout or death, when the chunk unloads and after a crash, so a build prop that was already `NoAI` stays that way. |

### The HUD row

Six 20 px slots centred above the hotbar, one per **granted** ability in slot order.

* **Nothing is drawn at all** until the player has at least one ability - no permanent row of empty
  grey boxes on a fresh world.
* Each slot is a frame sprite plus the ability's 16 px icon. A ready slot's frame is tinted in that
  ability's accent colour, and carries four corner ticks so ready/not-ready reads without colour
  vision.
* The cooldown draws as a **clockwise radial pie** (default) or a **linear bottom-up bar**
  (`/power hud linear`), with the remaining seconds in the middle. Both are procedural - no sprite,
  no `BufferBuilder` - so they behave identically on both loaders and under Sodium/Embeddium.
* Freshly granted slots **pop in one at a time**, four ticks apart, each with the ready chime - the
  row's full width is reserved from the first frame, so the icons land in place instead of the row
  sliding sideways under them. Slots the client already had (a respawn, a dimension change, a
  re-sync) are not re-animated.
* A use flashes the slot white; a press during cooldown **shakes** the slot two pixels instead of
  sending a packet.
* A press the **server** turns down - a ground pound on the ground, an ender pull with nothing under
  the crosshair, a press the client thought was ready and was not - shakes the slot too and stays
  silent. The refusal is a flag on the cooldown packet: without it the correction is
  indistinguishable from "this slot is ready" and the chime fires at a player who was just told no.
* Crossing back into ready plays `creator_powers:ui.ability_ready` once, edge-detected per slot.
* The row honours **F1** and `/power hud off`.

Cooldowns are stored as an absolute "ready at this game tick" value and synced with the server's own
clock, so a sweep is exact, survives a relog at the right fill, and never drifts on a laggy server.

---

## Registered content

The Power Kit registers **no blocks, no items, no entity types, no particle types and no creative
tab**. In full:

| Registry / mechanism | Id |
|---|---|
| Sound event | `creator_powers:ui.ability_ready` (the per-slot ready chime) |
| Player data attachment | `creator_powers:powers` — loadout + cooldowns, `copyOnDeath` |
| Payload (C2S) | `creator_powers:use_ability` |
| Payloads (S2C) | `creator_powers:sync_powers`, `creator_powers:cooldown_start`, `creator_powers:hud_mode` |
| Key mappings | `key.creator_powers.slot1` … `slot6`, category *Creator Mods: Power Kit* |

The six abilities live in `AbilityRegistry`, which is a plain ordered list rather than a vanilla
registry: the set is fixed, it is not data-driven and nothing needs a network id for it.

## Commands

Root `/power`. Mutating nodes require **op level 2**; `list` and `hud` are open to **level 0** so a
guest on a LAN world can check their own loadout and hide the row for their own capture. All
feedback goes through the suite's silent-mode helper: it never broadcasts `[Player: ...]` to other
ops, and `/creator silent true` moves it to the action bar.

| Command | Perm | Effect | Feedback |
|---|---|---|---|
| `/power give <targets> <ability>` | 2 | Grants one ability into the next free slot. | "Granted Dash to 1 player(s)" |
| `/power give <targets> all` | 2 | Grants all six in canonical order. | "Granted 6 abilities across 1 player(s)" |
| `/power all [<targets>]` | 2 | Alias of `give all`; targets default to the executing player. | as above |
| `/power clear <targets>` | 2 | Revokes everything, clears cooldowns, empties the HUD and drops any dome/pound that player owned. | "Cleared powers for 1 player(s)" |
| `/power remove <targets> <ability>` | 2 | Revokes one; the slots after it shift left. | "Removed Dash from 1 player(s)" |
| `/power cooldown reset [<targets>] [<ability>]` | 2 | Everything ready now. No targets = you; no ability = all six. | "Cooldowns reset for 1 player(s)" |
| `/power cooldown set <targets> <ability> <ticks>` | 2 | Forces a specific *remaining* cooldown, 0-72000. **This is the one for filming the sweep at a chosen fill.** | "Dash cooldown set to 30 ticks" |
| `/power use <targets> <ability>` | 2 | Fires it now, bypassing the grant and the cooldown (but not `canUse`). **Silent by design** - this is the trigger-on-cue command. | none, unless nothing could fire |
| `/power list [<target>]` | 0 | Prints the loadout with per-slot state. | "Pega: Dash ready, Fire Burst 37t" |
| `/power hud <on\|off\|radial\|linear>` | 0 | Client-side presentation switch for the executing player only. | none |

`<ability>` is a resource location with tab completion over the six ids
(`creator_powers:dash`, `:fire_burst`, `:ground_pound`, `:ender_pull`, `:shield_dome`,
`:mob_freeze`). A bare path works too - `/power give @s dash` - because an unqualified id parses as
`minecraft:dash` and is retried in this feature's own namespace; nobody tab-completes with the
camera running. An unknown id answers, in red and only to you, `Unknown ability 'x'. Known: dash,
fire_burst, ...`. A seventh grant answers `All 6 slots are full; use /power remove first`.

---

## Config keys

| Key | File | Effect |
|---|---|---|
| `features.powers` | `config/creatormods.json` | `false` removes the feature entirely: no payloads, no `/power`, no attachment, no keys in Options → Controls, and the damage mixin returns immediately. One key, one restart, a clean single-feature capture. |

There is **no per-ability config file**. Cooldowns and damage numbers are the plan's balance table,
compiled in; a tunable JSON plus `/power reload` is a stretch goal that was not built. Use
`/power cooldown set` when you need a different number for one shot.

The HUD mode (`on`/`off`/`radial`/`linear`) is per-client and per-session, set with `/power hud`;
it is not written to disk. Key bindings live in vanilla `options.txt` under the
**"Creator Mods: Power Kit"** category and persist across restarts like any vanilla key.

---

## Recording with it

The 45-second clip this feature exists to shoot:

1. **Set up off camera.** `/creator silent true`, `F3+B` off, chat hidden. `/power clear @s` so the
   row is empty for the first frame.
2. **Roll, then `/power all @s`.** The six icons pop in one by one, about four ticks apart, each
   ready-tinted and each with its own chime - roughly a second for the full row.
3. **Dash (`R`)** toward something you can streak past - the trail reads best third-person, side on.
4. **Fire Burst (`F`)** into a penned group; the cone paints over 3 ticks, so hold the shot a beat.
5. **Ground Pound (`G`)** - jump *first*, tap in mid-air. On the ground it is refused and the slot
   shakes, which is a fine shot in itself if you want to show the refusal.
6. **Ender Pull (`V`)** with the crosshair on a mob 15 blocks out. If it refuses, you were not
   actually on the mob: the ray-cast has to hit, and a miss deliberately costs nothing.
7. **Shield Dome (`C`)** with skeletons already shooting: arrows sparkle out mid-air, a creeper does
   nothing, a zombie can still walk up and chip you.
8. **Mob Freeze (`X`)** in a mixed crowd, then walk between them. They thaw together after 5 s.
9. **HUD close-up.** `/power cooldown set @s creator_powers:dash 40` (and friends) parks each sweep
   at the fill you want, so you can shoot the row without waiting out a real 20-second dome.

Useful mid-take:

* `/power use <player> <ability>` fires an ability on cue for someone else - a second player, or
  yourself from a command block - with no cooldown and no chat.
* `/power cooldown reset @s` between takes, so take 2 starts identical to take 1.
* `/power hud off` for a clean plate; `/power hud linear` if the radial pie is hard to read at a
  small GUI scale.

### Known limits

* **Bosses are exempt** from Ender Pull and Mob Freeze (ender dragon, wither, warden, elder
  guardian). That is deliberate, not a gap.
* **The dome does not reflect** projectiles; it voids them.
* **Cooldowns are absolute game ticks**, so they do not advance while the server is paused
  (`/tick freeze`) and they *do* advance while you are logged out.
* **Dash i-frames are melee only**: for those 8 ticks a direct hit from something standing next to
  you is swallowed, and arrows, explosions, fire, fall, magic and drowning all land as normal. The
  dash is there to carry you through a mob pile, not to out-tank the Shield Dome.
* **Ground Pound and Fire Burst skip armour stands, your own tamed animals and anyone vanilla would
  not let you hit** (PvP off, or a team-mate). Bosses take the damage but are never thrown.
* **Camera shake on the pound impact was not built.** Plan 03 lists it under Stretch ("client-side
  camera shake on Ground Pound impact"), so there is no `AbilityFxPayload` and no client shake
  timer; the impact reads through block-crack rings, the explosion particle and the anvil hit.
* Active effects (dome, freeze, an in-flight pound) are **not persisted**: logging out, dying and
  closing the world each end that player's dome (handing its absorption and armour straight back)
  and thaw the mobs that player froze. Only the loadout and its cooldowns survive a restart, and
  they survive death too (`copyOnDeath`).
* **`/reload` changes nothing mid-take.** It rebuilds the command tree, which is also how the
  per-tick driver is armed, so the driver is left alone when it is already running: a live dome, a
  frozen crowd and an in-flight dash trail all survive a reload.
* If a server dies mid-freeze, `NoAI` would normally persist in the mobs' NBT. Frozen mobs carry the
  entity tag `creator_powers_frozen` and a 200-tick sweep releases any tagged mob the current
  session does not own.

---

## How it is put together

| Piece | Where |
|---|---|
| The six abilities and their ordered registry | `ability/`, `ability/impl/` |
| Persisted loadout (`creator_powers:powers`, `copyOnDeath`) and pure cooldown maths | `data/PlayerPowers.java`, `data/CooldownMath.java` |
| Transient server state: domes, frozen mobs, i-frame windows, pounds | `effect/ActiveEffects.java` |
| The one place that decides whether an ability fires, plus the per-tick driver | `server/PowerManager.java` |
| C2S `use_ability`; S2C `sync_powers`, `cooldown_start`, `hud_mode` | `net/` |
| `/power` | `command/PowerCommand.java` |
| Key mappings, client mirror, HUD row, sweep | `client/` (client only) |
| Damage gate for the dome and dash i-frames, plus the dimension-change re-sync | `mixin/PowersServerPlayerMixin.java` |
| Join / respawn / logout hooks (HUD sync in 1 tick, effects handed back) | `mixin/PowersPlayerListMixin.java` |
| World-closing wipe, so no dome or frozen mob outlives its level | `mixin/PowersMinecraftServerMixin.java` |

The C2S packet carries nothing but an ability id and is never trusted: the server re-checks that the
ability exists, that the player was granted it, that it is off cooldown and that `canUse` passes,
and accepts at most 4 use packets per player per tick. A refused ability is additionally ignored for
4 ticks after the refusal has been answered, so a held key with nothing under the crosshair cannot
buy an unbounded number of Ender Pull ray-casts.

### Tests

* **JUnit** (`common/src/test/java/dev/riftal/creator/features/powers/`) - `CooldownMathTest`,
  `PlayerPowersTest`, `PlayerPowersCodecTest`, `AbilityRegistryTest`, `PowerPayloadTest`,
  `PowersAssetsTest`: the cooldown arithmetic, the pound falloff, the cone test, slot ordering and
  the 6-slot cap, the NBT codec (including old saves with no `ready_at`), the balance table, every
  payload round trip, and that every icon, slot sprite, `sounds.json` entry and lang key the code
  names actually ships.
* **GameTests** (`gametest/PowersGameTests.java`, one stub per loader) - granting and clearing, the
  full use/refuse/cooldown path, dash velocity plus the i-frame window, the fire cone hitting only
  what is in front, the pull moving its target, a pull with no target costing nothing, freeze
  holding hostiles and thawing them back to their original flags, the pound shockwave, the dome's
  buffs and arrow voiding, the dome expiry handing everything back, a `/reload` leaving a live dome
  alone while the world-closing wipe hands every buff back, a logout ending the dome and thawing
  that player's mobs, and the pound sparing armour stands and the caster's own pet.
