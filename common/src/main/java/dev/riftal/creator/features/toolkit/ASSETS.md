# creator_toolkit — asset inventory

Everything in this feature's `assets/` and `data/` trees, what it really is, and what an artist
would have to do to it. Nothing here claims to be finished art.

**This feature ships no art at all.** The Director's Toolkit is commands, one HUD line, one key
mapping and four mixins (`ToolkitMinecraftServerMixin` and `ToolkitServerLevelMixin` on both sides,
`ToolkitChatComponentMixin` and `ToolkitEntityRendererMixin` client only): it registers no block, item, entity, sound, particle or creative tab, so there is no
texture, model, blockstate, loot table, geo/animation JSON or `.ogg` to replace — and therefore no
`tools/` generator script either. Everything below is hand-written text, and it is final.

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_toolkit/lang/en_us.json` | JSON, 60 keys | HAND-WRITTEN, FINAL | Translate it. Every string the feature can put on screen is here: 48 `commands.creator_toolkit.*` for command feedback, 8 `hud.creator_toolkit.*` for the clapperboard, 2 `feature.creator_toolkit.*` for the feature list, and 2 `key.*` for the mark key and its category. |
| `data/creator_toolkit/structure/empty.nbt` | GameTest template, 9×9×9 polished-andesite floor | PROVIDED BY THE SCAFFOLD — do not overwrite | — |
| `creatormods-toolkit.mixins.json` | JSON | HAND-WRITTEN, FINAL | — |

## Why there is no font, icon or sound

* **The HUD** (`client/TakeHudLayer.java`) draws through `core.hud.HudText`, i.e. the vanilla font,
  and every glyph it uses in `en_us` is plain ASCII (`REC`, `---`, `TAKE`, `MARK`, `FROZEN`). No
  font asset, no sprite sheet, no colour texture: the four colours are ARGB constants in the layer.
  A translator may use any glyph the vanilla font sheet covers.
* **No sounds.** A recording tool that beeped would end up in the footage. Every piece of feedback is
  either a chat line, an action-bar line (silent mode) or the HUD flash.
* **No icon.** The plan sketched a 128×128 clapperboard for a standalone mod's `icon.png`; in this
  suite the mod icon is `common/src/main/resources/creatormods.png`, which is shared, owned by core
  and out of this feature's hands.

The feature does have one real key mapping — the mark key — and it costs no art either: it is two
lang lines (`key.creator_toolkit.mark` and `key.categories.creator_toolkit`) and nothing else.
Vanilla draws the entry in Options > Controls.
