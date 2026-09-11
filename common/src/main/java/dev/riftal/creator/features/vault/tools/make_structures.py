#!/usr/bin/env python3
"""Builds the seven Cursed Vault jigsaw pieces as vanilla structure-template NBT.

    python3 common/src/main/java/dev/riftal/creator/features/vault/tools/make_structures.py

Output: common/src/main/resources/data/creator_vault/structure/cursed_vault/*.nbt
(singular `structure/` - 1.21 renamed the datapack folder; the world-save export
path is still plural, which is how people get this wrong).

Written by hand rather than exported from a structure block because no dev client
is run in this pass. The file format is exactly what StructureTemplate.load reads:

    root: { DataVersion: int, size: [I;w,h,l] (a TAG_List of 3 TAG_Int),
            palette: [ {Name: "ns:path", Properties: {k: "v"}} ],
            blocks:  [ {pos: [x,y,z], state: int, nbt: {...}} ],
            entities: [] }

gzip-compressed, named root tag with an empty name (NbtIo.readCompressed).

Geometry convention, which is what makes the jigsaws line up:
  * every corridor piece is a 5-wide, 5-tall shell with a 3x3 interior;
  * a doorway is the jigsaw block at (cx, 1, edge) plus plain air at (cx, 2, edge)
    AND (cx, 3, edge), so a connected pair of jigsaws leaves a 1-wide, 3-tall hole.
    Three tall, not two: the Vault Keeper is 2.3 blocks tall (VaultFeature sizes it
    0.8 x 2.3), so a 1x2 doorway physically cannot pass it and the mini-boss could
    never leave the treasure room or walk back into it;
  * the jigsaw sits on the outermost block layer of the piece, so a child piece's
    bounding box starts exactly one block beyond the parent's and never overlaps
    (JigsawPlacement places the child so its jigsaw lands at parentPos + front).

Jigsaw priorities (both verified in the 1.21.1 sources):
  * `selection_priority` orders the jigsaws WITHIN a parent piece -
    SinglePoolElement.sortBySelectionPriority sorts descending, so the entrance's
    treasure anchor (10) claims its space before the three corridor exits (0);
  * `placement_priority` only orders the queue of already-placed children waiting
    to expand (JigsawPlacement.Placer, SequencedPriorityIterator), so it is NOT the
    lever for "resolve the treasure room first" and is left at 0.
"""

import gzip
import os
import random
import struct

DATA_VERSION = 3955  # SharedConstants.WORLD_VERSION for 1.21.1

HERE = os.path.dirname(os.path.abspath(__file__))
COMMON = os.path.abspath(os.path.join(HERE, *([".."] * 9)))
OUT_DIR = os.path.join(COMMON, "src", "main", "resources", "data", "creator_vault",
                       "structure", "cursed_vault")

NS = "creator_vault"

# --------------------------------------------------------------------- NBT out

TAG_END, TAG_BYTE, TAG_INT, TAG_LONG, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 4, 8, 9, 10


