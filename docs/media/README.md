# Media

Where recorded files live, what gets committed, and what each file is called.

The shot lists themselves are in [`SHOTLIST.md`](SHOTLIST.md) — eight 45-second clips, one per
feature, in real command syntax.

---

## Layout

```
docs/media/
├── README.md          this file
├── SHOTLIST.md        the eight shot lists
├── gif/               committed — the loops that go in the READMEs
├── png/               committed — stills, thumbnails, HUD crops, tooltip shots
├── clip/              committed only if small; otherwise a link (see below)
└── raw/               never committed — camera-original takes, project files, exports
```

`gif/` and `png/` are the only two directories that are always in git. They are what
`README.md` and `docs/features/*.md` link to, so a reader who clones the repo or opens it on
GitHub sees the feature working without downloading anything.

`raw/` is working material: full-length takes, OBS recordings, `.mp4` masters, editor project
files. It stays on disk and out of git. **Before you drop a single file in there, add this to
`.gitignore`:**

```gitignore
# --- Recorded media ---
docs/media/raw/
docs/media/clip/*.mp4
docs/media/clip/*.mov
docs/media/clip/*.webm
```

`.gitattributes` already marks `*.png` and `*.gif` as `binary`, so those two need nothing further.
It does **not** cover `*.mp4`, `*.mov` or `*.webm` — another reason finished clips belong on a
release page or a video host rather than in the tree.

Note that `.gitignore` also ignores `*.log`, which includes the Director's Toolkit take logs. If
you want to commit one as a documentation sample, rename it to `take-001.log.txt`.

---

## Naming

```
<feature-id>-<subject>[-<variant>].<ext>
```

* **`<feature-id>`** is one of the eight real ids, exactly as `/creator features` prints them:
  `toolkit` · `colossus` · `powers` · `rules` · `evolve` · `events` · `arsenal` · `vault`.
  Anything that is not feature-specific (the `/creator` command, the config file, a suite montage)
  uses `suite`.
* **`<subject>`** is what is happening, in one or two words, lowercase, hyphen separated, named
  after the thing in the code — the rule id, the ability path, the event id, the weapon id, the
  command verb. `powers-ground-pound.gif`, not `powers-aoe-attack.gif`.
* **`<variant>`** is optional and only for two shots of the same subject that need to sit next to
  each other: `-wide`, `-hud`, `-first-person`, `-before` / `-after`, `-full-draw` / `-half-draw`.
* Lowercase, hyphens only. No spaces, no underscores, no dates, no version numbers — git already
  knows both, and a dated filename breaks every link the day you re-record.

Examples, using real ids from the code:

```
gif/toolkit-take-timer.gif
gif/toolkit-wave-ring.gif
gif/colossus-slam-shockwave.gif
gif/colossus-phase3-ring.gif
gif/powers-shield-dome.gif
gif/rules-item-roulette.gif
gif/evolve-apex-transform.gif
gif/events-meteor-impact.gif
gif/arsenal-storm-bow-full-draw.gif
gif/arsenal-storm-bow-half-draw.gif
gif/vault-altar-activate.gif
png/vault-keeper-bossbar.png
png/suite-creator-features.png
```

Raw takes keep the take number the Director's Toolkit already assigned them, so a file lines up
with the line in `creator-toolkit/takes/<world>_<yyyy-MM-dd>_take-NNN.log`:

```
raw/<feature-id>/take-003.mp4
raw/<feature-id>/take-003.log        (copy of the toolkit log for that take)
```

Finished clips, if they are small enough to commit at all:

```
clip/<feature-id>-demo.mp4           the 45-second feature clip
clip/suite-demo.mp4                  the montage
```

---

## Specs

| Kind | Spec |
|---|---|
| Clip master | 1080p60, H.264, recorded with no music under the beats where the point is that chat is silent |
| Committed GIF | 720p, ≤ 8 s, ≤ 8 MB, 20–24 fps, generated with a palette (see below) |
| Committed PNG | Native resolution, no upscale. GUI scale 3 for HUD rows, tooltips and icon inserts |
| Anything larger | Link it from a GitHub release or a video host; do not commit it |

