#!/usr/bin/env python3
"""Regenerates every placeholder texture the arsenal feature ships.

Python 3 standard library only - no Pillow, no network. Writes 8-bit RGBA PNGs
straight into common/src/main/resources/assets/creator_arsenal/textures/.

    python3 common/src/main/java/dev/riftal/creator/features/arsenal/tools/make_placeholders.py

Nothing here is finished art - see ASSETS.md next to this folder. It is deliberately
flat: three tones per material, a hard one-pixel outline, no shading ramps, no
noise. What it *is* is readable: each sprite has the silhouette of the weapon it
stands for, so a recording with placeholder art still reads on camera, and one
shared palette runs through all nine files.

Palette (the whole feature):

    outline   #12161C   the single dark key line under everything
    steel     #8E9CAB / #62707F / #3C4653      grapple blade, hook
    wood      #6B5138 / #4A3828                bow limbs, hammer haft
    rope      #C9A06A                          grapple rope + wrap
    storm     #8FE9F5 / #3FA9C4                bow string, arrow fletching, sparks
    void      #564670 / #3A2F4E                hammer head
    arcane    #B06CF0                          hammer seams
    bone      #E4DFC6 / #B7B096                scythe snath
    soul      #6FE0CF / #2A8F8A                scythe edge, soul glow
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *([os.pardir] * 10)))
TEXTURES = os.path.join(REPO, "common", "src", "main", "resources",
                        "assets", "creator_arsenal", "textures")

CLEAR = (0, 0, 0, 0)

OUTLINE = (18, 22, 28, 255)

STEEL_HI = (142, 156, 171, 255)
STEEL_MID = (98, 112, 127, 255)
STEEL_LO = (60, 70, 83, 255)

WOOD_HI = (107, 81, 56, 255)
WOOD_LO = (74, 56, 40, 255)

ROPE = (201, 160, 106, 255)

STORM_HI = (143, 233, 245, 255)
STORM_LO = (63, 169, 196, 255)

VOID_HI = (86, 70, 112, 255)
VOID_LO = (58, 47, 78, 255)
ARCANE = (176, 108, 240, 255)

BONE_HI = (228, 223, 198, 255)
BONE_LO = (183, 176, 150, 255)

SOUL_HI = (111, 224, 207, 255)
SOUL_LO = (42, 143, 138, 255)


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
    with open(path, "wb") as handle:
        handle.write(png)
    print("wrote", os.path.relpath(path, REPO))


class Canvas:
    """A tiny RGBA pixel canvas with the three operations these sprites need."""

    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.rows = [[CLEAR for _ in range(width)] for _ in range(height)]

    def inside(self, x, y):
        return 0 <= x < self.width and 0 <= y < self.height

    def put(self, x, y, colour):
        if self.inside(x, y):
            self.rows[y][x] = colour

    def put_if_clear(self, x, y, colour):
        if self.inside(x, y) and self.rows[y][x] == CLEAR:
            self.rows[y][x] = colour

    def outline(self, colour=OUTLINE):
        """One-pixel key line around everything drawn so far."""
        filled = [(x, y) for y in range(self.height) for x in range(self.width)
                  if self.rows[y][x] != CLEAR]
        for x, y in filled:
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                self.put_if_clear(x + dx, y + dy, colour)

    def pixels(self, points, colour):
        for x, y in points:
            self.put(x, y, colour)

    def line(self, x0, y0, x1, y1, colour):
        """Bresenham, inclusive of both ends."""
        dx = abs(x1 - x0)
        dy = -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.put(x0, y0, colour)
            if x0 == x1 and y0 == y1:
                return
            err2 = 2 * err
            if err2 >= dy:
                err += dy
                x0 += sx
            if err2 <= dx:
                err += dx
                y0 += sy

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, colour)


def grapple_blade():
    """Iron short-sword on the tool diagonal, rope wound round the grip."""
    c = Canvas(16, 16)
    # Blade: two parallel diagonals, highlight on the upper-left edge.
    c.line(13, 1, 5, 9, STEEL_MID)
    c.line(12, 1, 4, 9, STEEL_HI)
    c.line(13, 2, 6, 9, STEEL_LO)
    # Cross guard, perpendicular to the blade.
    c.line(3, 8, 7, 12, STEEL_MID)
    c.line(3, 9, 6, 12, STEEL_LO)
    # Grip and pommel.
    c.line(4, 11, 2, 13, WOOD_LO)
    c.put(1, 14, STEEL_MID)
    # Rope wrap on the grip, and the coil hanging off the pommel.
    c.pixels([(4, 12), (3, 13), (2, 14), (1, 12), (2, 11)], ROPE)
    c.outline()
    return c


def storm_bow(stage=None):
    """Recurve bow, limbs on the left, live cyan string down the right.

    stage=None is the bow at rest. stage 0/1/2 are the three vanilla draw states
    (storm_bow_pulling_0..2): the limbs bend further forward and the string is
    pulled further back each step, and the tip sparks grow, so the full draw -
    the one that calls lightning - is unmistakable in first person.
    """
    c = Canvas(16, 16)
    # How far the limbs bow forward and the string is hauled back, per draw state.
    bend, haul, spark = ((0, 0, 1), (1, 1, 2), (2, 2, 3), (3, 3, 4))[0 if stage is None else stage + 1]

    # Limbs: an arc through five control points, drawn as four segments.
    arc = [(4, 2), (7 + bend, 4), (9 + bend, 8), (7 + bend, 12), (4, 14)]
    for (x0, y0), (x1, y1) in zip(arc, arc[1:]):
        c.line(x0, y0, x1, y1, WOOD_HI)
    c.line(6 + bend, 4, 8 + bend, 8, WOOD_LO)
    c.line(8 + bend, 8, 6 + bend, 12, WOOD_LO)
    # Nocks, and the string between them - drawn as two segments meeting at the
    # nocking point so a drawn string reads as the vanilla V.
    c.pixels([(4, 1), (4, 15)], WOOD_LO)
    c.line(4, 2, 3 - haul, 8, STORM_LO)
    c.line(3 - haul, 8, 4, 14, STORM_LO)
    c.line(3, 3, 2 - haul, 8, STORM_HI)
    c.line(2 - haul, 8, 3, 13, STORM_HI)
    # Sparks at the tips, more of them the further the bow is drawn.
    tips = [(6, 1), (2, 2), (6, 15), (2, 14), (10 + bend, 8), (0, 5), (0, 11), (12 + bend, 4)]
    c.pixels(tips[:spark + 4], STORM_HI)
    c.outline()
    return c


def gravity_hammer():
    """Obsidian maul: heavy head top-right, haft down to the bottom-left."""
    c = Canvas(16, 16)
    c.line(3, 14, 9, 8, WOOD_HI)
    c.line(2, 14, 8, 8, WOOD_LO)
    # Head: a blunt block with a bevelled face.
    c.rect(8, 2, 14, 7, VOID_HI)
    c.rect(9, 3, 13, 6, VOID_LO)
    c.pixels([(8, 2), (14, 2), (8, 7), (14, 7)], CLEAR)
    # Glowing seams.
    c.pixels([(10, 3), (11, 4), (10, 5), (11, 6), (13, 4), (9, 6)], ARCANE)
    # Pommel cap.
    c.pixels([(2, 15), (3, 15)], VOID_HI)
    c.outline()
    return c


def soul_scythe():
    """Bone snath with a long curved soul-fire edge sweeping right."""
    c = Canvas(16, 16)
    c.line(3, 15, 8, 5, BONE_HI)
    c.line(4, 15, 9, 5, BONE_LO)
    # Blade: an arc from the snath head out and down to the tip.
    arc = [(8, 4), (11, 3), (13, 5), (14, 9)]
    for (x0, y0), (x1, y1) in zip(arc, arc[1:]):
        c.line(x0, y0, x1, y1, SOUL_LO)
    inner = [(8, 5), (11, 4), (12, 6), (13, 9)]
    for (x0, y0), (x1, y1) in zip(inner, inner[1:]):
        c.line(x0, y0, x1, y1, SOUL_HI)
    # Grip wrap and a wisp coming off the edge.
    c.pixels([(5, 11), (6, 10), (5, 12)], SOUL_LO)
    c.pixels([(15, 6), (10, 1)], SOUL_HI)
    c.outline()
    return c


def grapple_hook():
    """Entity sprite: three-pronged hook on a rope, drawn to read at 8 px."""
    c = Canvas(16, 16)
    # Rope coming in from the top, then the shank.
    c.line(8, 0, 8, 3, ROPE)
    c.rect(7, 4, 9, 9, STEEL_MID)
    c.line(8, 4, 8, 9, STEEL_HI)
    # Ring at the top of the shank.
    c.pixels([(7, 3), (9, 3)], STEEL_LO)
    # Two visible prongs curling outward, plus a stub for the third.
    c.line(7, 9, 4, 11, STEEL_MID)
    c.line(4, 11, 3, 13, STEEL_HI)
    c.line(9, 9, 12, 11, STEEL_MID)
    c.line(12, 11, 13, 13, STEEL_HI)
    c.line(8, 10, 8, 13, STEEL_LO)
    c.pixels([(3, 14), (13, 14), (8, 14)], STEEL_LO)
    c.outline()
    return c


def storm_arrow():
    """32x32 sheet in vanilla's arrow layout.

    ArrowRenderer samples exactly two regions (uv values read out of
    net/minecraft/client/renderer/entity/ArrowRenderer.java):

      * the shaft quads use u 0 -> 0.5, v 0 -> 0.15625  = pixels (0,0)-(15,4)
      * the fletching cross uses u 0 -> 0.15625, v 0.15625 -> 0.3125 = (0,5)-(4,9)

    Everything else on the sheet is never sampled and stays transparent.
    """
    c = Canvas(32, 32)
    # Shaft strip: fletching at the low-u end, head at the high-u end.
    c.rect(0, 0, 15, 4, STEEL_LO)
    c.rect(0, 1, 15, 3, STEEL_MID)
    c.rect(0, 2, 15, 2, STEEL_HI)
    c.rect(0, 0, 4, 4, STORM_LO)
    c.rect(0, 1, 4, 3, STORM_HI)
    c.rect(14, 0, 15, 4, STEEL_HI)
    c.put(15, 2, STORM_HI)
    # Fletching cross-section.
    c.rect(0, 5, 4, 9, STORM_LO)
    c.rect(1, 6, 3, 8, STORM_HI)
    c.put(2, 7, STEEL_LO)
    return c


def main():
    write_out = [
        (os.path.join(TEXTURES, "item", "grapple_blade.png"), grapple_blade()),
        (os.path.join(TEXTURES, "item", "storm_bow.png"), storm_bow()),
        (os.path.join(TEXTURES, "item", "storm_bow_pulling_0.png"), storm_bow(0)),
        (os.path.join(TEXTURES, "item", "storm_bow_pulling_1.png"), storm_bow(1)),
        (os.path.join(TEXTURES, "item", "storm_bow_pulling_2.png"), storm_bow(2)),
        (os.path.join(TEXTURES, "item", "gravity_hammer.png"), gravity_hammer()),
        (os.path.join(TEXTURES, "item", "soul_scythe.png"), soul_scythe()),
        (os.path.join(TEXTURES, "entity", "grapple_hook.png"), grapple_hook()),
        (os.path.join(TEXTURES, "entity", "projectiles", "storm_arrow.png"), storm_arrow()),
    ]
    for path, canvas in write_out:
        write_png(path, canvas.width, canvas.height, canvas.rows)


if __name__ == "__main__":
    main()