def _str(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(tag):
    kind, value = tag
    if kind == "byte":
        return struct.pack(">b", value)
    if kind == "int":
        return struct.pack(">i", value)
    if kind == "long":
        return struct.pack(">q", value)
    if kind == "string":
        return _str(value)
    if kind == "list":
        element_kind, items = value
        type_id = {"": TAG_END, "int": TAG_INT, "compound": TAG_COMPOUND}[element_kind]
        out = struct.pack(">b", type_id) + struct.pack(">i", len(items))
        for item in items:
            out += _payload((element_kind, item)) if element_kind != "compound" else _compound_payload(item)
        return out
    if kind == "compound":
        return _compound_payload(value)
    raise ValueError("unknown tag kind " + kind)


def _type_id(tag):
    return {"byte": TAG_BYTE, "int": TAG_INT, "long": TAG_LONG, "string": TAG_STRING,
            "list": TAG_LIST, "compound": TAG_COMPOUND}[tag[0]]


def _compound_payload(mapping):
    out = b""
    for name, tag in mapping.items():
        out += struct.pack(">b", _type_id(tag)) + _str(name) + _payload(tag)
    return out + struct.pack(">b", TAG_END)


def write_nbt(path, root):
    body = struct.pack(">b", TAG_COMPOUND) + _str("") + _compound_payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as f:
        f.write(body)
    return path


# ------------------------------------------------------------------ block defs

AIR = ("minecraft:air", None)
BRICK = ("minecraft:deepslate_bricks", None)
CRACKED = ("minecraft:cracked_deepslate_bricks", None)
TILE = ("minecraft:deepslate_tiles", None)
POLISHED = ("minecraft:polished_deepslate", None)
CHISELED = ("minecraft:chiseled_deepslate", None)
COBBLED = ("minecraft:cobbled_deepslate", None)
LANTERN = ("minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"})
CHAIN = ("minecraft:chain", {"axis": "y", "waterlogged": "false"})
COBWEB = ("minecraft:cobweb", None)
MAGMA = ("minecraft:magma_block", None)
CANDLE = ("minecraft:soul_fire", None)


def ladder(facing):
    """A ladder whose support block is the one at pos.relative(facing.getOpposite()).

    LadderBlock.canSurvive (LadderBlock.java:63-66) attaches to
    `pos.relative(direction.getOpposite())`, so facing="south" needs a solid block
    one step NORTH of the ladder.
    """
    return ("minecraft:ladder", {"facing": facing, "waterlogged": "false"})


def dispenser(facing):
    return ("minecraft:dispenser", {"facing": facing, "triggered": "false"})


def tripwire_hook(facing):
    """TripWireHookBlock.FACING points away from the wall it is attached to
    (TripWireHookBlock.java:72-77), and a powered hook strongly powers that wall
    block, which in turn powers whatever sits against it."""
    return ("minecraft:tripwire_hook", {"facing": facing, "attached": "true", "powered": "false"})


def tripwire(east_west=True):
    props = {"attached": "true", "disarmed": "false", "powered": "false",
             "north": "false", "south": "false", "east": "false", "west": "false"}
    if east_west:
        props["east"] = "true"
        props["west"] = "true"
    else:
        props["north"] = "true"
        props["south"] = "true"
    return ("minecraft:tripwire", props)


def bars(**sides):
    props = {"east": "false", "north": "false", "south": "false", "west": "false",
             "waterlogged": "false"}
    props.update({k: "true" for k in sides})
    return ("minecraft:iron_bars", props)


def chest(facing):
    return ("minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"})


def sealed_chest(facing):
    return ("%s:sealed_chest" % NS, {"facing": facing})


def altar(state="sealed"):
    return ("%s:cursed_altar" % NS, {"state": state})


def jigsaw(orientation):
    return ("minecraft:jigsaw", {"orientation": orientation})


def jigsaw_nbt(name, target, pool, final_state="minecraft:air", selection_priority=0):
    return {
        "id": ("string", "minecraft:jigsaw"),
        "name": ("string", name),
        "target": ("string", target),
        "pool": ("string", pool),
        "final_state": ("string", final_state),
        "joint": ("string", "aligned"),
        "placement_priority": ("int", 0),
        "selection_priority": ("int", selection_priority),
    }


def chest_nbt(loot_table):
    return {
        "id": ("string", "minecraft:chest"),
        "LootTable": ("string", loot_table),
        "LootTableSeed": ("long", 0),
    }


def dispenser_nbt(item, count):
    """Dispenser loaded with `count` of `item`.

    1.21.1 item shape, read off ItemStack.CODEC (ItemStack.java:103-114): the keys
    are `id` and an INT `count` - the pre-1.20.5 byte `Count` is gone. `Slot` is a
    byte, per ContainerHelper.saveAllItems (ContainerHelper.java:35).
    """
    stack = {
        "Slot": ("byte", 0),
        "id": ("string", item),
        "count": ("int", count),
    }
    return {
        "id": ("string", "minecraft:dispenser"),
        "Items": ("list", ("compound", [stack])),
    }


