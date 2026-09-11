# Rule Engine (`rules` / `creator_rules`)

> `/rule random_drops on` and the next block you mine drops a saddle. The top-right HUD lists what
> is on, `/rule preset chaos` sets up a whole episode in one command, and everything survives a
> restart.

Eleven stackable "Minecraft but…" rules you can flip on and off mid-take, without a restart,
without a resource reload and without a single line of chat noise. Plan: `plans/04-rule-engine.md`.

---

## What it does

Rules are toggles, not modes: they stack. `random_drops` + `giant_mobs` + `gravity_x3` all at once
is the point. Every rule is one class implementing `Rule`, registered in
`RulesFeature#registerContent()`, driven by `RuleManager`, and each one undoes itself exactly when
switched off.

| id | one line | notes that matter on camera |
|---|---|---|
| `random_drops` | Every block and every mob drops a different item — the same wrong item every time. | Seeded from the **world seed**, so the same world gives the same mapping on every launch and a new world gives a new one. Takes effect on the very next block: no `/reload`, no one-second stall. A block that drops nothing still drops nothing. Honours `#creator_rules:never_random`. |
| `crafts_x10` | Every craft yields ten times as much. | Nine extra copies are pushed into the inventory, dropping at your feet when it is full. Crafting table, 2×2 grid and shift-click all work; stonecutter and smithing table are untouched. |
| `lava_floor` | Stand still for two seconds and the block under your feet turns to lava. | Keep moving and you leave a glowing trail. Creative/spectator exempt. `#creator_rules:lava_floor_immune` and unbreakable blocks survive, so the set you built stays standing. |
| `blocks_explode` | Every block you mine explodes. | Radius 2, `ExplosionInteraction.NONE` — it hurts you, it does **not** eat the build, and it does **not** destroy the drop you just mined (dropped items and XP orbs are exempt from the blast damage, so it composes with `random_drops`). 5-tick per-player cooldown so a fast miner cannot chain-explode. Creative/spectator exempt. |
| `giant_mobs` | Every mob is 3× its size with 3× the health. | Applied on the tick a mob joins the level, so a spawn is never seen at normal size; a 20-tick sweep is the backstop for mobs arriving from a chunk load. Re-applying after a chunk cycle does **not** heal the mob. `#creator_rules:no_giant` is respected (warden, ender dragon, villagers…). Fully undone on disable. |
| `gravity_x3` | Gravity ×3, and short falls hurt. | `generic.gravity` 0.08 → 0.24 and `generic.safe_fall_distance` 3 → 2. Re-applied on join and respawn. |
| `item_roulette` | Every 60 seconds the game gives you an item or takes one away. | Title card + sound both ways. Full stack a quarter of the time. The clock is stored as an absolute game time, so a relog does not reset it. `/rule item_roulette fire` rolls it on cue. |
| `hearts_currency` | Hearts are money. Spend them in `/shop`. | Two points of maximum health per heart. A purchase that would leave you under one heart is refused. The debt survives death. Right-clicking any block in `#creator_rules:shop_blocks` (an emerald block by default) opens the same window — sneak to build against it instead. |
| `one_heart` | You have one heart. | `-18` max health, re-applied on join and respawn. Composes with `hearts_currency` (the shop refuses to take you under one heart). |
| `no_stop_moving` | Standing still hurts. | 2 s of stillness, then 1 damage every 10 ticks. An action-bar `⚠ MOVE` warning at the halfway mark so it never feels unfair. 5 s of grace on join and respawn. Creative/spectator exempt. |
| `inventory_shuffle` | Your inventory reshuffles every 30 seconds. | The 36 main slots only — armour and off-hand are never touched, so it cannot silently unequip you mid-fight. `/rule inventory_shuffle fire` shuffles on cue. |

### The HUD

