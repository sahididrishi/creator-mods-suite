package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.events.BloodMoonEvent;
import dev.riftal.creator.features.events.events.LuckyRainEvent;
import dev.riftal.creator.features.events.events.MeteorEvent;
import dev.riftal.creator.features.events.events.SiegeEvent;
import dev.riftal.creator.features.events.events.VoidRiseEvent;
import dev.riftal.creator.features.events.lucky.LuckyOutcome;
import dev.riftal.creator.features.events.lucky.LuckyOutcomes;
import dev.riftal.creator.features.events.siege.SiegeWave;
import dev.riftal.creator.features.events.siege.SiegeWaves;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the half of this feature that never fails the build: a translation key with no line in the
 * lang file, a sound event with no ogg behind it, a block with no texture. Every one of those is
 * invisible until it is on camera.
 *
 * <p>The files are read off disk rather than off the classpath so the test fails loudly when a file
 * is deleted rather than passing on a stale build output. When the tree cannot be located at all
 * (an IDE run from an unexpected working directory) the test is skipped rather than failed.
 */
class EventAssetCoverageTest {

    private static final String NAMESPACE = "creator_events";

    /** Every sound event id {@code EventsFeature#registerContent()} declares, path only. */
    private static final List<String> SOUND_PATHS = List.of(
            "bloodmoon.drone", "meteor.whistle", "meteor.impact",
            "siege.horn", "lucky.pop", "void.hum");

    private static final List<WorldEvent> EVENTS = List.of(
            new BloodMoonEvent(), new MeteorEvent(), new SiegeEvent(),
            new LuckyRainEvent(), new VoidRiseEvent());

    @Test
    void langCoversEveryEventPhaseAndHudKey() throws IOException {
        JsonObject lang = lang();
        for (WorldEvent event : EVENTS) {
            assertKey(lang, "event." + NAMESPACE + "." + event.id());
            assertKey(lang, "hud." + NAMESPACE + "." + event.id());
            for (EventPhase phase : event.phases()) {
                assertKey(lang, "phase." + NAMESPACE + "." + event.id() + "." + phase.id());
            }
        }
    }

    @Test
    void langCoversTheRegisteredContentAndCommandFeedback() throws IOException {
        JsonObject lang = lang();
        assertKey(lang, "feature." + NAMESPACE + ".name");
        assertKey(lang, "itemGroup." + NAMESPACE);
        assertKey(lang, "block." + NAMESPACE + ".lucky_rain");
        assertKey(lang, "entity." + NAMESPACE + ".meteor");
        for (String verb : List.of("started", "stopped", "skipped", "skipped_end", "timer",
                "reloaded", "hud_on", "hud_off", "list", "unknown", "none", "idle", "idle_last",
                "status", "status_waves")) {
            assertKey(lang, "commands." + NAMESPACE + "." + verb);
        }
        for (String title : List.of("meteor", "siege", "siege.sub", "wave", "siege_repelled")) {
            assertKey(lang, "title." + NAMESPACE + "." + title);
        }
        assertKey(lang, "bossbar." + NAMESPACE + ".siege");
        assertKey(lang, "hud." + NAMESPACE + ".void_rising");
    }

    @Test
    void everySoundEventHasADefinitionASubtitleAndAFile() throws IOException {
        JsonObject lang = lang();
        JsonObject sounds = json(assets().resolve("sounds.json"));
        for (String path : SOUND_PATHS) {
            JsonElement entry = sounds.get(path);
            assertTrue(entry != null && entry.isJsonObject(),
                    "sounds.json has no entry for " + NAMESPACE + ":" + path);
            JsonObject definition = entry.getAsJsonObject();

            String subtitle = definition.get("subtitle").getAsString();
            assertEquals("subtitles." + NAMESPACE + "." + path, subtitle,
                    "subtitle key must follow the naming convention");
            assertKey(lang, subtitle);

            List<String> files = new ArrayList<>();
            definition.getAsJsonArray("sounds").forEach(element -> files.add(element.getAsString()));
            assertEquals(1, files.size(), "one placeholder file per sound event");
            String file = files.get(0);
            assertTrue(file.startsWith(NAMESPACE + ":"),
                    "sound files must be namespaced, was " + file);
            Path ogg = assets().resolve("sounds")
                    .resolve(file.substring(NAMESPACE.length() + 1) + ".ogg");
            assertTrue(Files.isRegularFile(ogg), "missing sound file " + ogg);
            assertTrue(Files.size(ogg) > 1024L, "sound file looks empty: " + ogg);
        }
        assertEquals(SOUND_PATHS.size(), sounds.size(),
                "sounds.json must not define events the feature never registers");
    }

