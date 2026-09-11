#!/usr/bin/env python3
"""Regenerates every procedural placeholder asset of the `powers` feature.

    python3 common/src/main/java/dev/riftal/creator/features/powers/tools/make_assets.py

Writes, relative to the repo root:

    common/src/main/resources/assets/creator_powers/textures/gui/slot.png            32x32
    common/src/main/resources/assets/creator_powers/textures/gui/slot_ready.png      32x32
    common/src/main/resources/assets/creator_powers/textures/gui/abilities/*.png     16x16  (six)
    common/src/main/resources/assets/creator_powers/sounds/ui/ability_ready.ogg      ~0.45 s

PNGs are written with the stdlib only (struct + zlib), per CONTRACT.md section 9.1.
The ogg needs both ffmpeg and oggenc (brew install vorbis-tools) on PATH, per
CONTRACT.md section 9.2: ffmpeg's native `vorbis` encoder is stereo-only, so ffmpeg
renders mono WAV and oggenc does the Vorbis encode. The result is *mono* 44.1 kHz,
which is what Minecraft needs to position a sound in 3D. This chime is only ever used
as a UI sound today, but mono keeps it correct if it ever becomes a world sound.

Everything produced is an obvious placeholder: flat colours, a hard outline, one
recognisable silhouette per ability. See ASSETS.md next to this folder.
"""

import math
import os
import struct
import subprocess
import sys
import zlib

# ---------------------------------------------------------------------------- paths

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, *([os.pardir] * 10)))
ASSETS = os.path.join(REPO, "common", "src", "main", "resources", "assets", "creator_powers")
GUI = os.path.join(ASSETS, "textures", "gui")
ICONS = os.path.join(GUI, "abilities")
SOUNDS = os.path.join(ASSETS, "sounds", "ui")

# ---------------------------------------------------------------------------- palette
# One accent per ability, matching Ability#hudColor() in the Java so the icon and the
# slot tint are the same colour on screen.

OUTLINE = (16, 18, 28, 255)
SHADOW = (0, 0, 0, 90)

ACCENTS = {
    "dash": (127, 215, 255),
    "fire_burst": (255, 144, 64),
    "ground_pound": (194, 161, 107),
    "ender_pull": (181, 127, 255),
    "shield_dome": (111, 227, 255),
    "mob_freeze": (220, 243, 255),
}


def shade(rgb, factor):
    """Same hue, scaled brightness, fully opaque."""
    return (
        max(0, min(255, int(rgb[0] * factor))),
        max(0, min(255, int(rgb[1] * factor))),
        max(0, min(255, int(rgb[2] * factor))),
        255,
    )


# ---------------------------------------------------------------------------- png


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
    print("wrote {} ({}x{}, {} bytes)".format(os.path.relpath(path, REPO), width, height, len(png)))


