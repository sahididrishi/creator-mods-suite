package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The reward and trap loot tables: weights, counts, and the entries the clip depends on being
 * there every single time.
 *
 * <p>Plan section 9 lists these as {@code VaultLootWeightsTest} over a pure
 * {@code loot/VaultLootWeights} weight table shared with datagen. There is no such class here and
 * there should not be: datagen is banned on this project (CONTRACT.md section 7), so the JSON is
 * hand-written and is the only definition of the weights. A Java mirror of it would be a second
 * copy that can silently drift from the file the game actually reads. The assertions the plan
 * wanted - positive weights, sane counts, a sampled distribution inside tolerance, a guaranteed
 * headline item - are made here directly against that JSON instead.
 */
class VaultLootTableTest {

    private static final String NS = "creator_vault";

    /** Draws for the distribution check. Large enough that 0.01 is many standard errors. */
    private static final int SAMPLES = 100_000;

    /** Allowed absolute deviation between an entry's share of the draws and its declared share. */
    private static final double TOLERANCE = 0.01D;

    private static Path data;

    /** One weighted entry: what it gives and how often. */
    private record Entry(String item, int weight, int minCount, int maxCount) {
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

    // ------------------------------------------------------------------- shape

    @Test
    void weightsArePositiveAndCountsAreInRange() {
        for (String table : List.of("cursed_vault", "cursed_vault_trap")) {
            for (Entry entry : entries(table, 1)) {
                assertTrue(entry.weight() > 0,
                        table + ": " + entry.item() + " has weight " + entry.weight()
                                + ", which makes it unreachable");
                assertTrue(entry.minCount() >= 1,
                        table + ": " + entry.item() + " can roll " + entry.minCount());
                assertTrue(entry.minCount() <= entry.maxCount(),
                        table + ": " + entry.item() + " has min > max");
                assertTrue(entry.maxCount() <= 64,
                        table + ": " + entry.item() + " can roll " + entry.maxCount()
                                + ", more than a stack");
            }
        }
    }

    @Test
    void theRewardChestIsWorthTheFight() {
        List<Entry> loot = entries("cursed_vault", 1);
        assertTrue(loot.size() >= 8, "only " + loot.size() + " things can come out of the vault");
        assertTrue(loot.stream().anyMatch(e -> e.item().equals("minecraft:diamond")));
        assertTrue(loot.stream().anyMatch(e -> e.item().startsWith(NS + ":")),
                "the reward chest should be able to hand back a Vault Key for the next take");
    }

    // ------------------------------------------------------------ distribution

    @Test
    void sampledDistributionMatchesTheDeclaredWeights() {
        // The same cumulative-weight pick vanilla's LootPool uses. This is the assertion that
        // catches a weight edited by one digit: the JSON still parses, the table still loads, and
        // the echo shard quietly becomes as common as gold.
        for (String table : List.of("cursed_vault", "cursed_vault_trap")) {
            List<Entry> loot = entries(table, 1);
            int total = loot.stream().mapToInt(Entry::weight).sum();
            assertTrue(total > 0, table + " has no weighted entries at all");

            Map<String, Integer> drawn = new LinkedHashMap<>();
            // A fixed seed on java.util.Random, which is specified to the bit, so this test can
            // never be flaky across JDKs or machines.
            RandomGenerator random = new Random(0xC0FFEEL);
            for (int i = 0; i < SAMPLES; i++) {
                int roll = random.nextInt(total);
                for (Entry entry : loot) {
                    roll -= entry.weight();
                    if (roll < 0) {
                        drawn.merge(entry.item(), 1, Integer::sum);
                        break;
                    }
                }
            }

            for (Entry entry : loot) {
                double expected = (double) entry.weight() / total;
                double actual = drawn.getOrDefault(entry.item(), 0) / (double) SAMPLES;
                assertTrue(Math.abs(expected - actual) <= TOLERANCE,
                        table + ": " + entry.item() + " expected " + expected + ", drew " + actual);
            }
            assertEquals(loot.size(), drawn.size(),
                    table + ": some entry was never drawn in " + SAMPLES + " rolls");
        }
    }

    // -------------------------------------------------------------- guarantees

    @Test
    void theRewardChestAlwaysContainsTheEnchantedBook() {
        // Plan beat 5 promises "custom loot (enchanted book, diamonds...)" on camera every take, so
        // the headline item cannot sit in the weighted pool where a bad roll can skip it.
        JsonObject pool = pools("cursed_vault").get(0).getAsJsonObject();
        assertEquals(1.0D, rolls(pool), "the guaranteed pool must roll exactly once");
        var entries = pool.getAsJsonArray("entries");
        assertEquals(1, entries.size(), "a second entry here makes the book a coin flip");
        JsonObject book = entries.get(0).getAsJsonObject();
        assertEquals("minecraft:book", book.get("name").getAsString());
        assertTrue(book.has("functions"), "an un-enchanted book is not a reward");
        boolean enchanted = false;
        for (JsonElement fn : book.getAsJsonArray("functions")) {
            enchanted |= "minecraft:enchant_with_levels"
                    .equals(fn.getAsJsonObject().get("function").getAsString());
        }
        assertTrue(enchanted, "the guaranteed book is never enchanted");
    }

    @Test
    void theTrapChestAlwaysContainsAVaultKey() {
        // Section 3 "out of scope" says the key comes from /vault key and a 100% drop in the trap
        // room - which is the only way a survival player ever opens the altar.
        JsonObject pool = pools("cursed_vault_trap").get(0).getAsJsonObject();
        assertEquals(1.0D, rolls(pool));
        var entries = pool.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals(NS + ":vault_key", entries.get(0).getAsJsonObject().get("name").getAsString());
    }

    // ----------------------------------------------------------------- helpers

    private static double rolls(JsonObject pool) {
        JsonElement value = pool.get("rolls");
        return value.isJsonObject()
                ? value.getAsJsonObject().get("min").getAsDouble()
                : value.getAsDouble();
    }

    private static com.google.gson.JsonArray pools(String table) {
        Path path = data.resolve("loot_table/chests/" + table + ".json");
        assertTrue(Files.isRegularFile(path), "missing " + path);
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("pools");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every weighted entry in one pool of a table, with its count range flattened out. */
    private static List<Entry> entries(String table, int poolIndex) {
        List<Entry> out = new ArrayList<>();
        JsonObject pool = pools(table).get(poolIndex).getAsJsonObject();
        for (JsonElement element : pool.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            int min = 1;
            int max = 1;
            if (entry.has("functions")) {
                for (JsonElement fn : entry.getAsJsonArray("functions")) {
                    JsonObject function = fn.getAsJsonObject();
                    if ("minecraft:set_count".equals(function.get("function").getAsString())) {
                        JsonObject count = function.getAsJsonObject("count");
                        min = count.get("min").getAsInt();
                        max = count.get("max").getAsInt();
                    }
                }
            }
            out.add(new Entry(entry.get("name").getAsString(),
                    entry.has("weight") ? entry.get("weight").getAsInt() : 1, min, max));
        }
        assertFalse(out.isEmpty(), table + " pool " + poolIndex + " is empty");
        return out;
    }
}
