package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.vault.worldgen.VaultStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reads the shipped structure templates back out of {@code data/creator_vault/structure/} and
 * asserts the things a level designer can only otherwise check by generating a world and flying
 * through it.
 *
 * <p>Every one of these guards a bug that shipped once already: an entrance with no shaft and no
 * ladder (so the vault was a sealed buried box), a "trap room" with no trap in it (magma and
 * cobwebs instead of the dispensers the plan asks for), and 1x2 doorways that a 2.3-block-tall
 * Vault Keeper physically cannot walk through.
 *
 * <p>{@code NbtIo} touches no registry, so this runs in plain JUnit with the game unbooted.
 */
class VaultPieceNbtTest {

    private static final String NS = "creator_vault";

    private static Path data;

    /** One decoded template: its size and every block in it, keyed by position. */
    private record Piece(String name, int[] size, Map<List<Integer>, Block> blocks) {

        Block at(int x, int y, int z) {
            return this.blocks.get(List.of(x, y, z));
        }

        boolean airAt(int x, int y, int z) {
            Block block = at(x, y, z);
            return block != null && "minecraft:air".equals(block.name());
        }

        List<Block> named(String id) {
            return this.blocks.values().stream().filter(b -> b.name().equals(id)).toList();
        }
    }

    /** One block inside a template: its id, its blockstate properties and its block-entity NBT. */
    private record Block(String name, Map<String, String> properties, CompoundTag nbt, int[] pos) {
    }

    @BeforeAll
    static void locateResources() {
        for (String candidate : new String[]{
                "src/main/resources/data/" + NS,
                "common/src/main/resources/data/" + NS,
                "../common/src/main/resources/data/" + NS}) {
            if (Files.isDirectory(Path.of(candidate))) {
                data = Path.of(candidate);
                return;
            }
        }
        Assumptions.abort("creator_vault data tree not found from " + Path.of(".").toAbsolutePath());
    }