def sealed_chest_nbt(loot_table):
    return {
        "id": ("string", "%s:sealed_chest" % NS),
        "LootTable": ("string", loot_table),
        "LootSeed": ("long", 0),
    }


VAULT_OUT = "%s:vault_out" % NS
VAULT_IN = "%s:vault_in" % NS
TREASURE_ANCHOR = "%s:treasure_anchor" % NS
TREASURE_IN = "%s:treasure_in" % NS
POOL_CORRIDORS = "%s:cursed_vault/corridors" % NS
POOL_TREASURE = "%s:cursed_vault/treasure" % NS
POOL_EMPTY = "minecraft:empty"

# Front direction -> the ORIENTATION value with top = UP.
ORIENT = {"north": "north_up", "south": "south_up", "east": "east_up", "west": "west_up"}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


# ---------------------------------------------------------------- piece builder

class Piece:
    def __init__(self, name, width, height, length, seed):
        self.name = name
        self.size = (width, height, length)
        self.rng = random.Random(seed)
        self.blocks = {}

    def set(self, x, y, z, block, nbt=None):
        w, h, l = self.size
        if not (0 <= x < w and 0 <= y < h and 0 <= z < l):
            raise IndexError("%s: (%d,%d,%d) outside %s" % (self.name, x, y, z, self.size))
        self.blocks[(x, y, z)] = (block, nbt)

    def shell(self, wall, floor, ceiling, y0=0, y1=None):
        """Fills a y-slab of the box: solid outer shell, air inside, a little decay.

        Defaults to the whole piece; the entrance passes an explicit range so the
        ladder shaft can be built on top of the hall rather than turning the whole
        11x24x11 box into one cathedral.
        """
        w, h, l = self.size
        if y1 is None:
            y1 = h - 1
        for x in range(w):
            for y in range(y0, y1 + 1):
                for z in range(l):
                    edge = x in (0, w - 1) or z in (0, l - 1)
                    if y == y0:
                        block = floor
                    elif y == y1:
                        block = ceiling
                    elif edge:
                        block = wall
                    else:
                        block = AIR
                    if block is wall and self.rng.random() < 0.18:
                        block = CRACKED
                    elif block is floor and self.rng.random() < 0.10:
                        block = COBBLED
                    self.blocks[(x, y, z)] = (block, None)

    def doorway(self, x, y, z, facing, name, target, pool, selection_priority=0):
        """A jigsaw at (x,y,z) plus two air blocks above it - a 1x3 walkable hole.

        Three tall so the 2.3-block Vault Keeper fits through; see the module
        docstring.
        """
        self.set(x, y, z, jigsaw(ORIENT[facing]),
                 jigsaw_nbt(name, target, pool, selection_priority=selection_priority))
        self.set(x, y + 1, z, AIR)
        self.set(x, y + 2, z, AIR)

    def corridor_out(self, x, y, z, facing):
        self.doorway(x, y, z, facing, VAULT_OUT, VAULT_IN, POOL_CORRIDORS)

    def corridor_in(self, x, y, z, facing):
        self.doorway(x, y, z, facing, VAULT_IN, VAULT_OUT, POOL_EMPTY)

    def to_nbt(self):
        palette = []
        palette_index = {}
        blocks = []
        for (x, y, z), (block, nbt) in sorted(self.blocks.items()):
            key = (block[0], tuple(sorted((block[1] or {}).items())))
            if key not in palette_index:
                palette_index[key] = len(palette)
                entry = {"Name": ("string", block[0])}
                if block[1]:
                    entry["Properties"] = ("compound",
                                           {k: ("string", v) for k, v in sorted(block[1].items())})
                palette.append(entry)
            block_tag = {
                "pos": ("list", ("int", [x, y, z])),
                "state": ("int", palette_index[key]),
            }
            if nbt:
                block_tag["nbt"] = ("compound", nbt)
            blocks.append(block_tag)

        w, h, l = self.size
        return {
            "DataVersion": ("int", DATA_VERSION),
            "size": ("list", ("int", [w, h, l])),
            "palette": ("list", ("compound", palette)),
            "blocks": ("list", ("compound", blocks)),
            "entities": ("list", ("", [])),
        }