Making a GIF that stays under 8 MB:

```bash
# 1. palette from the clip
ffmpeg -y -ss 12 -t 8 -i raw/powers/take-003.mp4 \
  -vf "fps=22,scale=1280:-1:flags=lanczos,palettegen=stats_mode=diff" palette.png

# 2. the GIF itself
ffmpeg -y -ss 12 -t 8 -i raw/powers/take-003.mp4 -i palette.png \
  -lavfi "fps=22,scale=1280:-1:flags=lanczos[v];[v][1:v]paletteuse=dither=bayer:bayer_scale=3" \
  gif/powers-shield-dome.gif
```

If it still will not fit: drop to `fps=18`, then to `scale=960:-1`, then shorten the loop. Do not
drop the palette step — a 256-colour GIF of a dark arena without one bands badly.

---

## What to shoot per feature

The plans each list a set of GIFs in §10. Here they are with the names this repo uses. Every
subject maps to a beat in [`SHOTLIST.md`](SHOTLIST.md).

| Feature | Committed GIFs |
|---|---|
| `toolkit` | `toolkit-take-timer` · `toolkit-mark-key` · `toolkit-wave-ring` · `toolkit-freeze-mobs` · `toolkit-arena-reset` · `toolkit-cam-go` · `toolkit-hide-hud` · `toolkit-silent-mode` |
| `colossus` | `colossus-spawn` · `colossus-slam-shockwave` · `colossus-lava-rain` · `colossus-minions` · `colossus-phase3-ring` · `colossus-death-loot` · `colossus-commands` |
| `powers` | `powers-dash` · `powers-fire-burst` · `powers-ground-pound` · `powers-ender-pull` · `powers-shield-dome` · `powers-mob-freeze` · `powers-hud-sweep` · `powers-commands` |
| `rules` | one per rule id — `rules-random-drops` · `rules-crafts-x10` · `rules-lava-floor` · `rules-blocks-explode` · `rules-giant-mobs` · `rules-gravity-x3` · `rules-item-roulette` · `rules-hearts-shop` · `rules-one-heart` · `rules-no-stop-moving` · `rules-inventory-shuffle` · `rules-preset-chaos` |
| `evolve` | `evolve-stage1-to-2` · `evolve-brute-oneshot` · `evolve-titan-step-jump` · `evolve-apex-transform` · `evolve-apex-walk` · `evolve-commands` |
| `events` | `events-bloodmoon` · `events-bloodmoon-skip` · `events-meteor` · `events-siege` · `events-luckyrain` · `events-voidrise` · `events-status` |
| `arsenal` | `arsenal-grapple-gap` · `arsenal-grapple-mob` · `arsenal-storm-bow-full-draw` · `arsenal-storm-bow-half-draw` · `arsenal-hammer-lift-slam` · `arsenal-scythe-lifesteal` · `arsenal-scythe-trail` · `arsenal-tooltips` |
| `vault` | `vault-locate` · `vault-walkthrough` · `vault-altar-activate` · `vault-keeper` · `vault-chest-unseal` · `vault-reset` |

---

## Linking media from the docs

Relative paths only, so the links work on GitHub, in a clone and in any static-site renderer:

```markdown
<!-- from README.md -->
![Ground Pound](docs/media/gif/powers-ground-pound.gif)

<!-- from docs/features/powers.md -->
![Ground Pound](../media/gif/powers-ground-pound.gif)
```

Give every image real alt text describing what is happening — these files are the only place a
reader can see the feature run, and alt text is what they get if the image does not load.

---

## Before you record

* Read the feature's section in [`SHOTLIST.md`](SHOTLIST.md) — the setup commands go in **before**
  the recorder starts.
* `/creator feature <id> false` for the seven features you are not filming, then restart, so
  nothing from another feature can appear in frame.
* `/creator silent true` (or `/toolkit hide commands true`) so command replies go to the action bar
  instead of chat.
* The art and audio in `colossus`, `events`, `arsenal` and `vault` is currently **procedural
  placeholder** — see each feature's `ASSETS.md`. Shoot the mechanics; do not cut a thumbnail out
  of an item sprite.