Top-right, right-aligned, header `RULES` in gold and one line per active rule. A rule toggled in
the last two seconds is highlighted yellow and clicks — including every rule a preset flips, so
`/rule preset chaos` fills the list with six highlighted rows instead of repopulating it silently.
It moves down while the tab list is open, disappears with F1 like every other HUD element, and
`/rule hud off` removes it entirely for thumbnails.

The switch behind `/rule hud` is the boolean gamerule **`creator_rules.rulesHud`**, so a data pack,
a server operator or the world-creation screen can hide the list without an op typing a command:
`/gamerule creator_rules.rulesHud false` does the same thing and every client updates at once.

### Persistence

Active rules, the HUD flag and each rule's private timers are stored per world in
`<world>/data/creator_rules.dat`. On the first tick of a loaded world every persisted rule has its
`onEnable` run for real, so a restarted world is never "active on paper, inert in practice".

---

## Registered content

The Rule Engine registers **no blocks, no items, no entity types, no sound events, no particles and
no creative tab** — it is commands, a HUD layer, ten mixins and data-pack JSON. What it does
declare:

| Registry / mechanism | Id |
|---|---|
| Game rule (boolean, `MISC`) | `creator_rules.rulesHud`, default `true` — the HUD switch behind `/rule hud` |
| Player data attachment | `creator_rules:hearts_spent` (int, survives death — the `hearts_currency` debt) |
| Payloads (S2C) | `creator_rules:rules_sync`, `creator_rules:rule_toast` |
| Saved data | `creator_rules` in the level's data storage (`<world>/data/creator_rules.dat`) |
| Rules | the eleven ids in the table above, in `RuleRegistry` — a plain ordered list, not a vanilla registry |

The game rule is registered through two `@Invoker` mixins, because `GameRules#register` and
`GameRules.BooleanValue#create` are private in 1.21.1. If that ever fails the feature logs a warning
and falls back to the `RuleSavedData` flag; `/rule hud` keeps working either way.

## Commands

Feedback goes through `core.command.CommandHelper`, so it lands on the action bar when
`/creator silent true` is on and **never** broadcasts `[Player: …]` to other operators.

The root `/rule` is ungated so that `list`, `status` and `shop` are usable by everyone; every child
that changes the world carries permission level 2 itself.

| Command | Perm | What it does |
|---|---|---|
| `/rule` | 0 | Same as `/rule status`. |
| `/rule list` | 0 | Every registered rule, active ones first, with its one-line description. |
| `/rule status` | 0 | `Active (3): random_drops, crafts_x10, giant_mobs` |
| `/rule shop` | 0 | Opens the hearts shop (alias of `/shop`, for packs where another mod owns `/shop`). |
| `/shop` | 0 | Opens the hearts shop. Refused with a reason while `hearts_currency` is off. |
| `/rule <name> on` | 2 | Switches one rule on. Tab-completes from the registry. |
| `/rule <name> off` | 2 | Switches it off, undoing everything it did. |
| `/rule <name> toggle` | 2 | Flips it. Handy on a macro key or a stream-deck button. |
| `/rule <name> fire` | 2 | Runs a timer rule's effect right now. `item_roulette` and `inventory_shuffle` answer to it; every other rule reports that it has nothing to fire. |
| `/rule preset <name>` | 2 | Applies a data-pack preset. Tab-completes from the loaded packs. |
| `/rule preset list` | 0 | `Presets: chaos, family_friendly, speedrun_hard` |
| `/rule clear` | 2 | Switches everything off. One key back to vanilla. |
| `/rule reload` | 2 | Re-reads presets, `shop.json` and the tag-filtered item pool from the data packs, without a full `/reload`. |
| `/rule hud on` \| `off` | 2 | Shows or hides the rule list on every client. Writes the `creator_rules.rulesHud` gamerule. |

Toggling an unknown rule is an error, not a silent no-op (`Unknown rule 'x'`). Toggling a rule that
is already in that state reports `… is already in that state` and changes nothing — enabling twice
never runs `onEnable` twice.