# ------------------------------------------------------------------- the pieces

# Ladder shaft geometry, shared by the builder and the sanity check below.
SHAFT_X0, SHAFT_X1 = 0, 4          # outer box, inclusive
SHAFT_Z0, SHAFT_Z1 = 0, 4
SHAFT_LADDER_X, SHAFT_LADDER_Z = 2, 1
HALL_TOP_Y = 9                     # the hall's ceiling layer
SHAFT_CAP_Y = 23                   # the plug just under the surface


def build_entrance():
    """The entry hall with the ladder shaft rising out of its corner.

    Plan section 5 specifies `entrance` as 11x24x11, "shaft with ladder + entry hall
    at the bottom", and beat 1 of the clip is digging down into it. The shaft is
    capped at the top rather than opened to daylight: `terrain_adaptation: bury` and
    `start_height {absolute: -30}` put the cap around y=-7, which is what the plan's
    manual-QA line ("confirm the ladder top is capped and no exposed opening") asks
    for. The creator digs down to the cap; `/vault tp` is the shortcut.
    """
    p = Piece("entrance", 11, 24, 11, seed=1)
    p.shell(BRICK, TILE, BRICK, y0=0, y1=HALL_TOP_Y)

    # Four pillars holding the hall up.
    for px, pz in ((3, 3), (7, 3), (3, 7), (7, 7)):
        for y in range(1, 8):
            p.set(px, y, pz, POLISHED)
        p.set(px, 8, pz, CHISELED)
    # Light. Nothing hangs under the shaft mouth, which is open sky as far as the
    # hall ceiling is concerned.
    for lx, lz in ((8, 2), (2, 8), (8, 8)):
        p.set(lx, 8, lz, LANTERN)
    for y in (6, 7):
        p.set(5, y, 5, CHAIN)
    p.set(5, 8, 5, LANTERN)

    # The shaft: a 5x5 brick box with a 3x3 throat, from the hall ceiling up.
    for y in range(HALL_TOP_Y, SHAFT_CAP_Y + 1):
        for x in range(SHAFT_X0, SHAFT_X1 + 1):
            for z in range(SHAFT_Z0, SHAFT_Z1 + 1):
                inside = SHAFT_X0 < x < SHAFT_X1 and SHAFT_Z0 < z < SHAFT_Z1
                if y == SHAFT_CAP_Y:
                    p.set(x, y, z, BRICK)          # the plug
                elif y == HALL_TOP_Y:
                    # Punch the hall ceiling open under the throat; leave the rest
                    # of that layer as the ceiling shell() already laid.
                    if inside:
                        p.set(x, y, z, AIR)
                elif inside:
                    p.set(x, y, z, AIR)
                else:
                    p.set(x, y, z, BRICK)

    # One ladder from the hall floor to just under the cap. Its support is the wall
    # one block NORTH of it at every height: the hall's north wall below the
    # ceiling, the ceiling layer itself, then the shaft's own north wall.
    for y in range(1, SHAFT_CAP_Y):
        p.set(SHAFT_LADDER_X, y, SHAFT_LADDER_Z, ladder("south"))
    # A lantern part-way up so the shaft is not a black hole on camera. LANTERN is
    # hanging=true, so it needs a block directly ABOVE it - hence the brick corbel.
    p.set(3, 17, 3, BRICK)
    p.set(3, 16, 3, LANTERN)

    # Three corridor exits and the single treasure anchor. The anchor gets a high
    # selection_priority so the 13x13 treasure room claims its space before the
    # corridor branches can take it - see the module docstring.
    p.corridor_out(5, 1, 0, "north")
    p.corridor_out(10, 1, 5, "east")
    p.corridor_out(0, 1, 5, "west")
    p.doorway(5, 1, 10, "south", TREASURE_ANCHOR, TREASURE_IN, POOL_TREASURE,
              selection_priority=10)
    return p


