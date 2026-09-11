# creator_vault — asset inventory

Everything in this feature's `assets/` and `data/` trees, what it really is, and what a real
artist should replace it with. Nothing here claims to be finished art.

Regenerate the procedural files:

```bash
python3 common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_textures.py
bash    common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_sounds.sh
python3 common/src/main/java/dev/riftal/creator/features/vault/tools/make_structures.py
```

## Textures

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_vault/textures/block/cursed_altar_base.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER (`tools/make_placeholder_textures.py`) | Carved obsidian/deepslate pedestal, rune ring glowing on the top face |
| `assets/creator_vault/textures/block/cursed_altar_crystal_sealed.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Dull, dormant crystal facets |
| `assets/creator_vault/textures/block/cursed_altar_crystal_charging.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Same facets, lit from inside, purple |
| `assets/creator_vault/textures/block/cursed_altar_crystal_active.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Fully lit, emissive; an `_emissive` overlay would be ideal |
| `assets/creator_vault/textures/block/cursed_altar_crystal_spent.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Cracked, grey, dead |
| `assets/creator_vault/textures/block/sealed_chest_side.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Obsidian crust over chest planks, wrapped in a chain |
| `assets/creator_vault/textures/block/sealed_chest_top.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Lid with a padlock and the chain crossing it |
| `assets/creator_vault/textures/item/vault_key.png` | 16x16 PNG | PROCEDURAL PLACEHOLDER | Ornate brass key, purple gem in the bow, 3/4 view |
| `assets/creator_vault/textures/entity/vault_keeper.png` | 64x64 PNG | PROCEDURAL PLACEHOLDER | Painted in the real vanilla humanoid skin layout (head/body/arm/leg UV nets each get their own tone, mortar borders, purple rune seams and two lit eyes) rather than a repeating tile. A real artist should carve actual stonework into it and add a glowmask for the eyes and seams |
| `assets/creator_vault/textures/entity/cursed_altar_crystal_{sealed,charging,active,spent}.png` | 32x32 PNG | PROCEDURAL PLACEHOLDER | The UV net for the altar crystal that `CursedAltarRenderer` animates. 32x32, not 16x16: the net of a 6x4x6 box is 24 px across. Faceted gem, emissive core |

## Models and blockstates

| File | Kind | Status |
|---|---|---|
| `assets/creator_vault/blockstates/cursed_altar.json` | JSON, one variant per `state` | HAND-WRITTEN, FINAL |
| `assets/creator_vault/blockstates/sealed_chest.json` | JSON, four `facing` rotations | HAND-WRITTEN, FINAL |
| `assets/creator_vault/models/block/cursed_altar_template.json` | JSON, 3-box pedestal + crystal | HAND-WRITTEN, FINAL - used by the **block item** only, which has no block entity and therefore no renderer to draw its crystal |
| `assets/creator_vault/models/block/cursed_altar_pedestal.json` | JSON, 2-box pedestal, no crystal | HAND-WRITTEN, FINAL - what the placed block draws; the crystal is `CursedAltarRenderer`'s |
| `assets/creator_vault/models/block/cursed_altar_{sealed,charging,active,spent}.json` | JSON | HAND-WRITTEN, FINAL |
| `assets/creator_vault/models/block/sealed_chest.json` | JSON, 14x15x14 box | HAND-WRITTEN, FINAL |
| `assets/creator_vault/models/item/*.json` | JSON | HAND-WRITTEN, FINAL |

## Sounds

