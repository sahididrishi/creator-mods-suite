# Director's Toolkit (`toolkit`)

> Every knob a recording crew needs while filming, on one command root and one HUD line.
> Namespace `creator_toolkit` · package `dev.riftal.creator.features.toolkit` · plan
> [`plans/01-directors-toolkit.md`](../../../plans/01-directors-toolkit.md)

The toolkit adds **no blocks, no items, no mobs and no world generation**. It is the camera crew:
a take recorder with an on-screen clapperboard, freeze switches, a wave spawner, block-for-block
arena snapshots, a shared shot list, the clean-frame switches and the creator cheats — all of it
designed so that *nothing* of the tooling ends up in the footage.

---

## What it does

| System | What you get |
|---|---|
| **Take recorder** | `REC TAKE 003  00:12.3` top-left, a take number that survives a restart, marks with wall-clock + RTA + server tick, one plain-text log per take on disk. |
| **Mark key (`M`)** | Drops a mark without opening chat. The HUD flashes `MARK 2` for two seconds; nothing is written to chat. |
| **Freeze** | `mobs` — a true pause (AI, gravity, age, fire, despawn timers all stop, nothing written to entity NBT). `players` — non-op crew pinned in place, ops stay mobile. `all` — vanilla `/tick freeze`. |
| **Waves** | A perfect ring of mobs facing inward, or a uniform scatter, spawned in one command and removed in one command. |
| **Arena snapshots** | Save a volume (blocks *and* the props standing in it), trash it on camera, restore it block-for-block between takes. |
| **Camera bookmarks** | Stand where the shot is, save it, and any crew member can snap to that exact position, yaw and pitch — including across dimensions. |
| **Clean frame** | Hide the HUD, the chat log and every name tag, per player. |
| **Silent commands** | Command feedback moves to the action bar and the `[Creator: …]` op broadcast stops. |
| **Cheats** | `god`, `fly`, `heal`, `clear` — `god` and `fly` stick across death and relog. |

---

## Commands

Root: **`/toolkit`**. The root itself is open so a crew member can read the clapperboard; everything
that *changes* something is permission level 2 (op).

| Command | Perm | What it does | Feedback |
|---|---|---|---|
| `/toolkit take start` | 2 | Starts the next take, opens its log file | `Take 003 started` |
| `/toolkit take stop` | 2 | Stops it, writes the footer | `Take 003 stopped at 00:41.7 (3 marks) -> world_2026-09-11_take-003.log` |
| `/toolkit take mark [label]` | 2 | Drops a mark, optional free-text note | `Mark 2 @ 00:12.3` |
| `/toolkit take status` | **0** | Reads the clapperboard | `Take 003 running 00:12.3, 2 mark(s)` |
| `/toolkit take set <1..999>` | 2 | Sets the number the next take will use | `Next take = 007` |
| `/toolkit freeze mobs <true\|false>` | 2 | Tick-cancel freeze for every mob | `Mobs frozen` |
| `/toolkit freeze players <true\|false>` | 2 | Movement lock for every non-op player | `Players frozen (3)` |
| `/toolkit freeze all <true\|false>` | 2 | Vanilla whole-game freeze | `Whole game frozen (vanilla tick freeze)` |
| `/toolkit freeze status` | 2 | All three flags | `Freeze - mobs: ON  players: OFF  all: OFF` |
| `/toolkit wave spawn <entity> <1..200> <0..64> [ring\|random [centre]]` | 2 | Spawns a wave; default mode is `ring`, default centre is the caller | `Spawned 24 minecraft:zombie in ring r=10.0` |
| `/toolkit wave clear` | 2 | Discards every entity the toolkit spawned in this level | `Cleared 24 wave entities` |
| `/toolkit arena save <name>` | 2 | Quick-save a 25×9×25 box around you (±12 horizontal, −2…+6 vertical) | `Arena 'ring' saved (25x9x25, 24 entities)` |
| `/toolkit arena save <name> <from> <to>` | 2 | Save the exact box (inclusive corners, ≤ 512 000 blocks) | as above |
| `/toolkit arena reset <name>` | 2 | Put the blocks and the props back, remove everything else in the box | `Arena 'ring' reset (24 removed, 3 restored)` |
| `/toolkit arena list` | 2 | Saved arenas and their sizes | `Arenas: ring (25x9x25)` |
| `/toolkit arena delete <name>` | 2 | Forget one | `Arena 'ring' deleted` |
| `/toolkit cam save <name>` | 2 | Save your exact position, yaw and pitch | `Camera 'hero' saved` |
| `/toolkit cam go <name>` | 2 | Snap to that shot, cross-dimension aware | **nothing** — silent by design |
| `/toolkit cam list` | 2 | The shot list | `Cameras: hero  minecraft:overworld  12.5 / 68.0 / -40.5` |
| `/toolkit cam del <name>` | 2 | Forget one | `Camera 'hero' deleted` |
| `/toolkit cheat god [true\|false]` | 2 | Invulnerable; no argument toggles | `God: ON` |
| `/toolkit cheat fly [true\|false]` | 2 | Creative flight; no argument toggles | `Fly: ON` |
| `/toolkit cheat heal` | 2 | Health, hunger, saturation, effects, fire, air | `Healed` |
| `/toolkit cheat clear` | 2 | Empties your inventory | `Inventory cleared` |
| `/toolkit hide hud <true\|false>` | 2 | Your HUD off (the same switch F1 uses) | `HUD hidden: ON` |
| `/toolkit hide chat <true\|false>` | 2 | Your chat log stops drawing (you can still type) | `Chat hidden: ON` |
| `/toolkit hide nametags <true\|false>` | 2 | No floating names in your frame | `Name tags hidden: ON` |
| `/toolkit hide commands <true\|false>` | 2 | Silent mode, server wide | `Silent mode ON - feedback moves to the action bar` |
| `/toolkit tphere <players>` | 2 | Pulls the crew to your exact spot **and angle** | `Teleported Cam2 to you` |

