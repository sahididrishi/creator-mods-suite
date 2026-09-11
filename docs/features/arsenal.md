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
| **Storm Bow** (`creator_arsenal:storm_bow`) | 600 durability · Epic, glinting · vanilla bow draw | A **full draw** shoots a Storm Arrow that calls a lightning bolt where it lands: 10 damage at the centre falling to 4 at 3 blocks, 0.8 outward push, plus a flash, sparks and thunder. The bolt is **visual-only**, so it starts no fires, ignites no blocks, charges no creepers, and any victim that was already burning is put out. A **half draw is an ordinary arrow** — that contrast is the shot the clip wants. |
| **Gravity Hammer** (`creator_arsenal:gravity_hammer`) | 12 damage · 0.8 speed · Epic, glinting · 1800 uses | **Right-click** lifts every living thing within 6 blocks (up to 32 of them) with Levitation II for 30 ticks and a purple pulse, then strips the effect, throws everything down at 1.8 blocks/tick with 4 blocks of padded fall distance, and hits each victim on touchdown for 6 damage at your feet falling to 3 at the edge, with a ring of block-crack particles. Players in creative or spectator are never lifted; items, arrows and minecarts are not living, so they stay put. **160 tick (8 s) cooldown**, which is what plays the vanilla icon wipe. |
| **Soul Scythe** (`creator_arsenal:soul_scythe`) | 9 damage · 1.2 speed · +0.75 sweeping ratio · +0.5 reach · Epic, glinting | Every hit **heals 25 % of the damage actually dealt** (zero during a target's invulnerability window, so it cannot be farmed by spamming). It **always sweeps** — neighbours of the struck target inside vanilla's own sweep box take half the damage and a knockback — regardless of vanilla's grounded/not-sprinting rules. Anything that **dies** to it (struck or swept) releases a soul wisp that chases you for 20 ticks and lands as **2 absorption hearts, capped at 6**. Sprinting with it in the main hand leaves a soul-fire trail. |

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
   vanilla rarity — yellow for the Rare blade, light purple for the three Epics — and under it each
   weapon has a flavour line in its own colour (aqua, light purple, light purple, dark aqua) and a
   grey mechanic line.

### Things that will bite you on camera

* One hook per player. Firing again while a hook is live **cuts** it rather than firing a second.
* The hook gives up in water, past 24 blocks of travel, past 32 blocks from you, or after 100 ticks.
* The grapple pull is server-driven velocity. On LAN it is smooth; over a >100 ms connection it can
  rubber-band slightly.
* Lightning only comes from a **full** draw. If the clip shows no bolt, the draw was short.
* The Storm Bow has no draw animation yet — the model does not change while you pull. See
  `ASSETS.md` in the feature folder for why.
* All art and audio in this feature is a **procedural placeholder**. Do not ship a thumbnail of the
  item sprites.
