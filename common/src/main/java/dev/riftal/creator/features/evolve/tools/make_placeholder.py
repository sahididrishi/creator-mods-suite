#!/usr/bin/env python3
"""Regenerates creator_evolve's placeholder art. python3 stdlib only, no dependencies.

    python3 common/src/main/java/dev/riftal/creator/features/evolve/tools/make_placeholder.py

Writes:
    assets/creator_evolve/textures/entity/apex_beast.png   128x128 RGBA

The layout is not decorative: every painted island is the exact vanilla box unwrap of one cube in
``ApexBeastModel.createBodyLayer()``, at the same ``texOffs`` and the same size. A vanilla cube of
size (w, h, d) at texOffs (u, v) unwraps to

    top    (u + d,         v,     w, d)     bottom (u + d + w,     v,     w, d)
    right  (u,             v + d, d, h)     front  (u + d,         v + d, w, h)
    left   (u + d + w,     v + d, d, h)     back   (u + 2d + w,    v + d, w, h)

so the whole island is 2*(w + d) wide and (d + h) tall. Anything outside an island is left fully
transparent, which makes the island map readable at a glance and makes it obvious this is a
placeholder rather than painted art.

The palette is the feature's: charcoal hide, violet chitin plates, bone horns and teeth, amber
eyes - the same violet the Apex stage uses on the HUD. Colours are flat, the islands are outlined,
and the word PLACEHOLDER is stamped in the unused strip at the bottom, so nobody mistakes the file
for finished work. See ASSETS.md.
"""

import os
import struct
import zlib

REPO_RELATIVE_OUT = os.path.join(
    "common", "src", "main", "resources", "assets", "creator_evolve", "textures", "entity"
)

SIZE = 128

# --- palette -----------------------------------------------------------------------------------
NONE = (0, 0, 0, 0)          # unused texel
OUTLINE = (18, 10, 26, 255)  # island border
HIDE_DARK = (44, 30, 56, 255)
HIDE_MID = (66, 44, 82, 255)
HIDE_LIGHT = (88, 60, 108, 255)
PLATE = (124, 70, 168, 255)
PLATE_HI = (176, 92, 224, 255)
BONE = (216, 203, 168, 255)
BONE_SHADE = (166, 152, 120, 255)
EYE = (255, 214, 92, 255)
EYE_CORE = (255, 246, 214, 255)
MAW = (58, 15, 30, 255)

# --- the model, mirrored from ApexBeastModel.createBodyLayer() ----------------------------------
# name: (u, v, width, height, depth)
CUBES = {
    "body": (0, 0, 20, 30, 14),
    "head": (0, 46, 14, 15, 18),
    "jaw": (46, 82, 10, 4, 12),
    "horn": (92, 82, 3, 8, 3),
    "arm": (70, 40, 7, 32, 7),
    "tail": (0, 82, 4, 4, 18),
    "leg": (70, 0, 8, 30, 8),
}

# 3x5 pixel font, enough for the word stamped at the bottom.
FONT = {
    "A": ["010", "101", "111", "101", "101"],
    "C": ["111", "100", "100", "100", "111"],
    "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"],
    "H": ["101", "101", "111", "101", "101"],
    "L": ["100", "100", "100", "100", "111"],
    "O": ["111", "101", "101", "101", "111"],
    "P": ["111", "101", "111", "100", "100"],
    "R": ["111", "101", "110", "101", "101"],
}


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
    with open(path, "wb") as handle:
        handle.write(png)


def faces(cube):
    """The six (name, x, y, w, h) face rectangles of one vanilla cube unwrap."""
    u, v, w, h, d = cube
    return [
        ("top", u + d, v, w, d),
        ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + 2 * d + w, v + d, w, h),
    ]


def fill(rows, x, y, w, h, colour):
    for py in range(y, y + h):
        for px in range(x, x + w):
            if 0 <= px < SIZE and 0 <= py < SIZE:
                rows[py][px] = colour


def outline(rows, x, y, w, h, colour=OUTLINE):
    for px in range(x, x + w):
        for py in (y, y + h - 1):
            if 0 <= px < SIZE and 0 <= py < SIZE:
                rows[py][px] = colour
    for py in range(y, y + h):
        for px in (x, x + w - 1):
            if 0 <= px < SIZE and 0 <= py < SIZE:
                rows[py][px] = colour


def shade_face(rows, x, y, w, h, top, bottom):
    """A two-tone vertical gradient so the faces read as volume, not as a flat block."""
    for py in range(y, y + h):
        t = (py - y) / max(1, h - 1)
        colour = top if t < 0.45 else bottom
        for px in range(x, x + w):
            if 0 <= px < SIZE and 0 <= py < SIZE:
                rows[py][px] = colour


def stripe(rows, x, y, w, h, colour, period=6, thickness=2):
    for py in range(y, y + h):
        if (py - y) % period < thickness:
            for px in range(x + 1, x + w - 1):
                if 0 <= px < SIZE and 0 <= py < SIZE:
                    rows[py][px] = colour


def text(rows, x, y, word, colour):
    cursor = x
    for letter in word:
        glyph = FONT.get(letter)
        if glyph is None:
            cursor += 4
            continue
        for gy, line in enumerate(glyph):
            for gx, cell in enumerate(line):
                if cell == "1":
                    px, py = cursor + gx, y + gy
                    if 0 <= px < SIZE and 0 <= py < SIZE:
                        rows[py][px] = colour
        cursor += 4