class Canvas:
    """A tiny RGBA raster with the handful of primitives these icons need."""

    def __init__(self, size):
        self.size = size
        self.px = [[(0, 0, 0, 0)] * size for _ in range(size)]

    def set(self, x, y, colour):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = colour

    def get(self, x, y):
        if 0 <= x < self.size and 0 <= y < self.size:
            return self.px[y][x]
        return (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, colour):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, colour)

    def dot(self, x, y, colour, weight=1):
        half = weight // 2
        for dy in range(-half, weight - half):
            for dx in range(-half, weight - half):
                self.set(x + dx, y + dy, colour)

    def line(self, x0, y0, x1, y1, colour, weight=1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            t = i / steps
            self.dot(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), colour, weight)

    def disc(self, cx, cy, radius, colour):
        for y in range(self.size):
            for x in range(self.size):
                if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                    self.set(x, y, colour)

    def ring(self, cx, cy, radius, colour, thickness=1.0):
        inner = (radius - thickness) ** 2
        outer = radius * radius
        for y in range(self.size):
            for x in range(self.size):
                d = (x - cx) ** 2 + (y - cy) ** 2
                if inner <= d <= outer:
                    self.set(x, y, colour)

    def arc(self, cx, cy, radius, start_deg, end_deg, colour, thickness=1.0):
        steps = int(radius * 12) + 12
        for i in range(steps + 1):
            angle = math.radians(start_deg + (end_deg - start_deg) * i / steps)
            x = cx + math.cos(angle) * radius
            y = cy - math.sin(angle) * radius
            self.dot(round(x), round(y), colour, 2 if thickness > 1 else 1)

    def polygon(self, points, colour):
        """Scanline fill of a closed polygon given as [(x, y), ...]."""
        for y in range(self.size):
            crossings = []
            count = len(points)
            for i in range(count):
                x0, y0 = points[i]
                x1, y1 = points[(i + 1) % count]
                if y0 == y1:
                    continue
                if min(y0, y1) <= y + 0.5 < max(y0, y1):
                    t = (y + 0.5 - y0) / (y1 - y0)
                    crossings.append(x0 + (x1 - x0) * t)
            crossings.sort()
            for i in range(0, len(crossings) - 1, 2):
                for x in range(int(math.floor(crossings[i] + 0.5)), int(math.ceil(crossings[i + 1] - 0.5)) + 1):
                    self.set(x, y, colour)

    def outline(self, colour=OUTLINE):
        """One-pixel hard border around everything opaque. Makes 16 px art read."""
        edge = []
        for y in range(self.size):
            for x in range(self.size):
                if self.get(x, y)[3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if self.get(x + dx, y + dy)[3] > 200:
                        edge.append((x, y))
                        break
        for x, y in edge:
            self.set(x, y, colour)

    def rows(self):
        return self.px


# ---------------------------------------------------------------------------- icons


def icon_dash(accent):
    """Two chevrons streaking right, with three speed lines behind them."""
    canvas = Canvas(16)
    dim = shade(accent, 0.55)
    for i, y in enumerate((4, 8, 12)):
        canvas.line(1, y, 4 - i % 2, y, dim, 1)
    for x0 in (5, 9):
        canvas.line(x0, 2, x0 + 4, 8, shade(accent, 1.0), 2)
        canvas.line(x0 + 4, 8, x0, 14, shade(accent, 0.8), 2)
    canvas.outline()
    return canvas


def icon_fire_burst(accent):
    """A leaning flame with a brighter core."""
    canvas = Canvas(16)
    body = [(9, 0), (9, 5), (11, 4), (12, 8), (11, 12), (8, 15), (5, 13), (4, 9), (6, 5), (6, 9), (7, 6)]
    canvas.polygon(body, shade(accent, 0.7))
    core = [(9, 7), (11, 10), (9, 14), (7, 12), (7, 9)]
    canvas.polygon(core, shade(accent, 1.2))
    canvas.outline()
    return canvas


def icon_ground_pound(accent):
    """A fat down arrow hitting a cracked floor line."""
    canvas = Canvas(16)
    canvas.rect(6, 1, 9, 7, shade(accent, 1.05))
    canvas.polygon([(3, 7), (12, 7), (8, 12)], shade(accent, 0.8))
    canvas.rect(1, 13, 14, 13, shade(accent, 0.55))
    for x in (3, 7, 11):
        canvas.line(x, 14, x + 1, 15, shade(accent, 0.45), 1)
    canvas.outline()
    return canvas


def icon_ender_pull(accent):
    """A target ring being yanked left by an arrow."""
    canvas = Canvas(16)
    canvas.ring(11, 8, 3.3, shade(accent, 0.85), 1.4)
    canvas.disc(11, 8, 1.0, shade(accent, 1.2))
    canvas.rect(3, 7, 5, 8, shade(accent, 1.0))
    canvas.polygon([(0, 7.5), (4, 3), (4, 12)], shade(accent, 1.1))
    canvas.outline()
    return canvas


def icon_shield_dome(accent):
    """A dome over a baseline, with an inner shell."""
    canvas = Canvas(16)
    for y in range(16):
        for x in range(16):
            if y <= 12 and (x - 8) ** 2 + ((y - 12) * 1.05) ** 2 <= 6.5 * 6.5:
                canvas.set(x, y, shade(accent, 0.5))
    canvas.arc(8, 12, 6.5, 0, 180, shade(accent, 1.2), 2)
    canvas.rect(2, 12, 14, 12, shade(accent, 1.0))
    canvas.outline()
    return canvas


def icon_mob_freeze(accent):
    """A six-spoke snowflake."""
    canvas = Canvas(16)
    bright = shade(accent, 1.0)
    dim = shade(accent, 0.7)
    for angle in (90, 30, 330, 270, 210, 150):
        rad = math.radians(angle)
        x = 8 + math.cos(rad) * 6.5
        y = 8 - math.sin(rad) * 6.5
        canvas.line(8, 8, round(x), round(y), bright, 1)
        mx = 8 + math.cos(rad) * 4.0
        my = 8 - math.sin(rad) * 4.0
        for branch in (rad + math.radians(50), rad - math.radians(50)):
            canvas.line(
                round(mx), round(my),
                round(mx + math.cos(branch) * 2.2), round(my - math.sin(branch) * 2.2),
                dim, 1,
            )
    canvas.dot(8, 8, bright, 2)
    canvas.outline()
    return canvas


ICON_BUILDERS = {
    "dash": icon_dash,
    "fire_burst": icon_fire_burst,
    "ground_pound": icon_ground_pound,
    "ender_pull": icon_ender_pull,
    "shield_dome": icon_shield_dome,
    "mob_freeze": icon_mob_freeze,
}


# ---------------------------------------------------------------------------- slot frames


# Slot sprites are drawn into the top-left 22x22 of a 32x32 sheet: CONTRACT.md section 9.1
# wants power-of-two textures, and PowerHudLayer blits a 22x22 region out of a 32x32 texture.
SLOT_SHEET = 32
SLOT_FRAME = 22


def slot_frame(ready):
    """22x22 hotbar-style cell in the corner of a 32x32 sheet. Its 20x20 interior holds the icon."""
    canvas = Canvas(SLOT_SHEET)
    fill = (12, 12, 20, 150) if not ready else (14, 20, 28, 165)
    border = (58, 58, 72, 255) if not ready else (232, 246, 255, 255)
    inner = (30, 30, 42, 255) if not ready else (96, 150, 178, 255)

    canvas.rect(0, 0, 21, 21, (0, 0, 0, 0))
    canvas.rect(1, 1, 20, 20, fill)
    # outer border
    canvas.rect(0, 0, 21, 0, border)
    canvas.rect(0, 21, 21, 21, border)
    canvas.rect(0, 0, 0, 21, border)
    canvas.rect(21, 0, 21, 21, border)
    # inner bevel
    canvas.rect(1, 1, 20, 1, inner)
    canvas.rect(1, 20, 20, 20, inner)
    canvas.rect(1, 1, 1, 20, inner)
    canvas.rect(20, 1, 20, 20, inner)
    if ready:
        # corner ticks so "ready" is readable without colour vision
        for x, y in ((2, 2), (19, 2), (2, 19), (19, 19)):
            canvas.set(x, y, (255, 255, 255, 255))
    return canvas


# ---------------------------------------------------------------------------- sound


def write_ready_chime(path):
    """Two short bright bell notes (E6 then A6), ~0.45 s, fast decay."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    filter_complex = (
        "[0:a]volume=0.55,afade=t=out:st=0.05:d=0.28[a];"
        "[1:a]adelay=110|110,volume=0.45,afade=t=out:st=0.18:d=0.27[b];"
        "[a][b]amix=inputs=2:duration=longest:dropout_transition=0,volume=1.6"
    )
    # Stage 1: ffmpeg renders MONO 44.1 kHz WAV to stdout. Stage 2: oggenc encodes it
    # to Ogg Vorbis -- ffmpeg's native `vorbis` encoder cannot write anything but stereo.
    render = [
        "ffmpeg", "-v", "error", "-y",
        "-f", "lavfi", "-i", "sine=frequency=1319:duration=0.45",
        "-f", "lavfi", "-i", "sine=frequency=1760:duration=0.34",
        "-filter_complex", filter_complex,
        "-ac", "1", "-ar", "44100", "-c:a", "pcm_s16le", "-f", "wav", "-",
    ]
    encode = ["oggenc", "-Q", "-q", "5", "-o", path, "-"]
    try:
        wav = subprocess.run(render, check=True, stdout=subprocess.PIPE).stdout
        subprocess.run(encode, check=True, input=wav)
    except (OSError, subprocess.CalledProcessError) as failure:
        print(
            "ffmpeg/oggenc failed ({}); the .ogg was NOT regenerated. "
            "Need ffmpeg and oggenc (brew install vorbis-tools) on PATH.".format(failure),
            file=sys.stderr,
        )
        return
    print("wrote {}".format(os.path.relpath(path, REPO)))


# ---------------------------------------------------------------------------- main


def main():
    for name, builder in ICON_BUILDERS.items():
        canvas = builder(ACCENTS[name])
        write_png(os.path.join(ICONS, name + ".png"), 16, 16, canvas.rows())

    write_png(os.path.join(GUI, "slot.png"), SLOT_SHEET, SLOT_SHEET, slot_frame(False).rows())
    write_png(os.path.join(GUI, "slot_ready.png"), SLOT_SHEET, SLOT_SHEET, slot_frame(True).rows())
    write_ready_chime(os.path.join(SOUNDS, "ability_ready.ogg"))


if __name__ == "__main__":
    main()
