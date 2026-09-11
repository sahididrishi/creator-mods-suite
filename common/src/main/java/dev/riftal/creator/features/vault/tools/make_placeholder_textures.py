#!/usr/bin/env python3
"""Regenerates every placeholder texture for the `vault` feature.

python3 standard library only - no Pillow, no network. Run from anywhere:

    python3 common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_textures.py

Everything it writes is deliberately flat-coloured with a hard border so nobody
mistakes it for finished art. See ASSETS.md next to this folder for what a real
artist should replace each file with.
"""

import os
import random
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
# tools/ -> vault/ -> features/ -> creator/ -> riftal/ -> dev/ -> java/ -> main/ -> src/ -> common/
COMMON = os.path.abspath(os.path.join(HERE, *([".."] * 9)))
ASSETS = os.path.join(COMMON, "src", "main", "resources", "assets", "creator_vault", "textures")


def write_png(path, width, height, pixels):
    """pixels: list of rows, each row a list of (r, g, b, a) tuples, 0-255."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in pixels
    )

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    return path


def checker(size, edge, dark, light, step=40):
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            if x == 0 or y == 0 or x == size - 1 or y == size - 1:
                row.append(edge)
            else:
                shade = step if (x + y) % 2 == 0 else 0
                base = dark if ((x // 4) + (y // 4)) % 2 == 0 else light
                row.append((min(255, base[0] + shade), min(255, base[1] + shade),
                            min(255, base[2] + shade), 255))
        rows.append(row)
    return rows


def diamond(size, edge, fill, glow):
    """A centred diamond on a dark ground - reads as 'crystal' at 16x16."""
    rows = []
    centre = (size - 1) / 2.0
    for y in range(size):
        row = []
        for x in range(size):
            if x == 0 or y == 0 or x == size - 1 or y == size - 1:
                row.append(edge)
                continue
            d = abs(x - centre) + abs(y - centre)
            if d < size * 0.28:
                row.append(glow)
            elif d < size * 0.45:
                row.append(fill)
            else:
                row.append(edge)
        rows.append(row)
    return rows


def key_sprite(size, edge, metal, gem):
    """Flat 2D key: a round bow on the left, a shaft and two teeth on the right."""
    rows = [[(0, 0, 0, 0)] * size for _ in range(size)]
    mid = size // 2
    for x in range(3, size - 2):
        rows[mid][x] = metal
        rows[mid - 1][x] = edge
    for x in range(2, 6):
        rows[mid - 2][x] = gem
        rows[mid + 1][x] = gem
    rows[mid - 1][2] = gem
    rows[mid][2] = gem
    for y in (mid + 1, mid + 2):
        rows[y][size - 4] = metal
        rows[y][size - 7] = metal
    return rows


# ---------------------------------------------------------------- entity sheet
#
# The vanilla 64x64 humanoid UV layout, read off HumanoidModel.createMesh
# (HumanoidModel.java:77-116): head texOffs(0,0) 8x8x8, hat texOffs(32,0),
# body texOffs(16,16) 8x12x4, arm texOffs(40,16) 4x12x4 (the left arm is the same
# UV mirrored), leg texOffs(0,16) 4x12x4 (left leg likewise).
#
# A cube at texOffs(u,v) of size (w,h,d) unwraps as:
#     down  (u+d,      v)     w x d
#     up    (u+d+w,    v)     w x d
#     right (u,        v+d)   d x h
#     front (u+d,      v+d)   w x h
#     left  (u+d+w,    v+d)   d x h
#     back  (u+2d+w,   v+d)   w x h
#
# so every region below is named, and every one gets its own tone. The old
# generator wrote one 8x8 cell repeated 64 times, which is why the Keeper read as
# a featureless purple blob however it was lit.

KEEPER_DEEP = (24, 18, 34, 255)
KEEPER_MID = (46, 38, 62, 255)
KEEPER_LIGHT = (64, 56, 84, 255)
KEEPER_PALE = (82, 72, 104, 255)
KEEPER_SEAM = (150, 80, 235, 255)
KEEPER_SEAM_HOT = (208, 156, 255, 255)
KEEPER_EYE = (255, 96, 72, 255)
KEEPER_EYE_CORE = (255, 214, 170, 255)
CLEAR = (0, 0, 0, 0)


def _rect(rows, x0, y0, w, h, colour):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            rows[y][x] = colour


def _face(rows, x0, y0, w, h, base, rng, chip=0.16):
    """One cube face: flat stone, a dark mortar border, a little deterministic chipping."""
    _rect(rows, x0, y0, w, h, base)
    for x in range(x0, x0 + w):
        rows[y0][x] = KEEPER_DEEP
        rows[y0 + h - 1][x] = KEEPER_DEEP
    for y in range(y0, y0 + h):
        rows[y][x0] = KEEPER_DEEP
        rows[y][x0 + w - 1] = KEEPER_DEEP
    for y in range(y0 + 1, y0 + h - 1):
        for x in range(x0 + 1, x0 + w - 1):
            r = rng.random()
            if r < chip * 0.45:
                rows[y][x] = KEEPER_DEEP
            elif r < chip:
                rows[y][x] = KEEPER_PALE


def _cube(rows, u, v, w, h, d, tones, rng):
    """Unwraps one box into the six named faces. `tones` is (down, up, side, front, back)."""
    down, up, side, front, back = tones
    _face(rows, u + d, v, w, d, down, rng)
    _face(rows, u + d + w, v, w, d, up, rng)
    _face(rows, u, v + d, d, h, side, rng)
    _face(rows, u + d, v + d, w, h, front, rng)
    _face(rows, u + d + w, v + d, d, h, side, rng)
    _face(rows, u + 2 * d + w, v + d, w, h, back, rng)


def _seam_v(rows, x, y0, y1, hot_every=3):
    """A glowing rune seam running down a face."""
    for i, y in enumerate(range(y0, y1)):
        rows[y][x] = KEEPER_SEAM_HOT if i % hot_every == 0 else KEEPER_SEAM


def _seam_h(rows, x0, x1, y, hot_every=3):
    for i, x in enumerate(range(x0, x1)):
        rows[y][x] = KEEPER_SEAM_HOT if i % hot_every == 0 else KEEPER_SEAM


def keeper_skin(rng):
    """A 64x64 sheet in the real skin layout: stone construct, purple rune seams, lit eyes."""
    rows = [[CLEAR] * 64 for _ in range(64)]

    # Head 8x8x8 at (0,0). The face is the palest panel so it reads at distance.
    _cube(rows, 0, 0, 8, 8, 8,
          (KEEPER_DEEP, KEEPER_MID, KEEPER_MID, KEEPER_LIGHT, KEEPER_MID), rng)
    # Carved brow and jaw seams on the face panel (8,8)-(16,16).
    _seam_h(rows, 9, 15, 10)
    _seam_h(rows, 10, 14, 14)
    _seam_v(rows, 11, 10, 14)
    # Two lit eyes.
    for ex in (10, 13):
        rows[11][ex] = KEEPER_EYE
        rows[11][ex + 1] = KEEPER_EYE
        rows[12][ex] = KEEPER_EYE_CORE
        rows[12][ex + 1] = KEEPER_EYE
    # Seam down the back of the skull (24,8)-(32,16).
    _seam_v(rows, 27, 9, 15)

    # Body 8x12x4 at (16,16). Front panel (20,20)-(28,32) gets the big chest rune.
    _cube(rows, 16, 16, 8, 12, 4,
          (KEEPER_DEEP, KEEPER_MID, KEEPER_MID, KEEPER_MID, KEEPER_LIGHT), rng)
    _seam_v(rows, 23, 21, 31)
    _seam_v(rows, 24, 21, 31)
    _seam_h(rows, 21, 27, 24)
    _seam_h(rows, 22, 26, 28)
    # Spine seam on the back panel (32,20)-(40,32).
    _seam_v(rows, 36, 21, 31)

    # Arm 4x12x4 at (40,16): banded, so the limbs never read as part of the torso.
    _cube(rows, 40, 16, 4, 12, 4,
          (KEEPER_DEEP, KEEPER_LIGHT, KEEPER_LIGHT, KEEPER_PALE, KEEPER_LIGHT), rng)
    for band in (23, 27):
        _seam_h(rows, 41, 55, band, hot_every=4)

    # Leg 4x12x4 at (0,16): the darkest limb, one ankle band.
    _cube(rows, 0, 16, 4, 12, 4,
          (KEEPER_DEEP, KEEPER_MID, KEEPER_MID, KEEPER_MID, KEEPER_DEEP), rng)
    _seam_h(rows, 1, 15, 29, hot_every=4)

    # Hat layer (32,0)-(64,16) is left fully transparent: the mob renders it at
    # +0.5 inflation, and RenderType.entityCutoutNoCull discards it, so the
    # silhouette stays the body itself rather than a second ghost shell.
    return rows


def crystal_net(fill, glow, edge):
    """32x32 sheet for the altar's floating crystal, unwrapped for a 6x4x6 box at texOffs(0,0).

    A 16x16 block texture cannot serve here: the UV net of a 6-wide, 6-deep box is
    2*(6+6) = 24 pixels across, so it needs a 32x32 sheet. Drawn as facets - a lit
    core with darker bevels - rather than the flat diamond the block model uses.
    """
    rows = [[(0, 0, 0, 0)] * 32 for _ in range(32)]

    def facet(x0, y0, w, h):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                # Distance from the panel centre drives the bevel.
                dx = abs(x - (x0 + (w - 1) / 2.0)) / max(1.0, (w - 1) / 2.0)
                dy = abs(y - (y0 + (h - 1) / 2.0)) / max(1.0, (h - 1) / 2.0)
                d = max(dx, dy)
                rows[y][x] = glow if d < 0.34 else (fill if d < 0.8 else edge)

    w = h = 6
    depth = 6
    height = 4
    facet(depth, 0, w, depth)                       # down
    facet(depth + w, 0, w, depth)                   # up
    facet(0, depth, depth, height)                  # right
    facet(depth, depth, w, height)                  # front
    facet(depth + w, depth, depth, height)          # left
    facet(2 * depth + w, depth, w, height)          # back
    return rows


DARK = (18, 10, 26, 255)
STONE_A = (52, 46, 62, 255)
STONE_B = (38, 33, 48, 255)
OBSIDIAN_A = (30, 22, 44, 255)
OBSIDIAN_B = (20, 14, 32, 255)

CRYSTALS = {
    "sealed": ((70, 50, 100, 255), (120, 90, 170, 255)),
    "charging": ((110, 60, 190, 255), (190, 130, 255, 255)),
    "active": ((150, 70, 255, 255), (235, 190, 255, 255)),
    "spent": ((48, 44, 56, 255), (78, 72, 92, 255)),
}


def main():
    written = []
    written.append(write_png(os.path.join(ASSETS, "block", "cursed_altar_base.png"),
                             16, 16, checker(16, DARK, STONE_A, STONE_B)))
    for state, (fill, glow) in CRYSTALS.items():
        written.append(write_png(
            os.path.join(ASSETS, "block", "cursed_altar_crystal_%s.png" % state),
            16, 16, diamond(16, DARK, fill, glow)))
    written.append(write_png(os.path.join(ASSETS, "block", "sealed_chest_side.png"),
                             16, 16, checker(16, DARK, OBSIDIAN_A, OBSIDIAN_B, step=24)))
    written.append(write_png(os.path.join(ASSETS, "block", "sealed_chest_top.png"),
                             16, 16, checker(16, DARK, OBSIDIAN_B, OBSIDIAN_A, step=24)))
    written.append(write_png(os.path.join(ASSETS, "item", "vault_key.png"),
                             16, 16, key_sprite(16, (26, 20, 12, 255), (198, 166, 88, 255),
                                                (170, 90, 255, 255))))
    for state, (fill, glow) in CRYSTALS.items():
        written.append(write_png(
            os.path.join(ASSETS, "entity", "cursed_altar_crystal_%s.png" % state),
            32, 32, crystal_net(fill, glow, DARK)))
    written.append(write_png(os.path.join(ASSETS, "entity", "vault_keeper.png"),
                             64, 64, keeper_skin(random.Random(20260912))))
    for path in written:
        print(os.path.relpath(path, COMMON))


if __name__ == "__main__":
    main()
