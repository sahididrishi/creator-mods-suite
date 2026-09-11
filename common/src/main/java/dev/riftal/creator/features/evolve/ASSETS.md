# creator_evolve — asset inventory

Every file here was generated or hand-written by the feature agent. **Nothing in this list is
finished art.** The feature registers no blocks and no items, so it ships no blockstates, block or
item models, loot tables, recipes, tags or creative tab — one entity texture, three sounds, a
sound definition file, a lang file and the GameTest template are the whole resource surface.

| File | Kind | Status | What a real artist should do |
|---|---|---|---|
| `assets/creator_evolve/textures/entity/apex_beast.png` | 128×128 RGBA PNG | **PROCEDURAL PLACEHOLDER** (`tools/make_placeholder.py`) | Skin the beast: charcoal hide, violet chitin plates over the chest, shoulders, forearms and tail ridge, bone horns and teeth, glowing amber eyes. The generator already paints one island per cube at the real UVs (see below), so painting over it in Blockbench needs no re-unwrap. |
| `assets/creator_evolve/sounds/evolve/roar_small.ogg` | 1.1 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (ffmpeg: brown noise, lowpassed and tremoloed) | A short throaty growl for the stage 2–4 transformations. **Mono, 44.1 kHz.** |
| `assets/creator_evolve/sounds/evolve/roar_apex.ogg` | 2.85 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (ffmpeg: brown noise, slowed, lowpassed, echoed) | The Apex roar: lion and bear layered and pitched down, with a sub-bass tail that outlives the title card. **Mono, 44.1 kHz.** |
| `assets/creator_evolve/sounds/evolve/complete.ogg` | 1.0 s Vorbis, **mono** | **PROCEDURAL PLACEHOLDER** (ffmpeg: two sines, the second delayed) | A bright two-note ascending chime landing on the beat the title card appears. **Mono, 44.1 kHz.** |
| `assets/creator_evolve/sounds.json` | JSON | HAND-WRITTEN, FINAL | — |
| `assets/creator_evolve/lang/en_us.json` | JSON | HAND-WRITTEN, FINAL | — |
| `data/creator_evolve/structure/empty.nbt` | NBT | SCAFFOLD, FINAL | — (the shared 9×9×9 GameTest arena) |

`EvolveAssetsTest` (JUnit) fails the build if any of the above goes missing, if `sounds.json`
promises an `.ogg` that is not shipped, or if a translation key the code emits has no line in the
lang file.

## The texture is UV-matched to the model, not decorative

There is **no GeckoLib model and no `geo/` or `animations/` directory**. The Apex beast is a vanilla
`EntityModel` built in Java (`client/render/ApexBeastModel.createBodyLayer()`), so a modeller edits
Java rather than Blockbench for the *geometry*. The texture, though, is laid out exactly as vanilla
unwraps those cubes, and `tools/make_placeholder.py` derives the islands from the same numbers:

| Cube | `texOffs` | Size (w × h × d) | Island (x, y, w, h) |
|---|---|---|---|
| body | (0, 0) | 20 × 30 × 14 | (0, 0, 68, 44) |
| head | (0, 46) | 14 × 15 × 18 | (0, 46, 64, 33) |
| jaw | (46, 82) | 10 × 4 × 12 | (46, 82, 44, 16) |
| horn (×2, mirrored) | (92, 82) | 3 × 8 × 3 | (92, 82, 12, 11) |
| arm (×2, mirrored) | (70, 40) | 7 × 32 × 7 | (70, 40, 28, 39) |
| leg (×2, mirrored) | (70, 0) | 8 × 30 × 8 | (70, 0, 32, 38) |
| tail | (0, 82) | 4 × 4 × 18 | (0, 82, 44, 22) |

A vanilla cube unwraps to `top | bottom` across the top strip and `right | front | left | back`
below it, so each island is `2*(w + d)` wide and `d + h` tall. Everything outside an island is left
fully transparent on purpose — it makes the island map readable and the placeholder obvious.

## Placeholder audio — channel layout

The three `.ogg` files are **1-channel (mono), 44.1 kHz Ogg Vorbis**, so Minecraft applies distance
attenuation and direction and the roar appears to come from the beast. (Stereo files are played
non-positionally; these are no longer stereo.) ffmpeg's native `vorbis` encoder is stereo-only, so
the pipeline is `ffmpeg -ac 1 -ar 44100` → `oggenc` (vorbis-tools); see CONTRACT.md §9.2. Every
replacement must stay **mono, 44.1 kHz**.

## The HUD ships no textures

The evolution bar, the stage name, the XP counter, the `+N EVO` pop-ups and the white transformation
flash are all drawn with `HudText.drawBar` / `drawShadowed` / `GuiGraphics.fill` in the stage's own
colour. There is deliberately no `textures/gui/` directory: a 182×5 flat bar costs nothing to draw
and cannot desync from the palette in `stage/Stages.java`.

## Regenerating

```bash
# texture
python3 common/src/main/java/dev/riftal/creator/features/evolve/tools/make_placeholder.py

# sounds (run from the repo root)
cd common/src/main/resources/assets/creator_evolve/sounds/evolve

ffmpeg -v error -y -f lavfi -i "anoisesrc=color=brown:duration=1.1:amplitude=0.9" \
       -af "lowpass=f=420,tremolo=f=24:d=0.6,afade=t=in:st=0:d=0.06,afade=t=out:st=0.65:d=0.45,volume=6,alimiter=limit=0.85" \
       -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k roar_small.ogg

ffmpeg -v error -y -f lavfi -i "anoisesrc=color=brown:duration=2.2:amplitude=1.0" \
       -af "lowpass=f=240,atempo=0.8,tremolo=f=14:d=0.5,aecho=0.8:0.7:120:0.35,afade=t=in:st=0:d=0.12,afade=t=out:st=1.9:d=0.8,volume=8,alimiter=limit=0.9" \
       -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k roar_apex.ogg

ffmpeg -v error -y -f lavfi -i "sine=frequency=1174.66:duration=0.9" -f lavfi -i "sine=frequency=1760:duration=0.9" \
       -filter_complex "[0]afade=t=out:st=0.03:d=0.45,volume=2.6[a];[1]adelay=120|120,afade=t=out:st=0.18:d=0.7,volume=2.3[b];[a][b]amix=inputs=2:normalize=0,alimiter=limit=0.9" \
       -ac 2 -ar 44100 -c:a vorbis -strict -2 -b:a 96k complete.ogg

# verify
ffprobe -v error -show_entries stream=codec_name,channels,sample_rate \
        -show_entries format=duration -of default=nw=1 roar_apex.ogg
```