All eight are **mono (1-channel), 44.1 kHz Ogg Vorbis** tones, so Minecraft gives them normal
positional distance attenuation. ffmpeg's native `vorbis` encoder is stereo-only, so they are
produced with `ffmpeg -ac 1 -ar 44100` piped into `oggenc` (vorbis-tools) rather than by ffmpeg
alone — see CONTRACT.md §9.2. The real replacements must stay **mono, 44.1 kHz Ogg Vorbis**.

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_vault/sounds/altar/activate.ogg` | 1.6 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER (ffmpeg sine) | Chains snapping taut + a rising crystal hum, **mono** |
| `assets/creator_vault/sounds/altar/unseal.ogg` | 1.1 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | The hum collapsing, crystal dropping into its socket |
| `assets/creator_vault/sounds/altar/reset.ogg` | 0.7 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | A short stone-and-metal re-arm click |
| `assets/creator_vault/sounds/chest/unseal.ogg` | 1.0 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | Obsidian cracking, chain falling to the floor |
| `assets/creator_vault/sounds/keeper/summon.ogg` | 1.8 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | Floor bursting, a deep inhale |
| `assets/creator_vault/sounds/keeper/idle.ogg` | 1.2 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | Low grinding groan, stone on stone |
| `assets/creator_vault/sounds/keeper/hurt.ogg` | 0.5 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | Stone chipping, short |
| `assets/creator_vault/sounds/keeper/death.ogg` | 2.0 s Vorbis, **mono** | PROCEDURAL PLACEHOLDER | Collapse into rubble, hum dying out |
| `assets/creator_vault/sounds.json` | JSON | HAND-WRITTEN, FINAL |

## Structure pieces

| File | Kind | Status | What a level designer should do |
|---|---|---|---|
| `data/creator_vault/structure/cursed_vault/entrance.nbt` | 11x24x11 template: entry hall plus a capped 3x3 ladder shaft in its corner | GENERATED BY SCRIPT (`tools/make_structures.py`) | Rebuild in-game with a structure block: dress the shaft with banners and rubble, break up the hall walls |
| `data/creator_vault/structure/cursed_vault/corridor_straight.nbt` | 5x5x8 | GENERATED BY SCRIPT | Vary the decay, add alcoves and skeleton props |
| `data/creator_vault/structure/cursed_vault/corridor_corner.nbt` | 5x5x5 | GENERATED BY SCRIPT | As above |
| `data/creator_vault/structure/cursed_vault/corridor_t.nbt` | 5x5x5 | GENERATED BY SCRIPT | As above |
| `data/creator_vault/structure/cursed_vault/corridor_end.nbt` | 5x5x3 | GENERATED BY SCRIPT | A proper dead-end: cave-in rubble, a broken lantern |
| `data/creator_vault/structure/cursed_vault/trap_room.nbt` | 11x6x11, two tripwire lines driving four arrow dispensers | GENERATED BY SCRIPT | Dress it: pressure-plate variants, a second trap type, skeleton props |
| `data/creator_vault/structure/cursed_vault/treasure_room.nbt` | 13x8x13 | GENERATED BY SCRIPT | Dress the dais, add candles, chains anchored to the four corners |
| `data/creator_vault/structure/cursed_vault/treasure_end.nbt` | 5x5x3 cap for the treasure anchor | GENERATED BY SCRIPT | A collapsed doorway: rubble, a bent iron door |
| `data/creator_vault/structure/empty.nbt` | 9x9x9 GameTest arena | SHIPPED BY THE SCAFFOLD - do not overwrite |

The pieces were written byte-for-byte in the vanilla structure-template format rather than exported
from a dev client, because no client is run in this pass. The geometry convention that makes the
jigsaws line up is documented at the top of `tools/make_structures.py`; keep to it if you rebuild a
piece by hand:

* doorways are the jigsaw block at `(centre, 1, edge)` plus plain air at `(centre, 2, edge)` **and
  `(centre, 3, edge)`** - three tall, because the Vault Keeper is 2.3 blocks tall and a 1x2 doorway
  physically cannot pass it;
* the jigsaw sits on the outermost block layer so a child piece never overlaps its parent;
* every corridor piece is 5 wide with its doorway at local x = 2, so any two of them connect.

## Data (hand-written JSON, no datagen anywhere)

Datagen is banned on this project (CONTRACT.md §7), so every file below was written by hand and is
guarded by `common/src/test/java/dev/riftal/creator/features/vault/VaultStructureDataTest.java`
and `VaultAssetsTest.java` - a pool that names a missing `.nbt`, a model that names a missing PNG,
a `sounds.json` entry with no `.ogg`, or a `Component.translatable` key with no lang line all fail
`./gradlew :common:test` instead of failing silently in the recording.

| File | Kind | Status |
|---|---|---|
| `assets/creator_vault/lang/en_us.json` | 49 keys: 7 block lines (including the four altar states and the sealed-chest message), 5 item lines, the entity, the tab, 2 feature-list lines, 8 subtitles, 8 HUD lines, 17 command lines | HAND-WRITTEN, FINAL |
| `data/creator_vault/worldgen/structure/cursed_vault.json` | `minecraft:jigsaw` | HAND-WRITTEN, FINAL |
| `data/creator_vault/worldgen/structure_set/cursed_vaults.json` | `random_spread` 24/8, salt 1830767658 | HAND-WRITTEN, FINAL |
| `data/creator_vault/worldgen/template_pool/cursed_vault/{entrance,corridors,corridor_ends,treasure,treasure_ends}.json` | 5 pools | HAND-WRITTEN, FINAL |
| `data/creator_vault/tags/worldgen/biome/has_structure/cursed_vault.json` | `#minecraft:is_overworld` | HAND-WRITTEN, FINAL |
| `data/creator_vault/tags/worldgen/structure/cursed_vault.json` | one entry; `/vault tp` resolves the structure through it | HAND-WRITTEN, FINAL |
| `data/creator_vault/loot_table/blocks/{cursed_altar,sealed_chest}.json` | block drops | HAND-WRITTEN, FINAL |
| `data/creator_vault/loot_table/chests/cursed_vault.json` | the reward chest: 1 enchanted book + 4-7 weighted rolls | HAND-WRITTEN, needs a balance pass, not an art pass |
| `data/creator_vault/loot_table/chests/cursed_vault_trap.json` | trap room: 1 guaranteed Vault Key + filler | HAND-WRITTEN, FINAL |
| `data/creator_vault/loot_table/entities/vault_keeper.json` | Keeper drops | HAND-WRITTEN, needs a balance pass |
| `data/creator_vault/recipe/vault_key.json` | shaped amethyst/gold/iron | HAND-WRITTEN, FINAL |

