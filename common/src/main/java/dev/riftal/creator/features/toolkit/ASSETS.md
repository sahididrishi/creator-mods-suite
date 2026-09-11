# creator_toolkit — asset inventory

Everything in this feature's `assets/` and `data/` trees, what it really is, and what an artist
would have to do to it. Nothing here claims to be finished art.

**This feature ships no art at all.** The Director's Toolkit is commands, one HUD line and five
mixins: it registers no block, item, entity, sound, particle or creative tab, so there is no
texture, model, blockstate, loot table, geo/animation JSON or `.ogg` to replace — and therefore no
`tools/` generator script either. Everything below is hand-written text, and it is final.

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_toolkit/lang/en_us.json` | JSON, 56 keys | HAND-WRITTEN, FINAL | Translate it. Every string the feature can put on screen is here: `commands.creator_toolkit.*` for command feedback, `hud.creator_toolkit.*` for the clapperboard, `feature.creator_toolkit.*` for the feature list. |
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

If the feature ever grows a real key mapping, it will also grow one key-binding translation key
(`key.creator_toolkit.mark` + a category) in the same lang file — still no art.