def paint_body(rows):
    for name, x, y, w, h in faces(CUBES["body"]):
        if name in ("top", "bottom"):
            shade_face(rows, x, y, w, h, HIDE_DARK, HIDE_DARK)
        else:
            shade_face(rows, x, y, w, h, HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)

    # Chest plates on the front face, a spine ridge down the back.
    _, fx, fy, fw, fh = faces(CUBES["body"])[3]
    for i, row in enumerate(range(fy + 3, fy + fh - 4, 6)):
        inset = 2 + i
        fill(rows, fx + inset, row, max(2, fw - inset * 2), 2, PLATE)
        fill(rows, fx + inset, row, max(2, fw - inset * 2), 1, PLATE_HI)
    _, bx, by, bw, bh = faces(CUBES["body"])[5]
    fill(rows, bx + bw // 2 - 1, by + 2, 2, bh - 4, PLATE)
    # Shoulder plate on the top face.
    _, tx, ty, tw, th = faces(CUBES["body"])[0]
    fill(rows, tx + 2, ty + 2, tw - 4, th - 4, PLATE)
    fill(rows, tx + 4, ty + 4, tw - 8, th - 8, PLATE_HI)


def paint_head(rows):
    for name, x, y, w, h in faces(CUBES["head"]):
        shade_face(rows, x, y, w, h, HIDE_LIGHT if name == "top" else HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)

    _, fx, fy, fw, fh = faces(CUBES["head"])[3]
    # Brow ridge, then two amber eyes with a lit core.
    fill(rows, fx + 1, fy + 3, fw - 2, 2, PLATE)
    for ex in (fx + 3, fx + fw - 6):
        fill(rows, ex, fy + 5, 3, 2, EYE)
        fill(rows, ex + 1, fy + 5, 1, 1, EYE_CORE)
    # Upper jaw / snout shadow along the bottom of the face.
    fill(rows, fx + 2, fy + fh - 4, fw - 4, 3, MAW)
    for tx in range(fx + 3, fx + fw - 3, 3):
        fill(rows, tx, fy + fh - 4, 1, 2, BONE)
    # Crest running back over the skull.
    _, tx, ty, tw, th = faces(CUBES["head"])[0]
    fill(rows, tx + tw // 2 - 1, ty + 1, 2, th - 2, PLATE)


def paint_jaw(rows):
    for name, x, y, w, h in faces(CUBES["jaw"]):
        shade_face(rows, x, y, w, h, MAW if name == "top" else HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)
    # Lower teeth along the front and both sides.
    for index in (2, 3, 4, 5):
        _, x, y, w, h = faces(CUBES["jaw"])[index]
        for tx in range(x + 1, x + w - 1, 2):
            fill(rows, tx, y + 1, 1, max(1, h - 2), BONE)


def paint_horn(rows):
    for name, x, y, w, h in faces(CUBES["horn"]):
        shade_face(rows, x, y, w, h, BONE, BONE_SHADE)
        outline(rows, x, y, w, h)
    # A dark ring near the base so the horn reads as horn and not as a stick.
    _, x, y, w, h = faces(CUBES["horn"])[3]
    fill(rows, x, y + h - 3, w, 1, HIDE_DARK)


def paint_arm(rows):
    for name, x, y, w, h in faces(CUBES["arm"]):
        shade_face(rows, x, y, w, h, HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)
    # Forearm plates on the four side faces, claws at the bottom of the front face.
    for index in (2, 3, 4, 5):
        _, x, y, w, h = faces(CUBES["arm"])[index]
        stripe(rows, x, y + h // 2, w, h // 2 - 2, PLATE, period=5, thickness=2)
    _, fx, fy, fw, fh = faces(CUBES["arm"])[3]
    for cx in range(fx + 1, fx + fw - 1, 2):
        fill(rows, cx, fy + fh - 3, 1, 2, BONE)


def paint_leg(rows):
    for name, x, y, w, h in faces(CUBES["leg"]):
        shade_face(rows, x, y, w, h, HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)
    for index in (2, 3, 4, 5):
        _, x, y, w, h = faces(CUBES["leg"])[index]
        stripe(rows, x, y + 4, w, h - 10, HIDE_LIGHT, period=7, thickness=2)
    # Toe claws on the front face and the sole.
    _, fx, fy, fw, fh = faces(CUBES["leg"])[3]
    for cx in range(fx + 1, fx + fw - 1, 3):
        fill(rows, cx, fy + fh - 3, 2, 2, BONE)
    _, bx, by, bw, bh = faces(CUBES["leg"])[1]
    fill(rows, bx + 1, by + 1, bw - 2, bh - 2, HIDE_DARK)


def paint_tail(rows):
    for name, x, y, w, h in faces(CUBES["tail"]):
        shade_face(rows, x, y, w, h, HIDE_MID, HIDE_DARK)
        outline(rows, x, y, w, h)
    # Spikes along the top face, fading towards the tip.
    _, x, y, w, h = faces(CUBES["tail"])[0]
    for sy in range(y + 1, y + h - 1, 3):
        fill(rows, x + 1, sy, max(1, w - 2), 1, PLATE if sy < y + h // 2 else PLATE_HI)


def build_rows():
    rows = [[NONE for _ in range(SIZE)] for _ in range(SIZE)]
    paint_body(rows)
    paint_head(rows)
    paint_jaw(rows)
    paint_horn(rows)
    paint_arm(rows)
    paint_leg(rows)
    paint_tail(rows)
    # The unused strip under the islands: stamp the word so this can never pass for real art.
    text(rows, 3, 110, "PLACEHOLDER", PLATE_HI)
    return rows


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    # tools/ -> evolve/ -> features/ -> creator/ -> riftal/ -> dev/ -> java/ -> main/ -> src/ -> common/ -> repo
    repo = os.path.abspath(os.path.join(here, *([os.pardir] * 10)))
    out_dir = os.path.join(repo, REPO_RELATIVE_OUT)
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "apex_beast.png")
    write_png(out, SIZE, SIZE, build_rows())
    print("wrote", out)


if __name__ == "__main__":
    main()