    @Test
    void theLuckyRainBlockHasItsWholeFileSet() throws IOException {
        assertFile(assets().resolve("blockstates/lucky_rain.json"));
        assertFile(assets().resolve("models/block/lucky_rain.json"));
        assertFile(assets().resolve("models/item/lucky_rain.json"));
        assertFile(assets().resolve("textures/block/lucky_rain.png"));
        assertFile(data().resolve("loot_table/blocks/lucky_rain.json"));

        // The model must point at the texture that actually exists, in our own namespace.
        JsonObject model = json(assets().resolve("models/block/lucky_rain.json"));
        assertEquals(NAMESPACE + ":block/lucky_rain",
                model.getAsJsonObject("textures").get("all").getAsString());
        JsonObject blockstate = json(assets().resolve("blockstates/lucky_rain.json"));
        assertEquals(NAMESPACE + ":block/lucky_rain",
                blockstate.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void theLootTablesTheCodeNamesExist() throws IOException {
        for (String table : List.of("meteor", "siege_reward")) {
            Path path = data().resolve("loot_table/" + table + ".json");
            assertFile(path);
            assertEquals("minecraft:chest", json(path).get("type").getAsString(),
                    table + " is opened as a chest table");
        }
    }

    @Test
    void theShippedDataPackFilesParseWithTheSameParsersTheGameUses() throws IOException {
        String outcomesJson = Files.readString(data().resolve("lucky_outcomes/default.json"),
                StandardCharsets.UTF_8);
        List<LuckyOutcome> outcomes = LuckyOutcomes.parse(outcomesJson);
        assertEquals(7, outcomes.size(), "every shipped outcome must survive the parser");
        assertTrue(outcomes != LuckyOutcomes.builtIn(),
                "the shipped file must parse, not silently fall back to the built-in table");
        for (LuckyOutcome outcome : outcomes) {
            assertTrue(outcome.isKnownType(), "shipped outcome '" + outcome.type() + "' is runnable");
        }

        String wavesJson = Files.readString(data().resolve("siege_waves/default.json"),
                StandardCharsets.UTF_8);
        List<SiegeWave> waves = SiegeWaves.parse(wavesJson);
        assertEquals(5, waves.size(), "the plan ships five waves");
        assertEquals(SiegeWaves.defaults(), waves,
                "the data file and the in-code fallback must not drift apart");
        for (SiegeWave wave : waves) {
            assertTrue(wave.total(SiegeWaves.NORMAL_FACTOR) > 0);
        }
    }

    @Test
    void theGameTestTemplateIsWhereBothLoadersLookForIt() {
        assertFile(data().resolve("structure/empty.nbt"));
    }

    private static void assertKey(JsonObject lang, String key) {
        assertTrue(lang.has(key), "lang/en_us.json is missing the key '" + key + "'");
        assertTrue(!lang.get(key).getAsString().isBlank(), "lang key '" + key + "' is empty");
    }

    private static void assertFile(Path path) {
        assertTrue(Files.isRegularFile(path), "missing resource " + path);
    }

    private static JsonObject lang() throws IOException {
        return json(assets().resolve("lang/en_us.json"));
    }

    private static JsonObject json(Path path) throws IOException {
        assertFile(path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Path assets() {
        return resources().resolve("assets").resolve(NAMESPACE);
    }

    private static Path data() {
        return resources().resolve("data").resolve(NAMESPACE);
    }

    /**
     * {@code common/src/main/resources}, found by walking up from the working directory so the test
     * behaves the same under Gradle (working directory {@code common/}) and under an IDE runner
     * (working directory the repo root).
     */
    private static Path resources() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            Path direct = candidate.resolve("src/main/resources");
            if (Files.isDirectory(direct.resolve("assets").resolve(NAMESPACE))) {
                return direct;
            }
            Path nested = candidate.resolve("common/src/main/resources");
            if (Files.isDirectory(nested.resolve("assets").resolve(NAMESPACE))) {
                return nested;
            }
        }
        Assumptions.abort("could not locate common/src/main/resources from " + here);
        throw new IllegalStateException("unreachable");
    }
}
