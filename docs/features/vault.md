# Cursed Vault (`vault`)

> An underground jigsaw dungeon you can `/locate`, a Cursed Altar that eats a Vault Key and summons
> a Vault Keeper mini-boss, and a Sealed Chest that cracks open into a loot chest when the Keeper
> dies - then `/vault reset` puts it all back for the next take.

| | |
|---|---|
| Feature id | `vault` |
| Resource namespace | `creator_vault` |
| Root command | `/vault` (op level 2) |
| Plan | [`plans/08-cursed-vault.md`](../../plans/08-cursed-vault.md) |
| Code | `common/src/main/java/dev/riftal/creator/features/vault/` |
| Assets inventory | [`ASSETS.md`](../../common/src/main/java/dev/riftal/creator/features/vault/ASSETS.md) |

---

## What it does

### The structure

`creator_vault:cursed_vault` is a plain `minecraft:jigsaw` structure - no custom `StructureType`, no
Java worldgen code, just hand-written JSON under `data/creator_vault/worldgen/`.

* **Placement** `random_spread`, spacing 24 chunks / separation 8, salt `1830767658`. Averages one
  vault every ~384 blocks, so `/locate` almost always answers within 1000 blocks.
* **Biomes** `#creator_vault:has_structure/cursed_vault`, which contains `#minecraft:is_overworld` -
  every overworld biome, modded ones included, as long as they are tagged correctly.
* **Depth** `step: underground_structures`, `start_height: {"absolute": -30}`,
  `terrain_adaptation: bury`, `liquid_settings: ignore_waterlogging` (aquifers do not flood it).
* **Pieces** seven templates in `data/creator_vault/structure/cursed_vault/`: `entrance`,
  `corridor_straight`, `corridor_corner`, `corridor_t`, `corridor_end`, `trap_room`,
  `treasure_room`.

Four template pools wire them together:

| Pool | Fallback | Elements (weight) |
|---|---|---|
| `creator_vault:cursed_vault/entrance` | `minecraft:empty` | `entrance` (1) |
| `creator_vault:cursed_vault/corridors` | `…/corridor_ends` | `corridor_straight` (4), `corridor_corner` (3), `corridor_t` (2), `trap_room` (2) |
| `creator_vault:cursed_vault/corridor_ends` | `minecraft:empty` | `corridor_end` (1) |
| `creator_vault:cursed_vault/treasure` | `minecraft:empty` | `treasure_room` (1) |

Jigsaw naming, which is what makes exactly one treasure room appear: corridor exits are named
`creator_vault:vault_out` and target `creator_vault:vault_in`; the entrance carries one extra
jigsaw named `creator_vault:treasure_anchor` pointing at the `treasure` pool, and the treasure room
is the only piece with a `creator_vault:treasure_in` connector. No `max_count`, no extra API.

The trap room always holds a vanilla chest with `creator_vault:chests/cursed_vault_trap`, which
contains a **guaranteed Vault Key** - so a survival player who finds a vault can always open it
without a command.

### The loop

1. **Cursed Altar** (`creator_vault:cursed_altar`) sits on the treasure-room dais with a
   `state` blockstate property: `sealed` → `charging` → `active` → `spent`. Because the state lives
   in the blockstate rather than in a payload, late joiners, `/reload` and relogs all look right.
   Light level 3 when sealed, 10 otherwise.
2. **Right-click it with a Vault Key.** One key is consumed (not in creative). The altar scans 16
   blocks for Sealed Chests, records each one's position *and facing*, rolls a loot seed and starts
   a **60-tick (3 s)** charge with a shrinking particle ring.
3. **The Vault Keeper bursts out** beside the altar: 80 HP, 8 attack, 6 armour, purple boss bar,
   fire immune, persistent, and leashed to its altar (it walks home past 20 blocks and is teleported
   back past 48, so it cannot be kited out of the room between takes).
4. **Kill it** and every recorded Sealed Chest is replaced, on the same tick, by a vanilla chest
   carrying `creator_vault:chests/cursed_vault` - obsidian particles, a crack, and loot that rolls
   the first time a player opens it, exactly like a dungeon chest.
