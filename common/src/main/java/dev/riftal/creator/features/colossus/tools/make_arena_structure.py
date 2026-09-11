#!/usr/bin/env python3
"""Writes data/creator_colossus/structure/arena_24.nbt - the GameTest arena for this feature.

The scaffold's `creator_colossus:empty` is 9x9x9, which is smaller than the Colossus' own
shockwave (7 blocks) and than the radius it summons minions at (4 blocks), so the boss tests
need a bigger floor. This produces the same tag shape as `empty.nbt`, verified by parsing it:

    DataVersion : TAG_Int    3955            (1.21.1)
    size        : TAG_List of 3 TAG_Int
    palette     : TAG_List of TAG_Compound   [{Name: polished_andesite}, {Name: air}]
    blocks      : TAG_List of TAG_Compound   [{pos: [x,y,z], state: int}]
    entities    : TAG_List (empty)

Floor is y=0 polished andesite, everything above is air, exactly like `empty.nbt`.

Run from the repo root:
  python3 common/src/main/java/dev/riftal/creator/features/colossus/tools/make_arena_structure.py
"""

import gzip
import os
import struct

DATA_VERSION = 3955  # 1.21.1, read out of the shipped empty.nbt
SIZE = (24, 12, 24)
OUT = "common/src/main/resources/data/creator_colossus/structure/arena_24.nbt"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def s(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named(tag_id, name, payload):
    return struct.pack(">b", tag_id) + s(name) + payload


def int_payload(value):
    return struct.pack(">i", value)


def int_list_payload(values):
    return struct.pack(">bi", TAG_INT, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_list_payload(compounds):
    return struct.pack(">bi", TAG_COMPOUND, len(compounds)) + b"".join(compounds)


def empty_list_payload():
    return struct.pack(">bi", TAG_END, 0)


def block_entry(x, y, z, state):
    body = named(TAG_LIST, "pos", int_list_payload([x, y, z]))
    body += named(TAG_INT, "state", int_payload(state))
    return body + struct.pack(">b", TAG_END)


def palette_entry(block_name):
    return named(TAG_STRING, "Name", s(block_name)) + struct.pack(">b", TAG_END)


def main():
    width, height, depth = SIZE
    blocks = []
    for x in range(width):
        for y in range(height):
            for z in range(depth):
                blocks.append(block_entry(x, y, z, 0 if y == 0 else 1))

    root = b""
    root += named(TAG_INT, "DataVersion", int_payload(DATA_VERSION))
    root += named(TAG_LIST, "size", int_list_payload(list(SIZE)))
    root += named(TAG_LIST, "palette", compound_list_payload([
        palette_entry("minecraft:polished_andesite"),
        palette_entry("minecraft:air"),
    ]))
    root += named(TAG_LIST, "blocks", compound_list_payload(blocks))
    root += named(TAG_LIST, "entities", empty_list_payload())
    root += struct.pack(">b", TAG_END)

    payload = struct.pack(">b", TAG_COMPOUND) + s("") + root

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as handle:
        handle.write(payload)
    print("wrote", OUT, os.path.getsize(OUT), "bytes,", len(blocks), "blocks")


if __name__ == "__main__":
    main()
