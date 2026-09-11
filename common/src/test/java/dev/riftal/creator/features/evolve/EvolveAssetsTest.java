package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftal.creator.features.evolve.perk.StagePerk;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.Stages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The resource half of the feature: everything the code names at runtime has to exist on the
 * classpath, and every translatable string it emits has to have a line in the lang file.
 *
 * <p>A missing texture, a sound file that {@code sounds.json} promises and does not ship, or a raw
 * {@code stage.creator_evolve.brute} showing up in the title card are all invisible to the compiler
 * and to the GameTests, and all three are visible on camera. They are cheap to catch here.
 *
 * <p>Resources are read off the test runtime classpath, which is where Gradle puts
 * {@code common/src/main/resources}.
 */
class EvolveAssetsTest {

    private static final String ASSETS = "/assets/" + EvolveFeature.NAMESPACE + "/";

    /** The sound event paths {@code EvolveFeature#registerContent()} registers. */
    private static final List<String> SOUND_EVENTS =
            List.of("evolve.roar_small", "evolve.roar_apex", "evolve.complete");

    /** Every non-stage, non-perk key the feature emits. Stage and perk keys are derived. */
    private static final List<String> FIXED_LANG_KEYS = List.of(
            "feature.creator_evolve.name",
            "entity.creator_evolve.apex_beast",
            "title.creator_evolve.stage",
            "hud.creator_evolve.transforming",
            "commands.creator_evolve.set.single",
            "commands.creator_evolve.set.many",
            "commands.creator_evolve.xp.single",
            "commands.creator_evolve.xp.many",
            "commands.creator_evolve.reset",
            "commands.creator_evolve.info",
            "commands.creator_evolve.roar",
            "commands.creator_evolve.fx.start",
            "commands.creator_evolve.fx.stop",
            "commands.creator_evolve.model",
            "commands.creator_evolve.model.on",
            "commands.creator_evolve.model.off",
            "commands.creator_evolve.model.auto",
            "commands.creator_evolve.disabled");

    private static URL resource(String path) {
        return EvolveAssetsTest.class.getResource(path);
    }

    private static String read(String path) {
        try (InputStream in = EvolveAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject json(String path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static JsonObject lang() {
        return json(ASSETS + "lang/en_us.json");
    }

    @Test
    void theBeastTextureShipsAndIsAPng() {
        String path = ASSETS + "textures/entity/apex_beast.png";
        assertNotNull(resource(path), "the renderer asks for " + path + " and it is not in the jar");

        try (InputStream in = EvolveAssetsTest.class.getResourceAsStream(path)) {
            byte[] header = in.readNBytes(8);
            assertEquals((byte) 0x89, header[0], "not a PNG signature");
            assertEquals('P', header[1]);
            assertEquals('N', header[2]);
            assertEquals('G', header[3]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void soundsJsonDescribesEveryRegisteredSoundEvent() {
        JsonObject sounds = json(ASSETS + "sounds.json");
        for (String event : SOUND_EVENTS) {
            assertTrue(sounds.has(event),
                    "sounds.json has no entry for the registered sound event " + event);
        }
        assertEquals(SOUND_EVENTS.size(), sounds.size(),
                "sounds.json describes sound events nothing registers");
    }

    @Test
    void everySoundFileSoundsJsonPromisesActuallyExists() {
        JsonObject sounds = json(ASSETS + "sounds.json");
        JsonObject lang = lang();

        for (String event : SOUND_EVENTS) {
            JsonObject entry = sounds.getAsJsonObject(event);
            assertTrue(entry.has("sounds"), event + " lists no sound files");

            List<String> files = new ArrayList<>();
            entry.getAsJsonArray("sounds").forEach(element -> files.add(element.getAsString()));
            assertTrue(!files.isEmpty(), event + " lists no sound files");

            for (String file : files) {
                String[] split = file.split(":", 2);
                assertEquals(2, split.length, file + " should be a full namespace:path");
                assertEquals(EvolveFeature.NAMESPACE, split[0], file + " is in the wrong namespace");
                String ogg = "/assets/" + split[0] + "/sounds/" + split[1] + ".ogg";
                assertNotNull(resource(ogg), "sounds.json promises " + ogg + " and it is missing");
            }

            String subtitle = entry.get("subtitle").getAsString();
            assertTrue(lang.has(subtitle), "no lang line for subtitle " + subtitle);
        }
    }

    @Test
    void everyStageAndPerkNameIsTranslated() {
        JsonObject lang = lang();
        for (EvolutionStage stage : Stages.ALL) {
            assertTrue(lang.has(stage.nameKey()), "no lang line for " + stage.nameKey());
            assertTrue(lang.has(stage.perkNameKey()), "no lang line for " + stage.perkNameKey());
        }
        for (StagePerk perk : StagePerks.all()) {
            assertTrue(lang.has("perk.creator_evolve." + perk.key()),
                    "no lang line for perk " + perk.key());
        }
    }

    @Test
    void everyStringTheFeatureEmitsIsTranslated() {
        JsonObject lang = lang();
        for (String key : FIXED_LANG_KEYS) {
            assertTrue(lang.has(key), "no lang line for " + key);
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is translated to nothing");
        }
    }

    @Test
    void theLangFileHasNoKeysFromAnotherFeature() {
        JsonObject lang = lang();
        for (String key : lang.keySet()) {
            assertTrue(key.contains("creator_evolve"),
                    key + " does not belong to this feature's lang file");
        }
    }

    @Test
    void theGameTestTemplateShips() {
        assertNotNull(resource("/data/" + EvolveFeature.NAMESPACE + "/structure/empty.nbt"),
                "the GameTests all load creator_evolve:empty");
    }
}
