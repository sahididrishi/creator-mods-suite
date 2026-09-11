package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the hand-written worldgen JSON, because every one of these mistakes fails silently in
 * game: a template pool that names an {@code .nbt} which is not in the jar generates an empty
 * structure, and a {@code loot_table}/{@code structure} folder spelled with the pre-1.21 plural is
 * simply never read.
 *
 * <p>Reads the resource tree off disk rather than through the classpath so it does not depend on
 * how the {@code test} source set is wired; if the tree cannot be found the test is skipped rather
 * than failed.
 */
class VaultStructureDataTest {

    private static final String NS = "creator_vault";

    private static Path data;

    @BeforeAll
    static void locateResources() {
        Path[] candidates = {
                Path.of("src/main/resources/data/" + NS),
                Path.of("common/src/main/resources/data/" + NS),
                Path.of("../common/src/main/resources/data/" + NS)
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                data = candidate;
                return;
            }
        }
        Assumptions.abort("creator_vault data tree not found from " + Path.of(".").toAbsolutePath());
    }

    private static JsonObject json(String relative) {
        Path path = data.resolve(relative);
        assertTrue(Files.isRegularFile(path), "missing " + path);
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void structureUsesTheVanillaJigsawTypeAndStaysUnderground() {
        JsonObject structure = json("worldgen/structure/cursed_vault.json");
        assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
        assertEquals("underground_structures", structure.get("step").getAsString());
        assertEquals("bury", structure.get("terrain_adaptation").getAsString());
        assertEquals("ignore_waterlogging", structure.get("liquid_settings").getAsString());
        assertFalse(structure.get("use_expansion_hack").getAsBoolean());
        assertTrue(structure.has("spawn_overrides"), "spawn_overrides is NOT optional in 1.21.1");

        int size = structure.get("size").getAsInt();
        assertTrue(size >= 0 && size <= 20, "jigsaw size must be 0..20, was " + size);

        int range = structure.get("max_distance_from_center").getAsInt();
        assertTrue(range >= 1 && range <= 128, "max_distance_from_center must be 1..128");
        // JigsawStructure#verifyRange adds 12 for every terrain adaptation except NONE.
        assertTrue(range + 12 <= 128, "range + terrain adaptation must not exceed 128");

        assertEquals("#" + NS + ":has_structure/cursed_vault", structure.get("biomes").getAsString());
        assertEquals(NS + ":cursed_vault/entrance", structure.get("start_pool").getAsString());

        int startY = structure.getAsJsonObject("start_height").get("absolute").getAsInt();
        assertTrue(startY < 0, "the vault is an underground structure, start_height was " + startY);
    }

    @Test
    void structureSetSpacingIsGreaterThanSeparation() {
        JsonObject set = json("worldgen/structure_set/cursed_vaults.json");
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("minecraft:random_spread", placement.get("type").getAsString());
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(spacing > separation, "spacing must be greater than separation");
        assertTrue(placement.get("salt").getAsLong() >= 0, "salt must be non-negative");

        JsonArray structures = set.getAsJsonArray("structures");
        assertEquals(1, structures.size());
        assertEquals(NS + ":cursed_vault",
                structures.get(0).getAsJsonObject().get("structure").getAsString());
    }

    @Test
    void everyPoolElementHasAnNbtFile() {
        List<String> locations = new ArrayList<>();
        for (Path pool : poolFiles()) {
            JsonObject root = json(data.relativize(pool).toString());
            for (JsonElement element : root.getAsJsonArray("elements")) {
                JsonObject single = element.getAsJsonObject().getAsJsonObject("element");
                assertEquals("minecraft:single_pool_element",
                        single.get("element_type").getAsString());
                assertEquals("rigid", single.get("projection").getAsString());
                locations.add(single.get("location").getAsString());
            }
        }
        assertFalse(locations.isEmpty(), "no pool elements found at all");
        for (String location : locations) {
            assertTrue(location.startsWith(NS + ":"), "foreign piece referenced: " + location);
            String path = location.substring((NS + ":").length());
            // 1.21 renamed the datapack folder to the singular `structure`.
            Path nbt = data.resolve("structure").resolve(path + ".nbt");
            assertTrue(Files.isRegularFile(nbt), "pool references a missing piece: " + nbt);
        }
    }

    @Test
    void everyFallbackIsEmptyOrAPoolWeShip() {
        for (Path pool : poolFiles()) {
            String fallback = json(data.relativize(pool).toString()).get("fallback").getAsString();
            if ("minecraft:empty".equals(fallback)) {
                continue;
            }
            assertTrue(fallback.startsWith(NS + ":"), "foreign fallback pool: " + fallback);
            Path target = data.resolve("worldgen/template_pool")
                    .resolve(fallback.substring((NS + ":").length()) + ".json");
            assertTrue(Files.isRegularFile(target), "fallback points at a missing pool: " + target);
        }
    }

    @Test
    void lootAndTagFoldersUseTheOnePointTwentyOneSpelling() {
        assertTrue(Files.isDirectory(data.resolve("loot_table")), "1.21 uses singular loot_table/");
        assertFalse(Files.exists(data.resolve("loot_tables")), "loot_tables/ is pre-1.21");
        assertFalse(Files.exists(data.resolve("structures")), "structures/ is pre-1.21");
        assertTrue(Files.isRegularFile(data.resolve("loot_table/chests/cursed_vault.json")));
        assertTrue(Files.isRegularFile(data.resolve("loot_table/blocks/cursed_altar.json")));
        assertTrue(Files.isRegularFile(data.resolve("loot_table/blocks/sealed_chest.json")));
        assertTrue(Files.isRegularFile(data.resolve("loot_table/entities/vault_keeper.json")));
        assertTrue(Files.isRegularFile(
                data.resolve("tags/worldgen/biome/has_structure/cursed_vault.json")));
        assertTrue(Files.isRegularFile(data.resolve("tags/worldgen/structure/cursed_vault.json")),
                "/vault tp resolves the structure through this tag");
    }

    @Test
    void chestLootIsWeightedAndBounded() {
        JsonObject table = json("loot_table/chests/cursed_vault.json");
        assertEquals("minecraft:chest", table.get("type").getAsString());
        int entries = 0;
        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            for (JsonElement entry : poolElement.getAsJsonObject().getAsJsonArray("entries")) {
                JsonObject item = entry.getAsJsonObject();
                if (item.has("weight")) {
                    assertTrue(item.get("weight").getAsInt() > 0, "weights must be positive");
                }
                if (item.has("functions")) {
                    for (JsonElement fn : item.getAsJsonArray("functions")) {
                        JsonObject function = fn.getAsJsonObject();
                        if ("minecraft:set_count".equals(function.get("function").getAsString())) {
                            JsonObject count = function.getAsJsonObject("count");
                            int min = count.get("min").getAsInt();
                            int max = count.get("max").getAsInt();
                            assertTrue(min >= 1 && min <= max && max <= 64,
                                    "bad set_count " + min + ".." + max);
                        }
                    }
                }
                entries++;
            }
        }
        assertTrue(entries >= 5, "the reward chest should feel worth the fight");
    }

    private static List<Path> poolFiles() {
        Path root = data.resolve("worldgen/template_pool");
        assertTrue(Files.isDirectory(root), "missing " + root);
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
