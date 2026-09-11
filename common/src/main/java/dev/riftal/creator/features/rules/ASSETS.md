# creator_rules - asset inventory

The Rule Engine is commands, a HUD layer, six mixins and a pile of data-pack JSON. It registers
**no blocks, no items, no entity types, no sound events, no particles and no creative tab**, so it
ships **no textures, no models, no blockstates, no loot tables, no GeckoLib geo/animations and no
`.ogg` files** - and therefore has no `tools/` generator script, because there is nothing
procedural to generate.

Every sound the feature plays is a **vanilla** `SoundEvent` looked up through
`net.minecraft.sounds.SoundEvents`, so no `sounds.json` exists or is needed:

| Rule / surface | Vanilla sound used |
|---|---|
| `lava_floor` melting a block | `SoundEvents.LAVA_POP` (`minecraft:block.lava.pop`) |
| `item_roulette` give | `SoundEvents.EXPERIENCE_ORB_PICKUP` |
| `item_roulette` take | `SoundEvents.ITEM_BREAK` |
| `inventory_shuffle` | `SoundEvents.NOTE_BLOCK_PLING` |
| hearts shop purchase | `SoundEvents.ANVIL_USE` |
| hearts shop refusal | `SoundEvents.ITEM_BREAK` |
| HUD toggle toast (client) | `SoundEvents.UI_BUTTON_CLICK` |

`/shop` is a plain three-row `ChestMenu`, so it uses the **vanilla generic 9x3 container texture**
and needs no screen class, no menu type and no GUI sprite sheet. The price tags are vanilla item
stacks with a `DataComponents.LORE` line.

## Files this feature ships

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_rules/lang/en_us.json` | JSON | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/shop.json` | JSON (data pack) | HAND-WRITTEN, FINAL - balance pass welcome | - |
| `data/creator_rules/rule_presets/chaos.json` | JSON (data pack) | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/rule_presets/speedrun_hard.json` | JSON (data pack) | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/rule_presets/family_friendly.json` | JSON (data pack) | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/tags/block/lava_floor_immune.json` | JSON tag | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/tags/entity_type/no_giant.json` | JSON tag | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/tags/item/never_random.json` | JSON tag | HAND-WRITTEN, FINAL | - |
| `creatormods-rules.mixins.json` | JSON | HAND-WRITTEN, FINAL | - |
| `data/creator_rules/structure/empty.nbt` | GameTest template | PROVIDED BY THE SCAFFOLD | - |

Every tag entry is written as `{ "id": ..., "required": false }` on purpose: a vanilla id that
moves or disappears in a future version degrades the rule instead of breaking tag loading for the
whole pack.

## HUD typography

`ActiveRulesHud` draws through `core.hud.HudText` with the **vanilla font**, right-aligned at
`guiScaledWidth - 4`. The header is `RULES` in gold (`0xFFFFAA00`), entries are white
(`0xFFFFFFFF`) and a rule toggled in the last two seconds is drawn in yellow (`0xFFFFFF55`).

Two strings in the lang file use non-ASCII glyphs that **are** in the vanilla font sheet and have
been kept deliberately: `⚠` in `hud.creator_rules.move` (action bar, `no_stop_moving`), `❤` in
`container.creator_rules.shop.cost` / `.bought`, and `✔`/`✘` in the `/rule` command feedback and
`/rule list` rows. No font asset is required for any of them.

## If art is ever added

There is no placeholder art to replace. If a future version gives the engine its own icon (for a
mod-list entry or a README badge) it belongs at `assets/creator_rules/icon.png`, 64x64 RGBA, and
this table gains a row saying so. Nothing in this feature may claim to be finished art when it is
not.
