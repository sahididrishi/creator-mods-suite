# creator_powers — asset inventory

Everything in this feature's `assets/` and `data/` trees, what it really is, and what an artist
would have to do to it. Nothing here claims to be finished art.

The Power Kit registers **no block, item, entity or particle** — so there is no blockstate, no
block/item model, no loot table, no recipe, no entity texture and no GeckoLib geo/animation JSON in
this namespace, and none is missing. It registers exactly **one** thing that needs a file on disk:
the sound event `creator_powers:ui.ability_ready`. Everything else it draws is the HUD cooldown row
(eight GUI sprites) plus the vanilla particles and vanilla `SoundEvents` the abilities use.

Regenerate every procedural placeholder below with:

```bash
python3 common/src/main/java/dev/riftal/creator/features/powers/tools/make_assets.py
```

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_powers/textures/gui/abilities/dash.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER (`tools/make_assets.py`) | Two speed chevrons on a pale-blue streak. Keep the silhouette readable at 16 px on a dark hotbar; a 2-value shape (body + highlight) beats a gradient. |
| `assets/creator_powers/textures/gui/abilities/fire_burst.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER | A leaning flame with a hot core. Orange `#FF9040` family; the core should read as a second, brighter value, not a blur. |
| `assets/creator_powers/textures/gui/abilities/ground_pound.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER | A heavy down-arrow striking a cracked stone line. Stone/sand `#C2A16B`; give the crack line real chipped pixels. |
| `assets/creator_powers/textures/gui/abilities/ender_pull.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER | A target reticle being yanked left by a hook or arrow. Ender purple `#B57FFF`. |
| `assets/creator_powers/textures/gui/abilities/shield_dome.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER | A translucent dome over a ground line, with a rim highlight. Cyan `#6FE3FF`; it must not read as the vanilla shield item. |
| `assets/creator_powers/textures/gui/abilities/mob_freeze.png` | 16×16 RGBA PNG | PROCEDURAL PLACEHOLDER | A six-spoke snowflake. Near-white `#DCF3FF`; keep at least one pixel of dark outline or it disappears on a snowy background. |
| `assets/creator_powers/textures/gui/slot.png` | 32×32 RGBA PNG sheet; the frame is the **top-left 22×22**, the rest transparent | PROCEDURAL PLACEHOLDER | The idle slot cell. Should sit next to the vanilla hotbar without matching it exactly — this row is the mod's, not vanilla's. Keep the frame in the top-left 22×22 (`PowerHudLayer.FRAME` / `FRAME_SHEET`) or move both constants with it. |
| `assets/creator_powers/textures/gui/slot_ready.png` | 32×32 RGBA PNG sheet, same layout | PROCEDURAL PLACEHOLDER | The "ready" cell: a brighter rim and the four corner ticks. The ticks exist so ready/not-ready is legible **without colour vision** — keep a non-colour cue of some kind. |
| `assets/creator_powers/sounds/ui/ability_ready.ogg` | 0.45 s Ogg **Vorbis**, **stereo**, 44.1 kHz (ffmpeg two-note sine chime) | PROCEDURAL PLACEHOLDER (`tools/make_assets.py`, needs ffmpeg) | A short, bright two-note bell that survives being heard six times in a stagger. Ship it **mono, 44.1 kHz** — see the limitation below. |
| `assets/creator_powers/sounds.json` | JSON, 1 event | HAND-WRITTEN, FINAL | — |
| `assets/creator_powers/lang/en_us.json` | JSON, 30 keys | HAND-WRITTEN, FINAL | Translate it. Ability names (`ability.creator_powers.*`), the key category and six key names (`key.*`), 15 command strings (`commands.creator_powers.*`), the chime subtitle. |
| `data/creator_powers/structure/empty.nbt` | GameTest template, 9×9×9 polished-andesite floor | PROVIDED BY THE SCAFFOLD — do not overwrite | — |
| `creatormods-powers.mixins.json` | JSON, one mixin | HAND-WRITTEN, FINAL | — |

## Known limitations of the placeholders

* **The chime is stereo.** The ffmpeg on this machine has no `libvorbis` and the native `vorbis`
  encoder is stereo-only (CONTRACT.md §9.2). Minecraft plays a stereo file non-positionally — which
  costs nothing here, because this sound is only ever played through
  `SimpleSoundInstance.forUI(...)` on the client that owns the HUD. **The replacement asset must
  still be mono 44.1 kHz**, so that it keeps working if the chime is ever moved to a world sound.
* **The icons are flat two-value shapes with a hard outline.** Deliberate: they have to read at
  20 px through a translucent cooldown sweep. They are obviously placeholder — no shading, no
  anti-aliasing, one accent colour each.
* **The accent colours are duplicated in Java.** Each `Ability#hudColor()` returns the same ARGB as
  the icon's accent in `tools/make_assets.py` (`ACCENTS`). Re-colour an icon and change the matching
  `hudColor()` in `ability/impl/`, or the slot tint and the sprite will disagree.

## What is deliberately *not* an asset

* **The cooldown sweep** is procedural: `client/SweepRenderer.java` draws it with `GuiGraphics#fill`
  only (a 2 px-cell radial pie, or a linear bar). No sprite, no `BufferBuilder`, no custom render
  type — which is what keeps it identical on both loaders and under Sodium/Embeddium.
* **Every ability's world VFX** uses vanilla particles (`END_ROD`, `FLAME`, `SMALL_FLAME`, `BLOCK`,
  `EXPLOSION`, `REVERSE_PORTAL`, `PORTAL`, `ENCHANTED_HIT`, `SNOWFLAKE`, `CLOUD`) and vanilla
  `SoundEvents`. No custom `ParticleType` is registered, so there is no particle JSON to ship.
* **No creative tab**, because the feature adds no items (CONTRACT.md §8).