5. **`/vault reset`** discards the Keeper, throws away the chest contents, puts the Sealed Chests
   back at their original facing and re-arms the altar.

A bound Keeper that cannot be resolved for **100 consecutive ticks** (killed by `/kill`, lava, the
void, or an unloaded chunk) counts as dead and the chests open anyway. `/vault status` shows that
countdown, so a take never dead-ends in an altar stuck on `active`.

### Content registered

| Registry | Ids |
|---|---|
| Block | `cursed_altar`, `sealed_chest` |
| Block entity | `cursed_altar`, `sealed_chest` |
| Item | `vault_key`, `cursed_altar`, `sealed_chest`, `vault_keeper_spawn_egg` |
| Entity type | `vault_keeper` |
| Sound event | `altar.activate`, `altar.unseal`, `altar.reset`, `chest.unseal`, `keeper.summon`, `keeper.idle`, `keeper.hurt`, `keeper.death` |
| Creative tab | `creator_vault` ("Cursed Vault") |
| Player data | `creator_vault:keys_used` (int, survives death) |
| Payload (S2C) | `creator_vault:vault_status` |

The **Sealed Chest** is unbreakable (`strength(-1)`), has `PushReaction.BLOCK` and **no inventory at
all** - nothing can hopper or piston the loot out before the fight is won.

The **Vault Key** is `Rarity.EPIC`, stacks to 16, glints without carrying enchantment data, and has
a shaped recipe (amethyst / gold / iron) as an alternative to the command.

---

## Commands

Root `/vault`, **permission level 2** on every node. All replies go through `CommandHelper`, so
`/creator silent true` moves the success lines to the action bar and leaves chat clean for the
recording; failures are always visible.

| Command | Args | Perm | Effect | Feedback |
|---|---|---|---|---|
| `/vault key` | — | 2 | One Vault Key into your inventory | `Gave 1 Vault Key(s)` |
| `/vault key <count>` | `1..16` | 2 | As above, n keys | `Gave n Vault Key(s)` |
| `/vault reset` | — | 2 | Nearest altar within **32** blocks: re-seal its chests, discard the Keeper, back to `sealed` | `Reset altar at x y z (n chest(s) re-sealed, keeper removed)` |
| `/vault reset <radius>` | `1..128` | 2 | As above with your own search radius | as above |
| `/vault spawn_keeper` | — | 2 | Spawns a Keeper where you stand and hands it to the nearest altar, so its death still unseals | `Spawned Vault Keeper (bound to altar at x y z)` / `(unbound …)` |
| `/vault unseal` | — | 2 | Skip the fight: primes a sealed altar first, then opens every chest it knows about | `Unsealed n chest(s)` |
| `/vault tp` | — | 2 | Finds the nearest `creator_vault:cursed_vault` within **100 chunks** and teleports you to the altar, or to a standable pocket at the vault's Y | `Teleported to creator_vault:cursed_vault at x y z` |
| `/vault status` | — | 2 | Four lines: altar position, state and charge, keeper alive / missing-tick countdown, chest count and your key counter | multi-line |

Failure lines (always shown, even in silent mode):
`No Cursed Altar within <n> blocks` · `No Cursed Vault found within 1600 blocks` ·
`Could not spawn a Vault Keeper here` · `That has to be run by a player.`

Vanilla commands worth having on the hotbar next to these: `/locate structure
creator_vault:cursed_vault`, `/tp`, `/gamemode`. Core extras: `/creator silent true`,
`/creator feature vault false`.

---

## Config

There is **no per-feature config file**. The one key that matters lives in
`config/creatormods.json`:

```json
{ "features": { "vault": true } }
```

* `false` → the feature gets no lifecycle calls at all: no blocks, no items, no entity, no commands,
  no HUD, no payload. The worldgen JSON still ships in the jar (it is datapack data, not code), so
  an already-generated vault stays in the world as plain deepslate rooms with missing blocks where
  the altar was. Switch the feature off **before** generating the chunks you intend to record.
