# Releasing

How to publish this repository and cut `v0.1.0`, what CI does once you do, and what is still
outstanding before this should be shown to the public as a finished mod.

Nothing in this file has been run. The repository has **no remote** and the tag has **not been
pushed** — every command below is yours to run when you have decided where this is published.

---

## 1. What is already done locally

* `main` carries the full history, including the release commit.
* An annotated tag `v0.1.0` points at that commit.
* `.github/workflows/build.yml` and `.github/workflows/release.yml` are committed and will start
  running the moment the repository exists on GitHub.

Check before you push:

```bash
git log --oneline -3
git tag -n99 v0.1.0
git status            # should be clean
```

---

## 2. Create the repository and push

### Option A — with the `gh` CLI

```bash
# From the repository root.
gh repo create creator-mods --public --source=. --remote=origin --push
```

`--push` pushes `main` and sets upstream, but **not tags**. Push the tag separately:

```bash
git push origin v0.1.0
```

Use `--private` instead of `--public` if you want to watch CI go green before anyone sees it. You
can flip it later with `gh repo edit --visibility public`.

### Option B — repository created in the browser

Create an empty repository (no README, no `.gitignore`, no licence — this tree already has all
three), then:

```bash
git remote add origin git@github.com:<you>/creator-mods.git
git push -u origin main
git push origin v0.1.0
```

Over HTTPS, use `https://github.com/<you>/creator-mods.git` instead.

### Verify

```bash
git remote -v
git ls-remote --tags origin
```

---

## 3. What CI does

Two workflows, both on `ubuntu-latest` with Temurin JDK 21 and `gradle/actions/setup-gradle@v4`.

### `Build` — on every push to any branch, and on every pull request

1. `./gradlew build` — compiles `:common`, `:fabric` and `:neoforge`, runs the **486 JUnit tests**
   in `:common`, and compiles the gametest source sets.
2. Writes `eula=true` into both GameTest run directories.
3. `./gradlew :fabric:runGameTest` — boots a real dedicated server, **134 GameTests**.
4. `./gradlew :neoforge:runGameTestServer` — the same **134 GameTests** on the other loader.
5. Uploads the two loader jars as build artifacts (`creatormods-fabric`,
   `creatormods-neoforge`), with the `-sources` and `-javadoc` jars filtered out.
6. On failure only, uploads the JUnit reports and both servers' logs, kept 7 days.

`timeout-minutes: 60`, because the **first** run has to download and decompile Minecraft (NeoForm)
and remap it (Loom) on a cold cache. Expect that first build to take a long time and every later
one to be a few minutes. The Gradle cache is written only from `main`; branches read it.

### `Release` — on pushing a tag matching `v*`

1. Builds everything again from the tag.
2. Copies just the two shipping jars into `build/release/`.
3. Extracts the `## [0.1.0]` section out of `CHANGELOG.md` into the release body. (Verified: that
   heading exists and yields 78 lines. A missing section falls back to a stub rather than failing.)
4. Creates the GitHub Release via `softprops/action-gh-release@v2` and attaches both jars.
   A tag containing `-` is marked a prerelease.

It needs `contents: write`, which the workflow declares. No secrets are required.

**Modrinth and CurseForge publishing is deliberately not enabled** — there is no project id for
either yet, and a publish step without one fails the release *after* the jars are attached. The
commented block at the end of `release.yml` is where it goes.

### Re-cutting the tag

If the release run fails and you need to fix something:

```bash
git tag -d v0.1.0
git push origin :refs/tags/v0.1.0     # delete the remote tag
# ... commit the fix ...
git tag -a v0.1.0 -m "Creator Mods Suite v0.1.0"
git push origin v0.1.0
```

Delete the half-made GitHub Release in the UI first, or the action will collide with it.

---

## 4. What remains before a genuine public release

The code is tested and both jars are clean. The **presentation is not finished**, and the
repository says so in several places. Treat the list below as blocking for a store page.

### 4.1 Record the media

There is **no captured footage in this repository at all** — no screenshots, no GIFs, no clips. The
`docs/media/gif/` and `docs/media/png/` directories described in `docs/media/README.md` do not exist
yet. (Nothing is broken by this: the docs deliberately link to `SHOTLIST.md` rather than to image
files, and the only images in `README.md` are shields.io badges. There are no dead image links to
clean up — but there is also nothing showing the mod actually running.)

* `docs/media/SHOTLIST.md` already specifies all eight clips (one per feature, ~45 s each): exact
  command sequence, world and seed, camera, HUD state, length, output format, and the pass
  condition for each take. It is the capture-day checklist.
* `docs/media/README.md` fixes the naming scheme and says which directories are committed
  (`gif/`, `png/`) and which are not (`raw/`, large `clip/`).
* **Before recording anything**, add the block `docs/media/README.md` specifies to `.gitignore`:

  ```gitignore
  # --- Recorded media ---
  docs/media/raw/
  docs/media/clip/*.mp4
  docs/media/clip/*.mov
  docs/media/clip/*.webm
  ```

  This has **not** been added yet — `.gitignore` currently has no media section.

### 4.2 Replace the placeholder art and audio

**Every texture, model, animation and sound in the mod is a placeholder and is meant to look like
one.** Textures are generated by committed Python scripts, sounds are synthesised with ffmpeg, and
models are hand-blocked-out shapes. They sit at exactly the paths the code loads, so nothing is
missing at runtime — but the Ashen Colossus is a banded grey box, not a sculpt.

Per-feature inventories, each listing every file, its status, and what a real artist should do:

```
common/src/main/java/dev/riftal/creator/features/<id>/ASSETS.md
```

for `arsenal` · `colossus` · `events` · `evolve` · `powers` · `rules` · `toolkit` · `vault`.

Two constraints that are load-bearing, not stylistic:

* **Audio must stay mono, 44.1 kHz Ogg Vorbis.** Minecraft will not pan a stereo sound — a stereo
  replacement silently loses distance and direction.
* **Keep the GeckoLib bone and locator names on any re-export.** The animation JSON drives bones by
  name, and `ColossusAssetsTest` / `EvolveAssetsTest` fail the build if clip lengths or locators
  drift from the server-side timings.

No AI-generated imagery on a store page — that is a Modrinth content-rules violation.

### 4.3 Still open

* **Only one Minecraft version.** 1.21.1 exactly, Java 21, NeoForge 21.1.250, Fabric Loader
  0.19.5. No other version has been built or tested.
* **No multiplayer testing beyond GameTest.** The GameTests run a dedicated server, but no real
  client has connected to one. No performance or memory profiling has been done.
* **No runtime config file.** `/arsenal reload`, `/colossus reload` and `/power reload` are
  documented as *not implemented*; tuning is compiled in.
* **`CONTRACT.md` (59 KB) is internal working material**, not user documentation. Decide whether it
  belongs in a public repository.
* **Licence and credits** — MIT, author `Nasir Idrishi (Riftal Studios)`. Confirm that is how you
  want to be credited publicly before the repository goes public.
