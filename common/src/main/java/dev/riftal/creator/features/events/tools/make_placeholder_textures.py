#!/usr/bin/env python3
"""Regenerates every placeholder texture for the `events` feature.

    python3 common/src/main/java/dev/riftal/creator/features/events/tools/make_placeholder_textures.py

python3 stdlib only (struct + zlib), per CONTRACT.md section 9.1. Everything written here is an
obvious placeholder: flat palette, hard border, a readable glyph. See ASSETS.md for what an artist
is meant to replace it with.
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, *([os.pardir] * 9)))
OUT = os.path.join(OUT, "src", "main", "resources", "assets", "creator_events", "textures")


def write_png(path, width, height, pixels):
    """pixels: list of rows, each row a list of (r, g, b, a) tuples, 0-255."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in pixels
    )

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))  # 8-bit RGBA
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote {} ({} bytes)".format(path, os.path.getsize(path)))


# The feature palette: event-director gold over a scorched near-black.
GOLD_LIGHT = (255, 214, 96, 255)
GOLD = (236, 178, 41, 255)
GOLD_DARK = (176, 121, 18, 255)
EDGE = (46, 32, 8, 255)
INK = (24, 18, 10, 255)

# A 7x9 "?" drawn by hand so the block reads as a lucky block at 16x16 and at any GUI scale.
QUESTION = [
    "0111110",
    "1100011",
    "1100011",
    "0000011",
    "0000110",
    "0001100",
    "0001100",
    "0000000",
    "0001100",
]


def lucky_rain():
    size = 16
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            edge = x == 0 or y == 0 or x == size - 1 or y == size - 1
            if edge:
                row.append(EDGE)
                continue
            # A flat two-tone weave so the face still reads as a block, not a sticker.
            if (x + y) % 4 == 0:
                row.append(GOLD_LIGHT)
            elif (x * 3 + y) % 7 == 0:
                row.append(GOLD_DARK)
            else:
                row.append(GOLD)
        rows.append(row)

    # Stamp the "?" centred, with a one-pixel drop shadow so it survives mipmapping.
    ox, oy = (size - len(QUESTION[0])) // 2, (size - len(QUESTION)) // 2
    for gy, line in enumerate(QUESTION):
        for gx, cell in enumerate(line):
            if cell != "1":
                continue
            x, y = ox + gx, oy + gy
            if 0 < x + 1 < size - 1 and 0 < y + 1 < size - 1:
                rows[y + 1][x + 1] = GOLD_DARK
    for gy, line in enumerate(QUESTION):
        for gx, cell in enumerate(line):
            if cell == "1":
                rows[oy + gy][ox + gx] = INK

    write_png(os.path.join(OUT, "block", "lucky_rain.png"), size, size, rows)


if __name__ == "__main__":
    lucky_rain()