    private static Piece piece(String name) {
        Path path = data.resolve("structure/cursed_vault").resolve(name + ".nbt");
        assertTrue(Files.isRegularFile(path), "missing piece " + path);
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ListTag sizeTag = root.getList("size", Tag.TAG_INT);
        assertEquals(3, sizeTag.size(), name + ": size must be three ints");
        int[] size = {sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2)};

        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        List<Map<String, String>> props = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            names.add(entry.getString("Name"));
            Map<String, String> p = new HashMap<>();
            CompoundTag properties = entry.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                p.put(key, properties.getString(key));
            }
            props.add(p);
        }

        Map<List<Integer>, Block> blocks = new HashMap<>();
        ListTag blockTag = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockTag.size(); i++) {
            CompoundTag entry = blockTag.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int state = entry.getInt("state");
            int[] xyz = {pos.getInt(0), pos.getInt(1), pos.getInt(2)};
            blocks.put(List.of(xyz[0], xyz[1], xyz[2]),
                    new Block(names.get(state), props.get(state),
                            entry.contains("nbt") ? entry.getCompound("nbt") : null, xyz));
        }
        return new Piece(name, size, blocks);
    }

    // ------------------------------------------------------------- the entrance

    @Test
    void theEntranceHasAShaftWithALadderInIt() {
        Piece entrance = piece("entrance");
        assertTrue(entrance.size()[1] >= 24,
                "plan section 5 specifies an 11x24x11 entrance - shaft plus hall - but this one is "
                        + entrance.size()[1] + " tall, i.e. hall only");

        List<Block> ladders = entrance.named("minecraft:ladder");
        assertTrue(ladders.size() >= 12,
                "a shaft you cannot climb is not a shaft; found " + ladders.size() + " ladder(s)");

        int lowest = ladders.stream().mapToInt(b -> b.pos()[1]).min().orElseThrow();
        int highest = ladders.stream().mapToInt(b -> b.pos()[1]).max().orElseThrow();
        assertEquals(1, lowest, "the ladder must start at the hall floor");
        assertTrue(highest >= entrance.size()[1] - 3,
                "the ladder stops at y=" + highest + ", well short of the top of the "
                        + entrance.size()[1] + "-tall piece");

        // One unbroken run, or the climb dead-ends part way up.
        Set<Integer> rungs = new LinkedHashSet<>();
        for (Block ladder : ladders) {
            rungs.add(ladder.pos()[1]);
        }
        for (int y = lowest; y <= highest; y++) {
            assertTrue(rungs.contains(y), "the ladder has a gap at y=" + y);
        }

        // Every rung needs its support block, or the whole column pops off on first block update.
        for (Block ladder : ladders) {
            String facing = ladder.properties().get("facing");
            assertEquals("south", facing, "the shaft ladder is hung on the north wall");
            Block support = entrance.at(ladder.pos()[0], ladder.pos()[1], ladder.pos()[2] - 1);
            assertTrue(support != null && !"minecraft:air".equals(support.name()),
                    "ladder at y=" + ladder.pos()[1] + " has nothing to hang on");
        }
    }

    @Test
    void theShaftIsCappedRatherThanOpenToTheSky() {
        // terrain_adaptation is `bury` and start_height is absolute, so an open shaft would be a
        // hole into a cave system rather than a way in. The plan's manual-QA line asks for a cap.
        Piece entrance = piece("entrance");
        int top = entrance.size()[1] - 1;
        Block ladder = entrance.named("minecraft:ladder").stream()
                .max((a, b) -> Integer.compare(a.pos()[1], b.pos()[1])).orElseThrow();
        Block above = entrance.at(ladder.pos()[0], top, ladder.pos()[2]);
        assertTrue(above != null && !"minecraft:air".equals(above.name()),
                "the top of the shaft is open - " + (above == null ? "nothing" : above.name()));
    }

    // ------------------------------------------------------------ the trap room

    @Test
    void theTrapRoomHasDispensersLoadedWithArrows() {
        Piece trap = piece("trap_room");
        List<Block> dispensers = trap.named("minecraft:dispenser");
        assertTrue(dispensers.size() >= 2,
                "plan beat 2 is a room where the floor drops arrows; found "
                        + dispensers.size() + " dispenser(s)");
        for (Block dispenser : dispensers) {
            CompoundTag nbt = dispenser.nbt();
            assertTrue(nbt != null, "a dispenser with no inventory NBT fires nothing");
            ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
            assertFalse(items.isEmpty(), "empty dispenser at " + List.of(dispenser.pos()[0],
                    dispenser.pos()[1], dispenser.pos()[2]));
            CompoundTag stack = items.getCompound(0);
            assertEquals("minecraft:arrow", stack.getString("id"));
            // 1.21.1 item shape: `count` as an int. The pre-1.20.5 byte `Count` is silently ignored.
            assertTrue(stack.contains("count"), "1.21.1 stacks use `count`, not `Count`");
            assertTrue(stack.getInt("count") > 0, "a dispenser loaded with zero arrows");
        }
    }

    @Test
    void theTrapRoomHasTripwireToSetTheDispensersOff() {
        Piece trap = piece("trap_room");
        List<Block> hooks = trap.named("minecraft:tripwire_hook");
        List<Block> wire = trap.named("minecraft:tripwire");
        assertTrue(hooks.size() >= 2, "a tripwire line needs a hook at each end");
        assertFalse(wire.isEmpty(), "hooks with no wire between them never trip");
        assertEquals(0, hooks.size() % 2, "hooks come in pairs");

        // Each hook must be against a solid block, and the dispenser it drives sits on that block.
        for (Block hook : hooks) {
            String facing = hook.properties().get("facing");
            int dx = "east".equals(facing) ? -1 : "west".equals(facing) ? 1 : 0;
            int dz = "south".equals(facing) ? -1 : "north".equals(facing) ? 1 : 0;
            int ax = hook.pos()[0] + dx;
            int ay = hook.pos()[1];
            int az = hook.pos()[2] + dz;
            Block anchor = trap.at(ax, ay, az);
            assertTrue(anchor != null && !"minecraft:air".equals(anchor.name()),
                    "tripwire hook at " + List.of(hook.pos()[0], ay, hook.pos()[2])
                            + " is attached to nothing");
            Block driven = trap.at(ax, ay + 1, az);
            assertTrue(driven != null && "minecraft:dispenser".equals(driven.name()),
                    "nothing is wired to the hook at "
                            + List.of(hook.pos()[0], ay, hook.pos()[2]));
        }
    }

    @Test
    void theTrapChestCarriesTheLootTableTheFeatureDeclares() {
        Piece trap = piece("trap_room");
        List<Block> chests = trap.named("minecraft:chest");
        assertEquals(1, chests.size(), "one reward chest in the trap room");
        CompoundTag nbt = chests.get(0).nbt();
        assertTrue(nbt != null, "the trap chest has no block-entity NBT, so it rolls nothing");
        // Consumes the constant rather than re-typing the id, which is the whole reason
        // VaultFeature.TRAP_LOOT_TABLE exists.
        assertEquals(VaultFeature.TRAP_LOOT_TABLE.location().toString(), nbt.getString("LootTable"));
        assertTrue(Files.isRegularFile(data.resolve("loot_table/chests/cursed_vault_trap.json")));
    }

    // --------------------------------------------------------------- every piece

    @Test
    void everyDoorwayIsWideEnoughForTheKeeperToWalkThrough() {
        // VaultFeature sizes the Keeper 0.8 x 2.3, so a doorway needs three blocks of air, not two.
        // With 1x2 doorways the mini-boss could never leave (or re-enter) the treasure room and the
        // tether teleport became the only way it ever came back.
        for (String name : VaultStructures.PIECES) {
            Piece p = piece(name);
            for (Block jigsaw : p.named("minecraft:jigsaw")) {
                int x = jigsaw.pos()[0];
                int y = jigsaw.pos()[1];
                int z = jigsaw.pos()[2];
                assertTrue(p.airAt(x, y + 1, z),
                        name + ": no headroom above the jigsaw at " + List.of(x, y, z));
                assertTrue(p.airAt(x, y + 2, z),
                        name + ": doorway at " + List.of(x, y, z)
                                + " is only two blocks tall - the 2.3-block Keeper cannot pass it");
            }
        }
    }

    @Test
    void everyPieceNamedByTheConstantsIsOnDisk() {
        for (String name : VaultStructures.PIECES) {
            Piece p = piece(name);
            assertFalse(p.blocks().isEmpty(), name + " decodes to an empty template");
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(p.size()[axis] > 0 && p.size()[axis] <= 48,
                        name + " is " + p.size()[axis] + " on axis " + axis
                                + "; structure templates cap at 48");
            }
        }
    }

    @Test
    void everyShippedPieceIsNamedByTheConstants() {
        Path dir = data.resolve("structure/cursed_vault");
        assertTrue(Files.isDirectory(dir), "missing " + dir);
        try (var stream = Files.list(dir)) {
            for (Path nbt : stream.filter(p -> p.toString().endsWith(".nbt")).toList()) {
                String name = nbt.getFileName().toString().replace(".nbt", "");
                assertTrue(VaultStructures.PIECES.contains(name),
                        "piece on disk that VaultStructures.PIECES does not name: " + name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theTreasureAnchorOutranksTheCorridorExits() {
        // SinglePoolElement.sortBySelectionPriority sorts DESCENDING, so the highest
        // selection_priority jigsaw on the entrance is offered its child first. That is what stops
        // a corridor branch taking the space the 13x13 treasure room needs.
        Piece entrance = piece("entrance");
        int anchor = Integer.MIN_VALUE;
        int corridors = Integer.MIN_VALUE;
        for (Block jigsaw : entrance.named("minecraft:jigsaw")) {
            CompoundTag nbt = jigsaw.nbt();
            assertTrue(nbt != null, "a jigsaw block with no NBT connects to nothing");
            int priority = nbt.getInt("selection_priority");
            if (nbt.getString("name").endsWith("treasure_anchor")) {
                anchor = Math.max(anchor, priority);
            } else {
                corridors = Math.max(corridors, priority);
            }
        }
        assertTrue(anchor > Integer.MIN_VALUE, "the entrance has no treasure anchor at all");
        assertTrue(anchor > corridors,
                "the treasure anchor (" + anchor + ") must outrank the corridor exits ("
                        + corridors + ") or the treasure room can be crowded out");
    }

    @Test
    void theTreasureFallbackCanActuallyAttachToTheAnchor() {
        // A child only attaches when one of ITS jigsaws is NAMED what the parent jigsaw TARGETS.
        // corridor_end wears creator_vault:vault_in, so pointing the treasure pool's fallback at
        // cursed_vault/corridor_ends would have left the hole open just as `minecraft:empty` did.
        String target = null;
        for (Block jigsaw : piece("entrance").named("minecraft:jigsaw")) {
            if (jigsaw.nbt().getString("name").endsWith("treasure_anchor")) {
                target = jigsaw.nbt().getString("target");
            }
        }
        assertTrue(target != null, "the entrance has no treasure anchor");

        boolean matched = false;
        for (Block jigsaw : piece("treasure_end").named("minecraft:jigsaw")) {
            matched |= target.equals(jigsaw.nbt().getString("name"));
        }
        assertTrue(matched, "treasure_end has no jigsaw named " + target
                + ", so it can never cap the treasure anchor");
    }
}
