#!/usr/bin/env python3
"""Procedural placeholder textures for the creator_colossus feature.

python3 stdlib only (CONTRACT.md section 9.1). Writes six RGBA PNGs:

    assets/creator_colossus/textures/entity/ashen_colossus.png                  128x128
    assets/creator_colossus/textures/entity/ashen_colossus_glowmask.png         128x128
    assets/creator_colossus/textures/entity/ashen_colossus_enraged.png          128x128
    assets/creator_colossus/textures/entity/ashen_colossus_enraged_glowmask.png 128x128
    assets/creator_colossus/textures/entity/ashen_minion.png                    64x64
    assets/creator_colossus/textures/entity/ashen_minion_glowmask.png           64x64

The UV rectangles below are copied by hand from the two hand-authored GeckoLib models
(geo/entity/ashen_colossus.geo.json and geo/entity/ashen_minion.geo.json). Change a cube's
`uv` or `size` there and you must change the matching entry in COLOSSUS_CUBES / MINION_CUBES
here, or the texture will slide off the model.

Box-UV layout for a cube at (u, v) of size (w, h, d) - the same unwrap vanilla uses:

    (u+d,      v)     w x d   top
    (u+d+w,    v)     w x d   bottom
    (u,        v+d)   d x h   right side
    (u+d,      v+d)   w x h   FRONT  (-Z, the face the mob looks along)
    (u+d+w,    v+d)   d x h   left side
    (u+2d+w,   v+d)   w x h   back

Run:  python3 common/src/main/java/dev/riftal/creator/features/colossus/tools/make_placeholder.py
"""

import os
import struct
import zlib

# --------------------------------------------------------------------------- png


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
    with open(path, "wb") as handle:
        handle.write(png)


def blank(size):
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


# --------------------------------------------------------------------------- palette

# Basalt body: cold grey, warm cracks. Deliberately flat and banded so it reads as a
# placeholder rather than as finished art.
STONE = {
    "edge": (22, 20, 24, 255),
    "dark": (46, 44, 50, 255),
    "base": (76, 74, 80, 255),
    "light": (102, 100, 108, 255),
    "ash": (134, 132, 138, 255),
}

MINION_STONE = {
    "edge": (20, 16, 14, 255),
    "dark": (48, 40, 34, 255),
    "base": (74, 64, 54, 255),
    "light": (98, 86, 72, 255),
    "ash": (124, 110, 94, 255),
}

CRACK_CALM = (206, 92, 26, 255)
CRACK_CALM_HOT = (252, 158, 58, 255)
CRACK_RAGE = (238, 46, 18, 255)
CRACK_RAGE_HOT = (255, 186, 96, 255)

# Face shading multipliers: top catches light, bottom is in shadow.
FACE_SHADE = {
    "top": 1.18,
    "bottom": 0.62,
    "right": 0.86,
    "front": 1.0,
    "left": 0.86,
    "back": 0.78,
}


def shade(colour, factor):
    r, g, b, a = colour
    return (
        min(255, int(r * factor)),
        min(255, int(g * factor)),
        min(255, int(b * factor)),
        a,
    )


# --------------------------------------------------------------------------- cube UVs

# name, u, v, w, h, d, role
COLOSSUS_CUBES = [
    ("body", 0, 0, 24, 32, 14, "torso"),
    ("arm_left", 76, 0, 8, 28, 8, "limb"),
    ("head", 0, 46, 14, 14, 14, "head"),
    ("arm_right", 56, 46, 8, 28, 8, "limb"),
    ("leg_right", 0, 88, 8, 32, 8, "limb"),
    ("leg_left", 32, 88, 8, 32, 8, "limb"),
    ("hand_left", 64, 88, 10, 10, 10, "fist"),
    ("hand_right", 64, 108, 10, 10, 10, "fist"),
]

MINION_CUBES = [
    ("body", 0, 0, 10, 8, 14, "torso"),
    ("head", 0, 22, 8, 8, 8, "head"),
    ("leg_front_right", 32, 22, 2, 10, 2, "limb"),
    ("leg_front_left", 40, 22, 2, 10, 2, "limb"),
    ("leg_back_right", 48, 22, 2, 10, 2, "limb"),
    ("leg_back_left", 56, 22, 2, 10, 2, "limb"),
]


def faces(u, v, w, h, d):
    """The six (name, x, y, width, height) rectangles of one box unwrap."""
    return [
        ("top", u + d, v, w, d),
        ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h),
        ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h),
        ("back", u + 2 * d + w, v + d, w, h),
    ]


# --------------------------------------------------------------------------- painting


