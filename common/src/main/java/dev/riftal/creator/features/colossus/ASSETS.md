# creator_colossus — asset inventory

**Nothing in this list is finished art.** Every file below was generated or hand-written by the
feature agent and exists at exactly the path the code asks for, so the mod runs with no missing
textures, no missing models and no missing sounds — but the Colossus is currently a banded grey box
figure with orange crack stripes, and it is meant to look like a placeholder.

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_colossus/geo/entity/ashen_colossus.geo.json` | GeckoLib 4 model, 128x128 UV | **HAND-AUTHORED PLACEHOLDER** | A blocked-out 5-block giant: `root` → { `leg_left`, `leg_right`, `body` }, `body` → { `head`, `arm_left` → `hand_left`, `arm_right` → `hand_right` }. 78 px tall. Re-sculpt it in Blockbench (`File → New → GeckoLib Animated Model`, texture 128x128), keep the **bone names** — the animation JSON drives them by name and the bone literally called `head` is what makes the model track the player. Locators `hand_left_tip`, `hand_right_tip`, `mouth` are worth adding for particle keyframes. |
| `assets/creator_colossus/geo/entity/ashen_minion.geo.json` | GeckoLib 4 model, 64x64 UV | **HAND-AUTHORED PLACEHOLDER** | Four-legged ash crawler, 18 px tall, bones `root` → { `body` → `head`, `leg_front_left`, `leg_front_right`, `leg_back_left`, `leg_back_right` }. Same rule: keep the names. |
| `assets/creator_colossus/animations/entity/ashen_colossus.animation.json` | GeckoLib animations | **HAND-AUTHORED PLACEHOLDER** | Ten clips, hand-keyframed, readable but not polished. **The lengths are load-bearing**: each one matches `AttackKind.durationTicks()` exactly and `ColossusAssetsTest.clipLengthsCoverTheServerTimeline` fails the build if they drift. `idle` 2.0 s loop · `walk` 1.2 s loop · `spawn` 3.0 s · `roar` 1.75 s · `slam` 2.0 s (impact at 1.0 s) · `lava_rain` 1.5 s (cast 0.5 s) · `summon` 1.5 s (cast 0.75 s) · `combo` 2.5 s (hits 0.5 / 1.1 / 1.9 s) · `stagger` 2.0 s · `death` 3.5 s, loop mode **hold_on_last_frame**. Everything else is **play once** — a hold blocks replay. |
| `assets/creator_colossus/animations/entity/ashen_minion.animation.json` | GeckoLib animations | **HAND-AUTHORED PLACEHOLDER** | `animation.minion.idle` 2.0 s loop, `.walk` 0.8 s loop, `.attack` 0.6 s play-once (triggered from `doHurtTarget`). |
| `assets/creator_colossus/textures/entity/ashen_colossus.png` | 128x128 RGBA PNG | **PROCEDURAL PLACEHOLDER** (`tools/make_placeholder.py`) | Basalt grey with ash strata and dim orange cracks. UV islands are laid out by the geo above — body `(0,0)`, arm_left `(76,0)`, head `(0,46)`, arm_right `(56,46)`, leg_right `(0,88)`, leg_left `(32,88)`, hand_left `(64,88)`, hand_right `(64,108)`. Real art: cooled lava crust, soot streaks running down from the shoulders, cracks that read as depth rather than as stripes. |
| `assets/creator_colossus/textures/entity/ashen_colossus_glowmask.png` | 128x128 RGBA PNG | **PROCEDURAL PLACEHOLDER** | Crack and eye pixels only, everything else fully transparent. Consumed by GeckoLib's `AutoGlowingGeoLayer`, which finds it by the `_glowmask` suffix. |
| `assets/creator_colossus/textures/entity/ashen_colossus_enraged.png` | 128x128 RGBA PNG | **PROCEDURAL PLACEHOLDER** | The phase 3 variant — `client/ColossusModel.java` swaps to it when `getPhase() >= 3`. Cracks should go from dim orange to white-hot, and the crust should look like it is failing. |
| `assets/creator_colossus/textures/entity/ashen_colossus_enraged_glowmask.png` | 128x128 RGBA PNG | **PROCEDURAL PLACEHOLDER** | Cracks and eyes at full brightness. |
| `assets/creator_colossus/textures/entity/ashen_minion.png` | 64x64 RGBA PNG | **PROCEDURAL PLACEHOLDER** | Warmer, browner ash than the boss so the two read apart at a distance. |
| `assets/creator_colossus/textures/entity/ashen_minion_glowmask.png` | 64x64 RGBA PNG | **PROCEDURAL PLACEHOLDER** | Cracks and eyes only. A fully transparent sheet is also a valid answer if the minion should not glow. |
| `assets/creator_colossus/sounds/colossus/roar.ogg` | 1.7 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (`tools/make_sounds.sh`, brown noise slowed) | Layer a lion roar over a low rumble, pitch down about 4 semitones. Length should stay near the 35-tick roar clip. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/colossus/swing.ogg` | 0.5 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (pink noise sweep) | Two tonnes of rock coming round through the air. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/colossus/slam.ogg` | 1.2 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (48 Hz sine + echo) | A low thud with a stone-crack transient on the front and a long sub tail. This is the single most important sound in the feature. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/colossus/step.ogg` | 0.35 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (70 Hz sine) | A dull stomp, quieter than the slam, playing on every footfall. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/colossus/hurt.ogg` | 0.65 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (band-passed brown noise) | Stone grinding on stone. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/colossus/death.ogg` | 3.3 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (brown noise, slowed, long fade) | The collapse: a failing roar that falls away into rubble. Should not outrun the 70-tick death clip. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/minion/hurt.ogg` | 0.35 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** | A dry crumble, brighter than the boss so the two do not mask each other. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds/minion/death.ogg` | 0.8 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** | The same, falling apart. **Mono, 44.1 kHz.** |
| `assets/creator_colossus/sounds.json` | JSON | HAND-WRITTEN, FINAL | Eight entries, keyed exactly as the `SoundEvent`s are registered. |
| `assets/creator_colossus/lang/en_us.json` | JSON | HAND-WRITTEN, FINAL | One line for every string the feature emits, subtitles included. |
| `assets/creator_colossus/models/item/ashen_colossus_spawn_egg.json` | JSON | HAND-WRITTEN, FINAL | `minecraft:item/template_spawn_egg`; the colours come from code. |
| `assets/creator_colossus/models/item/ashen_minion_spawn_egg.json` | JSON | HAND-WRITTEN, FINAL | — |
| `data/creator_colossus/loot_table/entities/ashen_colossus.json` | JSON | HAND-WRITTEN, FINAL | Netherite ingot or scrap, magma cream, blaze rods, 35 % nether star. Deliberately **not** gated on `killed_by_player`, so `/colossus kill` still drops on camera. |
| `data/creator_colossus/loot_table/entities/ashen_minion.json` | JSON | HAND-WRITTEN, FINAL | — |
| `data/creator_colossus/structure/arena_24.nbt` | NBT | GENERATED, FINAL (`tools/make_arena_structure.py`) | 24x12x24 polished andesite floor. The GameTest arena — the 9x9 `empty.nbt` is smaller than the boss' own 7-block shockwave. |
| `data/creator_colossus/structure/empty.nbt` | NBT | SHIPPED BY THE SCAFFOLD | Do not overwrite. |