def build_corridor_straight():
    p = Piece("corridor_straight", 5, 5, 8, seed=2)
    p.shell(BRICK, TILE, BRICK)
    for z in (2, 5):
        p.set(2, 3, z, LANTERN)
    p.set(1, 2, 4, bars(south=True, north=True))
    p.corridor_in(2, 1, 0, "north")
    p.corridor_out(2, 1, 7, "south")
    return p


def build_corridor_corner():
    p = Piece("corridor_corner", 5, 5, 5, seed=3)
    p.shell(BRICK, TILE, BRICK)
    p.set(2, 3, 2, LANTERN)
    p.set(1, 1, 3, COBWEB)
    p.corridor_in(2, 1, 0, "north")
    p.corridor_out(4, 1, 2, "east")
    return p


def build_corridor_t():
    p = Piece("corridor_t", 5, 5, 5, seed=4)
    p.shell(BRICK, TILE, BRICK)
    p.set(2, 3, 2, LANTERN)
    for y in (1, 2):
        p.set(2, y, 3, CHAIN)
    p.corridor_in(2, 1, 0, "north")
    p.corridor_out(4, 1, 2, "east")
    p.corridor_out(0, 1, 2, "west")
    return p


def build_corridor_end():
    p = Piece("corridor_end", 5, 5, 3, seed=5)
    p.shell(BRICK, TILE, BRICK)
    p.set(2, 3, 1, LANTERN)
    p.set(1, 1, 1, COBWEB)
    p.set(3, 1, 1, COBWEB)
    p.corridor_in(2, 1, 0, "north")
    return p


# The two tripwire lines across the trap room, at these z rows.
TRAP_WIRE_ROWS = (3, 7)

# Arrows per dispenser. One stack-ish: enough for several takes before a reload.
TRAP_ARROWS = 16


def build_trap_room():
    """The trap room: two tripwire lines, four arrow dispensers, one reward chest.

    Plan beat 2 is "a trap room where the floor drops arrows (dispensers via
    structure NBT)" and section 6 spells it out as "dispensers loaded with arrows
    behind tripwire". The wiring is the plainest arrangement vanilla has, so it
    works with no redstone dust to get wrong:

        hook at (1, 1, z) facing east   -> attached to the wall block (0, 1, z)
        dispenser at (0, 2, z)          -> sits directly on that wall block

    A tripped hook STRONGLY powers the block it is attached to
    (TripWireHookBlock.getDirectSignal, TripWireHookBlock.java:262-268); a solid
    strongly-powered block then reads as powered to every neighbour, which is what
    DispenserBlock.neighborChanged tests. Walking the line fires all four.
    """
    p = Piece("trap_room", 11, 6, 11, seed=6)
    p.shell(BRICK, TILE, BRICK)

    for z in TRAP_WIRE_ROWS:
        # Hooks face into the room; their support is the shell wall behind them.
        p.set(1, 1, z, tripwire_hook("east"))
        p.set(9, 1, z, tripwire_hook("west"))
        for x in range(2, 9):
            p.set(x, 1, z, tripwire(east_west=True))
        # Dispensers replace a wall block each, one block above the hook's anchor,
        # firing across the room at ankle height.
        p.set(0, 2, z, dispenser("east"), dispenser_nbt("minecraft:arrow", TRAP_ARROWS))
        p.set(10, 2, z, dispenser("west"), dispenser_nbt("minecraft:arrow", TRAP_ARROWS))

    # A little dressing that is NOT the trap: a cobweb snag either side of the
    # centre and a scorched floor, so the room still reads as dangerous on camera.
    for x in (2, 8):
        p.set(x, 1, 5, COBWEB)
    for x in range(4, 7):
        p.set(x, 0, 5, MAGMA)

    p.set(5, 0, 5, POLISHED)
    p.set(5, 1, 5, chest("north"), chest_nbt("%s:chests/cursed_vault_trap" % NS))
    for lx, lz in ((2, 2), (8, 2), (2, 8), (8, 8)):
        p.set(lx, 4, lz, LANTERN)
    for x in (1, 9):
        for z in (4, 6):
            p.set(x, 3, z, bars(north=True, south=True))
    p.corridor_in(5, 1, 0, "north")
    p.corridor_out(5, 1, 10, "south")
    return p


