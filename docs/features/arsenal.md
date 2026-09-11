# Arsenal

> Four signature weapons, each with one unmistakable on-camera mechanic.
> Feature id `arsenal` · namespace `creator_arsenal` · plan: `plans/07-arsenal.md`

Arsenal is the "custom items, weapons, tools" showcase. Everything it does is a right-click or a
left-click — no GUI, no key binding, nothing to explain over voice-over — and every decision is made
on the server, so a second player on a LAN world sees the rope, the bolt, the lift and the souls
exactly as the recorder does.

## The four weapons

| Weapon | Stats (as the tooltip shows them) | What it does |
|---|---|---|
| **Grapple Blade** (`creator_arsenal:grapple_blade`) | 7 damage · 1.6 speed · Rare, glinting · 1200 uses, repairs with iron | **Right-click** fires a hook up to 24 blocks. It bites the first block or mob it touches, then reels you to it in at most 10 ticks (capped at 1.8 blocks/tick), with a rope drawn from your hand. **Right-click again cuts the line** — that path ignores the cooldown, so you can always get unstuck on camera. Fall damage is suppressed for the flight and for 10 ticks after you land. Hooking a mob pulls *you* to the mob, not the mob to you. |
| **Storm Bow** (`creator_arsenal:storm_bow`) | 600 durability · Epic, glinting · vanilla bow draw animation | A **full draw** shoots a Storm Arrow that calls a lightning bolt where it lands: 10 damage at the centre falling to 4 at 3 blocks, 0.8 outward push, plus a flash, sparks and thunder. The blast lands in full on the mob the arrow physically struck too — that one takes the arrow *and* the 10, not one or the other. The bolt is **visual-only**, so it starts no fires, ignites no blocks, charges no creepers, and any victim that was already burning is put out. A **half draw is an ordinary arrow** — that contrast is the shot the clip wants. |
| **Gravity Hammer** (`creator_arsenal:gravity_hammer`) | 12 damage · 0.8 speed · Epic, glinting · 1800 uses | **Right-click** lifts every living thing within 6 blocks (up to 32 of them) with Levitation II for 30 ticks and a purple pulse, then strips the effect, throws everything down at 1.8 blocks/tick with **4 blocks of fall distance added on top** (so even a short lift lands hard), and hits each victim on touchdown for 6 damage at your feet falling to 3 at the edge — in full, on top of the fall damage the slam itself caused — with a ring of block-crack particles (drawn for the first 8 victims to land; a 32-mob slam would otherwise be a particle-packet hitch). Players in creative or spectator are never lifted; items, arrows and minecarts are not living, so they stay put. **160 tick (8 s) cooldown**, which is what plays the vanilla icon wipe. |
| **Soul Scythe** (`creator_arsenal:soul_scythe`) | 9 damage · 1.2 speed · +0.75 sweeping ratio · +0.5 reach · Epic, glinting | Every hit **heals 25 % of the damage actually dealt** (zero during a target's invulnerability window, so it cannot be farmed by spamming). It **always sweeps** — neighbours of the struck target inside vanilla's own sweep box take half the damage and a knockback — regardless of vanilla's grounded/not-sprinting rules. On the swings where vanilla's *own* sweep fires (grounded, fully charged, not sprinting) that one is left to do the job, because it hits harder: it uses this weapon's +0.75 sweeping ratio, so ours would have been absorbed by the victims' invulnerability window while still playing a second arc and sound. Anything that **dies** to it (struck or swept) releases a soul wisp that chases you for 20 ticks and lands as **2 absorption hearts, capped at 6**. Sprinting with it in the main hand leaves a soul-fire trail. |

## Registered content

Everything this feature puts in a registry, and nothing else. All ids are in the `creator_arsenal`
namespace.

| Registry | Ids |
|---|---|
| Item | `grapple_blade`, `storm_bow`, `gravity_hammer`, `soul_scythe` |
| Entity type | `grapple_hook` (0.25³, no save, no summon), `storm_arrow` (0.5³, no summon) |
| Sound event | `arsenal.hook_bite`, `arsenal.slam_impact`, `arsenal.soul_absorb` |
| Creative tab | `creator_arsenal` — **Creator Arsenal**, icon Soul Scythe, holds the four weapons |
| Payload (S2C) | `creator_arsenal:grapple_fx` — cosmetic hook effects only |

No blocks, no block entities, no particle types, no attachments, no gamerules, no recipes and no
loot tables. The hook and the arrow are both `noSummon()`, so neither shows up in `/summon`
completion; the hook is also `noSave()`, so a world that is closed mid-flight comes back clean.

## Commands

Root `/arsenal`. Everything goes through the suite's `CommandHelper`, so feedback honours
`/creator silent true` (action bar instead of chat) and is **never** broadcast to other operators.

| Command | Arguments | Permission | What it does | Feedback |
|---|---|---|---|---|
| `/arsenal give <targets> <weapon>` | `targets`: players · `weapon`: `grapple_blade` \| `storm_bow` \| `gravity_hammer` \| `soul_scythe` (suggested) | 2 | Gives one of that weapon; the Storm Bow comes with 64 arrows. Full inventory drops the item at the player's feet. | "Gave Soul Scythe to Pega" |
| `/arsenal give <targets> all` | `targets`: players | 2 | Gives all four weapons, plus 64 arrows. | "Gave 4 weapons to Pega" |
| `/arsenal cooldown reset [<targets>]` | optional `targets`, defaults to you | 2 | Clears the item cooldown on all four weapons. | "Arsenal cooldowns reset for Pega" |
| `/arsenal hook retract [<targets>]` | optional `targets`, defaults to you | 2 | Discards live grapple hooks and drops the pull state — the panic button when a hook sticks mid-take. | "Retracted 1 hook(s)" |
| `/arsenal slam cancel [<targets>]` | optional `targets`, defaults to you | 2 | Removes levitation from everything a hammer is holding up and drops the slam. | "Cancelled 1 slam(s)" |

An unknown weapon name is refused in red — `Unknown weapon 'banana'. Options: grapple_blade,
storm_bow, gravity_hammer, soul_scythe` — and nothing is given.

## Config

Arsenal has no config file of its own. The one key that affects it is the suite's feature switch in
`config/creatormods.json`:

```json
{ "features": { "arsenal": true } }
```

`false` means the feature gets **no** lifecycle calls at all: no items, no entity types, no creative
tab, no `/arsenal`, no sounds. That is the clean-capture switch — turn the other seven off and
restart for a recording that contains nothing but this feature. The same thing from in game:

```
/creator features            # list the state of all eight
/creator feature arsenal true
```

Damage numbers, radii and cooldowns are constants in
`features/arsenal/mechanic/DamageMath.java`, `GravitySlam.java` and `GrappleBladeItem.java` — plan
07 lists a runtime config for them as a stretch goal, and it is not implemented.

## Recording with it

1. **Set up.** `/creator silent true`, then `/arsenal give @s all`. The tab
   **Creator Arsenal** (icon: the scythe) also holds all four for a creative-inventory shot.
2. **Grapple, 3–12 s.** Stand on a ledge with something solid 15–20 blocks away and roughly level
   with you; aim slightly above the target. The pull ends about 1.5 blocks short of the anchor, so
   aim at a wall face rather than a ledge edge. If a take goes wrong mid-flight,
   `/arsenal hook retract` frees you instantly, and `/arsenal cooldown reset` lets you go again
   without waiting out the 20 ticks.
3. **Storm Bow, 12–20 s.** Hold the draw to the end (the arrow only calls lightning at **full**
   draw), fire into a mob group on open ground, then follow it with a deliberate half-draw shot for
   the contrast. Nothing will catch fire, so it is safe over a wooden build — that is worth saying
   on camera.
4. **Hammer, 20–30 s.** Stand in the middle of a pen of eight or so mobs. The beat is 1.5 seconds
   of hang time, so hold the shot; the icon wipe in the hotbar is a good insert. If a take is cut
   while mobs are floating, `/arsenal slam cancel` puts them down, and `/arsenal cooldown reset`
   re-arms the hammer immediately.
5. **Scythe, 30–42 s.** Take yourself down to a few hearts first (`/damage` or a fall), then sprint
   into a pack: the trail, the hearts coming back on every hit, the sweep and the souls arriving as
   golden hearts all read in one continuous shot. Absorption caps at six golden hearts.
6. **Tooltips, 42–45 s.** Four item frames, GUI scale 3, F3+H off. The item name is coloured by
   vanilla rarity — aqua for the Rare blade, light purple for the three Epics — and under it each
   weapon has a flavour line in its own colour (aqua, light purple, light purple, dark aqua) and a
   grey mechanic line.

### Things that will bite you on camera

* One hook per player. Firing again while a hook is live **cuts** it rather than firing a second.
* The hook gives up in water, past 24 blocks of travel, past 32 blocks from you, or after 100 ticks.
* The grapple pull is server-driven velocity. On LAN it is smooth; over a >100 ms connection it can
  rubber-band slightly.
* Lightning only comes from a **full** draw. If the clip shows no bolt, the draw was short.
* The Storm Bow draws like a vanilla bow: the model switches through `storm_bow_pulling_0/1/2` at
  vanilla's own thresholds, so the full draw — the one that calls lightning — looks different from
  the half draw in first person. That is the contrast the 12–20 s beat is built on.
* All art and audio in this feature is a **procedural placeholder**. Do not ship a thumbnail of the
  item sprites.

## What it deliberately does not do

* **No runtime tuning.** Damage numbers, radii and cooldowns are constants in
  `mechanic/DamageMath.java`, `mechanic/GravitySlam.java`, `item/GrappleBladeItem.java` and
  `item/ArsenalTiers.java`. Plan 07's runtime config plus a `/arsenal reload` is a stretch goal and
  is not built; `/arsenal cooldown reset` is the only live knob.
* **No `grapple_hook` item.** Plan 07's registry table lists one as an icon for the hook renderer,
  but `GrappleHookRenderer` draws a camera-facing quad textured straight from
  `textures/entity/grapple_hook.png`, so an item would be an unobtainable registry entry nothing
  reads. The texture ships; the registry entry is intentionally absent.
* **No GeckoLib models.** Both entities render with vanilla-style renderers (a quad plus a
  `RenderType.lineStrip()` rope for the hook, `ArrowRenderer` for the arrow). 3D scythe/hammer
  models are a plan stretch goal.
* **No Better Combat `weapon_attributes` JSON**, and no recipes — the weapons are command- and
  creative-tab-only by design.
* **The Storm Bow's bolt is cosmetic.** It starts no fires, ignites no blocks, charges no creepers,
  and puts out a victim that was already burning. That is a feature, not a missing hook.
* **No custom GameTest arenas.** Every test runs in the shared 9×9×9 `empty.nbt`; plan 07's
  `arena_16x8` and `cliff_gap` templates need a dev client and were not built.

## Tests

| Kind | Where | What it covers |
|---|---|---|
| JUnit | `common/src/test/java/dev/riftal/creator/features/arsenal/` | `DamageMathTest` (lightning and slam falloff, lifesteal, sweep ratio, absorption cap, grapple pull maths), `ArsenalTiersTest` (uses, speed, damage bonus, enchantability, repair ingredients), `WeaponArgumentTest` (the `Weapon` enum ids, lookup and the unknown-id path), `WeaponTooltipsTest` (tooltip lines and colours), `GrappleFxPayloadTest` (payload round trip) |
| GameTest | `common/src/gametest/java/dev/riftal/creator/features/arsenal/gametest/ArsenalGameTests.java` | 16 bodies: feature enabled; rarity and attribute components on all four weapons; `/arsenal give … all` plus the 64 arrows; the unknown-weapon refusal; a charged Storm Arrow striking without fire and an uncharged one never striking; the blast landing in full on the mob the arrow hit; the hammer lifting everything in range and slamming after the hang time; slam damage surviving the fall-damage invulnerability window; the scythe healing and sweeping, not double-sweeping on top of vanilla's own sweep, and granting absorption after the wisp; the hook biting and reeling, a second use cutting the line, and the reel carrying the player across a gap |
| Loader stubs | `fabric/src/gametest/java/.../ArsenalFabricGameTests.java`, `neoforge/src/gametest/java/.../ArsenalNeoForgeGameTests.java` | Every test that asserts on health, fire, effects, position or motion carries its own `batch` on both loaders — batch-mates run simultaneously in arenas only 14 blocks apart and share one `ServerLevel`, so a neighbouring feature's area effect reaches into ours. |

Assets: see [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/arsenal/ASSETS.md).