Names for `arena` and `cam` are **case-insensitive** (`Hero` and `hero` are the same slot) and tab-complete.

### Mark key

Tapping **`M`** in-game sends a mark — no chat box, no echo, just the yellow `MARK n` flash. It is a
raw key check (`KeyboardHandler#keyPress`), so it fires only when no screen is open, with no
modifier held, on the main window, with a player in the world. The server re-checks permission
level 2 and rate-limits to one press per 5 ticks. **It is not rebindable yet** — see
[Known limits](#known-limits).

---

## The HUD

```
REC TAKE 003  00:12.3
    MARK 2
    FROZEN mobs
```

* `REC` in red while recording, a grey `---` badge when idle.
* The timer is real time (RTA), counted on the **client's** wall clock so it stays smooth between
  packets; the server's clock is folded in on every update, so a LAN host with a skewed clock cannot
  make it jump. It keeps counting while the game is paused — that is deliberate, it matches what the
  recorder is doing.
* `MARK n` flashes yellow for 2 s; after a take stops, the mark count stays on screen.
* `FROZEN mobs` / `players` / `mobs+players` in aqua whenever a freeze is on.
* The layer honours **F1** and `/toolkit hide hud`, so a clean frame really is clean.
* A client that joins mid-take asks the server for the state once per world it enters, so a crew
  member who logs in late still sees the right take number and timer.

Every string on the HUD is a `hud.creator_toolkit.*` translation key in
`assets/creator_toolkit/lang/en_us.json`.

---

## Files the feature writes

| What | Where |
|---|---|
| Take log, one per take | `<server dir>/creator-toolkit/takes/<world>_<yyyy-MM-dd>_take-NNN.log` |
| Take number, arenas, camera bookmarks | the overworld's `data/creatormods_toolkit.dat` (`SavedData`) |
| `god` / `fly` flags | player attachment `creator_toolkit:cheats`, copied on death |

A take log looks like this:

```
take=3 start=2026-09-11T14:01:30.000Z tick=1660 world=My_World__Ep_1
mark=1 rta=00:01.5 tick=1690 wall=2026-09-11T14:01:31.500Z by=Creator
mark=2 rta=00:12.3 tick=1906 wall=2026-09-11T14:01:42.300Z by=Creator label=hero shot
stop=2026-09-11T14:02:11.700Z rta=00:41.7 marks=2
```

If the world is closed while a take is running, the footer is still written, with `reason=server_stop`.

---

## Configuration

The toolkit has **no config file of its own**. The one key that affects it is the suite-wide feature
switch in `config/creatormods.json`:

```json
{ "features": { "toolkit": true } }
```

`false` (or `/creator feature toolkit false` + restart) removes the feature completely: no commands,
no payloads, no HUD layer, no attachment, and all five mixins become no-ops. That is the clean
single-feature recording mode.

`/toolkit hide commands true` writes two **vanilla gamerules** through the core `SilentMode` helper —
`sendCommandFeedback` and `logAdminCommands` — and restores them when you switch it back off.

---

## Shooting with it

A 45-second take, in the order a director actually types it:

1. **Set the stage.** Build or find the arena, stand in the middle, `/toolkit arena save ring`.
   (Or give exact corners: `/toolkit arena save ring 10 60 10 34 68 34`.)
2. **Save the shots.** Walk to each camera position and `/toolkit cam save hero`, `… top`, `… low`.
3. **Go quiet.** `/toolkit hide commands true`. The notice is printed once, in chat, *before* the
   flip — everything after it lands on the action bar.
4. **Roll.** `/toolkit take start`. The HUD switches to red `REC`.
5. **Shoot.** `/toolkit wave spawn zombie 24 10 ring` → `/toolkit freeze mobs true` for the
   walk-through b-roll → `/toolkit cam go hero` → `/toolkit freeze mobs false` →
   `/toolkit cheat god true` to wade through the fight.
6. **Mark the good bits.** Tap **`M`** whenever something works. Nothing appears in the footage but
   the yellow flash, and every press lands in the log with a timestamp you can scrub to.
7. **Clean frame for the thumbnail.** `/toolkit hide hud true` and `/toolkit hide nametags true`.
   Note that the action bar is part of the HUD, so with the HUD hidden you get *no* command feedback
   at all — that is intended.
8. **Reset between takes.** `/toolkit arena reset ring` puts the floor, the props and the scenery
   entities back and sweeps every wave mob out of the level.
9. **Cut.** `/toolkit take stop`. The HUD shows the final time and mark count, and the `.log` next to
   the world has one line per mark.

Tips from the plan's failure-mode list, all of which behave as described:

* Dying mid-take does not stop the timer, and `god`/`fly` come back after the respawn.
* Frozen mobs stay frozen across a dimension change; nothing is written to their NBT, so releasing
  them is instant and a crash cannot leave them broken.
* A crew member who joins *after* `/toolkit freeze players true` is locked where they land.
* Resetting an arena while somebody is standing in it never kills or moves them.
* A restart comes back **unfrozen and not recording** on purpose; only the take *number*, the arenas
  and the cameras persist.

---

## Known limits

* **The mark key is not rebindable** and does not appear in the Controls screen: the core library has
  no key-mapping registry and the loader bootstraps are off-limits to feature code, so `M` is a raw
  key check in a `KeyboardHandler` mixin. It clashes with map mods that also use `M` (Xaero's,
  JourneyMap). `/toolkit take mark` is the workaround until core grows a key-mapping API.
* **No camera glide.** `/toolkit cam go` is an instant snap; the eased glide and FOV in bookmarks are
  stretch goals in the plan and deliberately not built.
* **`/toolkit hide …` only affects the player who typed it.** An op cannot blank someone else's
  screen — by design.
* **A mob riding a boat is not frozen by `freeze mobs`** (vanilla ticks passengers on a different
  path). Use `freeze all` for that shot.
* **Arena volume is capped at 512 000 blocks**; a `StructureTemplate` holds the whole box in memory
  and anything larger hitches the server on camera.

---

## Tests

| Kind | Where |
|---|---|
| JUnit (pure logic: ring/disc maths, timer formatting, log file, take state, cheat flags, arena and camera NBT, payload codecs) | `common/src/test/java/dev/riftal/creator/features/toolkit/` |
| GameTest bodies (feature enabled, wave ring, mob freeze and release, arena reset, arena size cap, take recorder, camera bookmarks) | `common/src/main/java/dev/riftal/creator/features/toolkit/gametest/ToolkitGameTests.java` |
| Loader stubs | `fabric/src/gametest/java/.../ToolkitFabricGameTests.java`, `neoforge/src/main/java/.../ToolkitNeoForgeGameTests.java` |

Assets: see [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/toolkit/ASSETS.md)
— this feature ships text only.