Feature documentation lives at `docs/features/vault.md`.

## Not shipped in this pass

* **No GeckoLib rig.** The plan asked for an animated `GeoBlockEntity` altar and a GeckoLib Keeper
  reusing plan 02's Ashen Minion rig. That rig *does* exist in this repo - at
  `assets/creator_colossus/geo/entity/ashen_minion.geo.json` - but it belongs to the `colossus`
  feature's namespace, and pointing this feature at another agent's asset means a rename over
  there silently breaks the mini-boss over here. Copying it into `creator_vault/` would be
  duplicating someone else's art rather than writing our own. Either way the altar - which has no
  minion rig to borrow - would still need a hand-written `.geo.json` and `.animation.json` that
  this file would have to call placeholder, and it would need exactly the same
  block-entity-renderer hook the plain renderer needs, because `core.client.ClientRenderers` has
  none.

  **The altar is animated anyway.** `client/CursedAltarRenderer` is an ordinary vanilla
  `BlockEntityRenderer` over a baked `ModelPart`: the crystal bobs while sealed, rises and spins up
  across the 60-tick charge, holds lit and fast while the Keeper is out, and drops dark when the
  altar is spent. Everything is derived from the blockstate and the world clock rather than from a
  triggered clip, so late joiners, `/reload` and relogs all show the same thing - which is the
  failure mode the plan calls out for triggered GeckoLib animations. Swapping this for GeckoLib
  later is additive and changes nothing outside `client/`.

  Registration goes through `mixin/VaultBlockEntityRenderersMixin`, an `@Invoker` on vanilla's
  private-static `BlockEntityRenderers.register`. That is the only route available to a feature
  agent: core has no hook, the shared access widener belongs to the core agent, and nothing calls
  into a feature's loader glue. Ask the core agent for a real
  `ClientRenderers.blockEntityRenderer(...)` and this mixin can be deleted.
* **The Keeper still uses the vanilla humanoid mesh**, not a GeckoLib rig. Its texture is now a real
  skin-layout sheet rather than a repeating tile, but it is still procedural placeholder art.
* **No emissive/glowmask textures.** Needs either a GeckoLib `AutoGlowingGeoLayer` or a resource-pack
  emissive convention. The altar instead emits real block light (3 when sealed, 10 otherwise) and
  the renderer draws the crystal at full-bright until the altar is spent.