---

## Config

The Rule Engine has no config file of its own. It has exactly one key, in the shared
`config/creatormods.json`:

```json
{ "features": { "rules": true } }
```

`false` and the whole feature is gone: no commands, no HUD, no payloads, no mixin side effects
(every mixin's first statement is `CreatorMods.isEnabled("rules")`). That is the one-key clean
capture the suite exists for. `/creator features` lists the state and
`/creator feature rules false` writes the key; both need a restart to take effect.

Everything else that would normally be config is a **data pack** instead — see below — so it can be
changed per world and reloaded without restarting.

---

## Data-pack surface

All under `data/creator_rules/` and all overridable by a world or server data pack.

### Presets — `rule_presets/<name>.json`

```json
{ "rules": ["random_drops", "crafts_x10", "giant_mobs"], "replace": true }
```

`replace: true` (the default) switches off everything the list does not name first, so one command
sets up a whole episode. `replace: false` only adds. Rule ids this build does not know are dropped
with a warning rather than failing the file. Three presets ship:

| preset | rules |
|---|---|
| `chaos` | `random_drops`, `crafts_x10`, `blocks_explode`, `giant_mobs`, `gravity_x3`, `item_roulette` |
| `speedrun_hard` | `one_heart`, `no_stop_moving`, `gravity_x3` |
| `family_friendly` | `crafts_x10`, `giant_mobs`, `item_roulette` |

Add a file, run `/rule reload`, and it tab-completes immediately.

### Shop — `shop.json`

```json
{ "offers": [ { "item": "minecraft:netherite_sword", "count": 1, "hearts": 3 } ] }
```

At most 27 offers (a three-row chest). An offer with a non-positive price or a silly count is
skipped with a warning; if the whole file is unreadable the built-in catalogue is used, so a typo
can never leave a creator with an empty shop mid-take.

### Tags

| tag | used by | meaning |
|---|---|---|
| `tags/item/never_random.json` | `random_drops`, `item_roulette` | Items that are never handed out. Ships with bedrock, barriers, command blocks, spawners, the debug stick… |
| `tags/block/lava_floor_immune.json` | `lava_floor` | Blocks the floor rule refuses to melt. Ships with bedrock, obsidian, every chest/barrel/shulker box and every bed. |
| `tags/entity_type/no_giant.json` | `giant_mobs` | Mobs left at their normal size. Ships with the ender dragon, wither, warden, elder guardian, ghast, ravager, iron golem and villagers. |
| `tags/block/shop_blocks.json` | `hearts_currency` | Blocks that open the heart shop when right-clicked. Ships with the emerald block. |

Every entry is `"required": false`, so a vanilla id that disappears in a future version degrades
the rule instead of breaking tag loading for the whole pack.

---

## Recording with it

* **Set up the episode in one command.** `/rule preset chaos` before you hit record; `/rule clear`
  between takes. Both are silent-mode aware.
* **Turn silent mode on.** `/creator silent true` moves every `/rule` reply to the action bar, so
  the chat box stays empty in frame and other ops on the server see nothing.
* **The HUD is the on-screen state.** Leave it on for the take (viewers can see what is active and
  each toggle flashes yellow for two seconds), `/rule hud off` for thumbnails and B-roll, F1 for
  everything at once.
* **Bind `/rule <name> toggle` to a macro key.** It is the one command shape that works without
  knowing the current state, which is what you want mid-take.
* **`random_drops` is stable per world.** Pre-record a take, watch it back, re-shoot it — stone
  drops the same wrong item every time. Want a different joke? New world, or change the world seed.
* **`blocks_explode` will not eat your build.** It is `ExplosionInteraction.NONE` on purpose, so the
  set survives and you do not have to re-shoot the wide shot.
* **Mind the two-second `lava_floor` timer** while lining up a static shot — creative mode is exempt,
  so frame in creative and switch to survival when you roll.
* **`item_roulette` fires every 60 s** and shows a title card by design. If you need it on cue,
  `/rule item_roulette fire` rolls it on the frame you press the key (and restarts the clock);
  `/rule inventory_shuffle fire` does the same for the shuffle.
* **Put an emerald block on the set.** With `hearts_currency` on, right-clicking it opens the shop,
  which reads far better than typing `/shop`. Sneak-right-click still builds against it.
* **Multiplayer**: the HUD syncs on join, so a guest who arrives mid-episode sees the right list.

---

## Adding a rule (about an hour)

1. Write one class in `features/rules/rules/` implementing `Rule`: `id()` plus whichever of
   `onEnable` / `onDisable` / `tick` / `onPlayerJoin` / `onPlayerRespawn` / `stripFrom` /
   `onBlockBroken` / `onCraftTaken` / `onEntityJoin` / `onPlayerChangedDimension` / `fire` /
   `remapBlockDrops` / `remapMobDrops` / `save` / `load` you actually need. `LavaFloorRule` is ~60 lines end to end.
2. `RuleRegistry.register(new YourRule());` in `RulesFeature#registerContent()`.
3. Two lines in `assets/creator_rules/lang/en_us.json`: `rule.creator_rules.<id>` and
   `rule.creator_rules.<id>.desc`.
4. Optionally a tag under `data/creator_rules/tags/` for the things it should leave alone.

The rules that matter: **`onDisable` must undo everything `onEnable` did**, every attribute
modifier is transient and keyed by a `ResourceLocation` in `RuleIds` so it can be removed exactly,
and anything that must survive a restart goes in `save`/`load`, not in a static field.

---

## What it deliberately does not do

* **No per-rule config file and no `/rule reload` of Java constants.** Timings, damage and
  multipliers are compiled in (`rules/*.java`); only the presets, the shop and the tags are
  data-pack surface, and `/rule reload` re-reads exactly those.
* **No per-player rules.** Every rule is world-wide by design — a rule that was on for one player
  and off for another would be impossible to read off the HUD on camera.
* **No custom GUI.** The hearts shop is a plain three-row `ChestMenu` with vanilla item stacks and
  lore price tags, so there is no screen class, no menu type and no sprite sheet to ship.
* **No blocks, items, entities or sounds of its own.** Every sound the engine plays is a vanilla
  `SoundEvent`; the only registry entry it adds is the `creator_rules.rulesHud` gamerule.
* **`inventory_shuffle` never touches armour or the off-hand**, and `crafts_x10` never touches the
  stonecutter or the smithing table. Both are deliberate limits, not gaps.
* **Rule state is per world, not per save-slot-and-player.** `/rule clear` is the only "back to
  vanilla" button; there is no undo history.

## Tests

* JUnit (`common/src/test/java/dev/riftal/creator/features/rules/`) — `RandomDropsMappingTest`
  (determinism, bijection, blacklist), `StillTrackerTest`, `RulePresetTest`, `ShopOffersTest`
  (affordability boundaries), `RuleSavedDataTest` (NBT round trip, dirty flags),
  `RuleRegistryTest` (registration order, duplicate replacement), `RulePayloadCodecTest`
  (wire format of both S2C payloads), `RuleManagerTest` (preset replace/add semantics and
  enable idempotency, via the pure `RulePresetPlan`).
* GameTest (`RulesGameTests`, mirrored in the Fabric and NeoForge holders) — gravity, one-heart,
  giant-mob grow/shrink and no-heal-on-regrowth, `random_drops` mapping stability and drop
  remapping, `crafts_x10` insertion, `lava_floor` melt rules, `blocks_explode` sparing both the
  build and the drops plus its per-player cooldown, `inventory_shuffle` permutation,
  `/rule preset` driving the live engine, and three data-pack tests that the shipped presets, tags
  and `shop.json` actually load and name real content.
