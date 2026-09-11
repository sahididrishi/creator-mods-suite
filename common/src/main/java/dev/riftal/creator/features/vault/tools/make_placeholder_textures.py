#!/usr/bin/env python3
"""Regenerates every placeholder texture for the `vault` feature.

python3 standard library only - no Pillow, no network. Run from anywhere:

    python3 common/src/main/java/dev/riftal/creator/features/vault/tools/make_placeholder_textures.py

Everything it writes is deliberately flat-coloured with a hard border so nobody
mistakes it for finished art. See ASSETS.md next to this folder for what a real
artist should replace each file with.
"""

import os
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


def flat_entity(width, height, base, band, accent):
    """64x64 sheet: banded purple so every humanoid cube lands on something visible."""
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            if (x % 8 == 0) or (y % 8 == 0):
                row.append(accent)
            elif (y // 4) % 2 == 0:
                row.append(base)
            else:
                row.append(band)
        rows.append(row)
    # A lighter block where the vanilla humanoid head-front UV sits (8,8)-(16,16),
    # so the Keeper visibly has a face rather than reading as a solid brick.
    for y in range(9, 16):
        for x in range(9, 16):
            rows[y][x] = (196, 150, 255, 255)
    for y in (11, 12):
        for x in (10, 14):
            rows[y][x] = (255, 90, 90, 255)
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
    written.append(write_png(os.path.join(ASSETS, "entity", "vault_keeper.png"),
                             64, 64, flat_entity(64, 64, (58, 34, 88, 255), (44, 26, 68, 255),
                                                 (28, 16, 44, 255))))
    for path in written:
        print(os.path.relpath(path, COMMON))


if __name__ == "__main__":
    main()