* Toggle it live with `/creator feature vault false` and restart; list state with
  `/creator features`.

Everything else is a constant you can read in one place, `AltarStateMachine`:

| Constant | Value | What it controls |
|---|---|---|
| `CHARGE_TICKS` | 60 | Key → Keeper, in ticks |
| `KEEPER_CHECK_INTERVAL` | 20 | How often an active altar polls its Keeper |
| `KEEPER_MISSING_LIMIT` | 100 | Ticks of "unresolvable Keeper" that count as dead |
| `CHEST_SCAN_RADIUS` | 16 | Sealed Chest scan, taken once per activation |
| `STATUS_BROADCAST_INTERVAL` / `_RANGE` | 10 ticks / 32 blocks | HUD payload rate and reach |

---

## Recording with it

The HUD layer (top left, hidden by F1 like everything else) appears on its own while an altar is
charging or a Keeper is alive, and disappears two seconds after you walk away: altar state, a charge
bar with a countdown or the Keeper's health bar, then the sealed-chest count and how many keys you
have spent.

A 45-second take, in the order the commands fall:

1. `/creator silent true` — success lines move to the action bar.
2. `/locate structure creator_vault:cursed_vault` — read the coordinates on camera.
3. `/vault tp` — lands you on the altar, not at y = 0 like `/locate` alone.
4. Walk the corridors, film the trap room, arrive at the dais.
5. `/vault key` — right-click the altar. Key gone, ring of particles, HUD countdown, 3 seconds.
6. Fight the Keeper. Boss bar is automatic; `/creator feature …` is not needed.
7. Kill it: the chest cracks open. Open it on camera - the loot rolls on first open.
8. `/vault reset` — chest re-seals, altar re-arms. **Shoot the take again immediately.**

Useful during a shoot:

* **Missed the summon?** `/vault reset` then `/vault key` again. There is no cooldown.
* **Want the loot beat without the fight?** `/vault unseal` opens the chests from a sealed altar in
  one command.
* **Want the fight somewhere photogenic?** `/vault spawn_keeper` puts one where you are standing and
  still binds it to the nearest altar, so its death opens the real chests.
* **Lost track of state?** `/vault status` prints the altar's state, charge, keeper and chest count.
* **Recording a different feature?** `/creator feature vault false` and restart - nothing from this
  feature is registered at all.

Known, deliberate behaviours worth not being surprised by on camera:

* Two altars within 16 blocks of the same chest will both record it; unsealing either opens it.
* Breaking the altar unbinds its Keeper (the Keeper lives on) and leaves the chests sealed.
  `/vault unseal` still works from anywhere near them.
* Offering a key to a charging, active or spent altar is refused and the key is **not** consumed.
* `/reload` reloads the loot tables and sounds; worldgen changes need a world restart.

---

## Tests

| Where | What |
|---|---|
| `common/src/test/.../vault/AltarStateMachineTest` | the transition table, charge progress, timing invariants |
| `…/AltarStateTest` | serialized-name round trip and the unknown-name fallback |
| `…/VaultStructureDataTest` | every pool element has an `.nbt`, fallbacks exist, 1.21 singular folder names, structure/structure-set fields, loot bounds |
| `…/VaultAssetsTest` | every `Component.translatable` key has a lang line; every blockstate → model → texture chain resolves; every `sounds.json` entry has an `.ogg` and a subtitle; textures are power-of-two |
| `…/VaultStatusPayloadTest` | the state ⇄ ordinal round trip and the out-of-range fallback |
| `…/VaultHudStateTest` | the client cache's last-wins, clear and staleness contract |
| `common/.../vault/gametest/VaultGameTests` | 16 in-game tests: charge and summon, both interaction paths, key refusal, keeper death, adoption, NBT round trip, reset, unbreakable chest, loot table, scan semantics, nearest-altar lookup, `/vault reset` through Brigadier |

Every altar GameTest runs in its **own batch**: `StructureGridSpawner` puts test arenas 14 blocks
apart and the altar's chest scan reaches 16, so two of them side by side would unseal each other's
chests.