## Placeholder audio — channel layout

All eight `.ogg` files are **1-channel (mono), 44.1 kHz Ogg Vorbis**, so Minecraft gives them full
3D positional attenuation and direction — which for a boss whose whole point is that you hear it
coming is the behaviour you want. (They were briefly stereo, which Minecraft plays
non-positionally; that is fixed.) ffmpeg's *native* `vorbis` encoder is stereo-only, so the
conversion goes `ffmpeg -ac 1 -ar 44100` → `oggenc` (vorbis-tools); see CONTRACT.md §9.2. Every
replacement must stay **mono, 44.1 kHz**.

## Notes for whoever produces the art

* Both mobs are GeckoLib. There is **no vanilla-style Java model** anywhere in this feature, so a
  modeller works entirely in Blockbench and never touches Java.
* **No sound or particle keyframes are used**, and none are needed. Every sound and particle in this
  fight is emitted from the server at the ticks in `AttackKind`, because GeckoLib's keyframe
  handlers run on the client render thread only. Adding a keyframe for a sound the server already
  plays would double it up.
* The bone names and the clip names are an API. `ColossusAssetsTest` (JUnit) checks that every clip
  the code triggers exists, that every animated bone exists in the model, that no cube's UV runs off
  the sheet, that the clip lengths match the server timeline and that every `sounds.json` entry
  resolves to a real file. Re-export freely; the test will tell you what you broke.
* `ash_bomb` needs no assets at all — it renders as a magma cream through `ThrownItemRenderer`.
* Particles are all vanilla: `BLOCK` (the floor state), `LAVA`, `FLAME`, `SOUL_FIRE_FLAME`,
  `LARGE_SMOKE`, `ASH`, `CRIT`, `EXPLOSION_EMITTER`.

## Regenerating the placeholders

From the repo root:

```bash
# six PNGs
python3 common/src/main/java/dev/riftal/creator/features/colossus/tools/make_placeholder.py

# eight .ogg files (needs ffmpeg; prints a codec/rate/channel report at the end)
bash common/src/main/java/dev/riftal/creator/features/colossus/tools/make_sounds.sh

# the 24x12x24 GameTest arena
python3 common/src/main/java/dev/riftal/creator/features/colossus/tools/make_arena_structure.py
```

`make_placeholder.py` carries a copy of every cube's UV rectangle. If you change a cube's `uv` or
`size` in a `.geo.json`, change the matching row in `COLOSSUS_CUBES` / `MINION_CUBES` too, or the
regenerated placeholder will slide off the model.