def paint_face(base, glow, rect, palette, crack, crack_hot, role, face):
    """Fills one face rectangle: banded stone, a dark border, and a few lit cracks."""
    _, x0, y0, fw, fh = rect
    factor = FACE_SHADE[face]

    for y in range(fh):
        for x in range(fw):
            px, py = x0 + x, y0 + y
            # A face only gets a border when it is wide enough to still show stone inside
            # it - a 2px limb face would otherwise be solid black.
            border = fw > 3 and fh > 3 and (x == 0 or y == 0 or x == fw - 1 or y == fh - 1)
            if border:
                base[py][px] = shade(palette["edge"], factor)
                continue

            # Horizontal strata: every fourth row is a lighter ash band, every seventh a seam.
            if y % 7 == 3:
                colour = palette["dark"]
            elif y % 4 == 1:
                colour = palette["light"]
            else:
                colour = palette["base"]
            # A dusting of ash on the upward faces.
            if face == "top" and (x + y) % 5 == 0:
                colour = palette["ash"]
            base[py][px] = shade(colour, factor)

    # Cracks: fixed zig-zag columns, so the art is reproducible and reads as veins rather
    # than as noise. Only the big surfaces get them; a 2px limb face has no room.
    if fw >= 6 and fh >= 6:
        columns = [fw // 4, (3 * fw) // 4] if fw >= 12 else [fw // 2]
        for col in columns:
            x = col
            for y in range(2, fh - 2):
                x += 1 if (y // 2) % 2 == 0 else -1
                x = max(2, min(fw - 3, x))
                hot = (y % 5) == 0
                colour = crack_hot if hot else crack
                base[y0 + y][x0 + x] = colour
                glow[y0 + y][x0 + x] = colour
                if hot and x + 1 < fw - 2:
                    base[y0 + y][x0 + x + 1] = crack
                    glow[y0 + y][x0 + x + 1] = crack

    # Eyes: two lit slits on the front of the head, which is what makes the silhouette read.
    if role == "head" and face == "front" and fw >= 6 and fh >= 6:
        eye_y = y0 + fh // 3
        for side in (fw // 4 - 1, (3 * fw) // 4 - 1):
            for dx in range(2):
                px = x0 + side + dx
                if x0 < px < x0 + fw - 1:
                    base[eye_y][px] = crack_hot
                    glow[eye_y][px] = crack_hot

    # Knuckle marks on the fists, so left/right hands are not flat squares.
    if role == "fist" and face == "front" and fw >= 6:
        row = y0 + fh // 2
        for dx in range(1, fw - 1, 2):
            base[row][x0 + dx] = shade(palette["edge"], factor)


def paint(size, cubes, palette, crack, crack_hot):
    base = blank(size)
    glow = blank(size)
    for _name, u, v, w, h, d, role in cubes:
        for rect in faces(u, v, w, h, d):
            paint_face(base, glow, rect, palette, crack, crack_hot, role, rect[0])
    return base, glow


# --------------------------------------------------------------------------- main


def resolve_texture_dir():
    here = os.path.dirname(os.path.abspath(__file__))
    # .../common/src/main/java/dev/riftal/creator/features/colossus/tools -> .../common (9 levels up)
    common = os.path.abspath(os.path.join(here, *([os.pardir] * 9)))
    return os.path.join(
        common, "src", "main", "resources", "assets", "creator_colossus", "textures", "entity"
    )


def main():
    out = resolve_texture_dir()
    os.makedirs(out, exist_ok=True)

    calm, calm_glow = paint(128, COLOSSUS_CUBES, STONE, CRACK_CALM, CRACK_CALM_HOT)
    write_png(os.path.join(out, "ashen_colossus.png"), 128, 128, calm)
    write_png(os.path.join(out, "ashen_colossus_glowmask.png"), 128, 128, calm_glow)

    rage, rage_glow = paint(128, COLOSSUS_CUBES, STONE, CRACK_RAGE, CRACK_RAGE_HOT)
    write_png(os.path.join(out, "ashen_colossus_enraged.png"), 128, 128, rage)
    write_png(os.path.join(out, "ashen_colossus_enraged_glowmask.png"), 128, 128, rage_glow)

    minion, minion_glow = paint(64, MINION_CUBES, MINION_STONE, CRACK_CALM, CRACK_CALM_HOT)
    write_png(os.path.join(out, "ashen_minion.png"), 64, 64, minion)
    write_png(os.path.join(out, "ashen_minion_glowmask.png"), 64, 64, minion_glow)

    for name in sorted(os.listdir(out)):
        print("wrote", os.path.join(out, name))


if __name__ == "__main__":
    main()