def build_treasure_end():
    """The cap for the treasure anchor when the treasure room does not fit.

    `cursed_vault/treasure`'s fallback used to be `minecraft:empty`, so a treasure
    room that intersected a corridor branch left the entrance's south doorway open
    into raw stone AND the vault with no Cursed Altar at all. It cannot fall back to
    `cursed_vault/corridor_ends`: a child only attaches if it owns a jigsaw whose
    NAME equals the parent's TARGET, and corridor_end's jigsaw is named
    `creator_vault:vault_in`, not `creator_vault:treasure_in`. Hence this piece -
    identical in spirit to corridor_end, but wearing the treasure connector.
    """
    p = Piece("treasure_end", 5, 5, 3, seed=8)
    p.shell(BRICK, TILE, BRICK)
    p.set(2, 3, 1, LANTERN)
    p.set(1, 1, 1, COBWEB)
    p.set(3, 1, 1, COBWEB)
    p.doorway(2, 1, 0, "north", TREASURE_IN, TREASURE_ANCHOR, POOL_EMPTY)
    return p


def build_treasure_room():
    p = Piece("treasure_room", 13, 8, 13, seed=7)
    p.shell(BRICK, TILE, BRICK)
    # Raised dais.
    for x in range(4, 9):
        for z in range(4, 9):
            p.set(x, 1, z, POLISHED)
    for x in range(5, 8):
        for z in range(5, 8):
            p.set(x, 2, z, CHISELED)
    p.set(6, 3, 6, altar("sealed"))
    # Four slack chains above the altar.
    for y in (5, 6):
        p.set(6, y, 6, CHAIN)
    # The reward, three blocks behind the altar, facing whoever walks in.
    p.set(6, 2, 9, sealed_chest("north"), sealed_chest_nbt("%s:chests/cursed_vault" % NS))
    p.set(6, 1, 9, POLISHED)
    # Light and framing.
    for lx, lz in ((2, 2), (10, 2), (2, 10), (10, 10)):
        p.set(lx, 6, lz, LANTERN)
        for y in range(1, 6):
            p.set(lx, y, lz, POLISHED)
    for z in (4, 8):
        p.set(1, 2, z, bars(north=True, south=True))
        p.set(11, 2, z, bars(north=True, south=True))
    p.doorway(6, 1, 0, "north", TREASURE_IN, TREASURE_ANCHOR, POOL_EMPTY)
    return p


BUILDERS = [
    build_entrance,
    build_corridor_straight,
    build_corridor_corner,
    build_corridor_t,
    build_corridor_end,
    build_trap_room,
    build_treasure_room,
    build_treasure_end,
]


def main():
    for builder in BUILDERS:
        piece = builder()
        path = os.path.join(OUT_DIR, piece.name + ".nbt")
        write_nbt(path, piece.to_nbt())
        print("%-22s %2dx%2dx%2d  %5d blocks  -> %s"
              % ((piece.name,) + piece.size + (len(piece.blocks),
                 os.path.relpath(path, COMMON))))


if __name__ == "__main__":
    main()
