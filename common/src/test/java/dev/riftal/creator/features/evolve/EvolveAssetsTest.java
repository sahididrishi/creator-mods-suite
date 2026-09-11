package dev.riftal.creator.features.evolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * {@code ApexBeast.MODEL_HEIGHT}, repeated rather than imported: {@code ApexBeast} implements
     * GeckoLib's {@code GeoEntity}, and GeckoLib is {@code compileOnly} on {@code :common}, so it is
     * not on the JUnit compile classpath. {@code EvolveGameTests.apexBeastHitboxMatchesTheMesh}
     * asserts the constant itself; this file asserts the shipped geometry agrees with it.
     */
    private static final float MODEL_HEIGHT = 4.68F;

    /** The animation clips {@code ApexBeast} names in its controllers, per plan section 6. */
    private static final List<String> ANIMATIONS = List.of(
            "animation.apex.idle", "animation.apex.walk",
            "animation.apex.roar", "animation.apex.attack");

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
            "commands.creator_evolve.fx.busy",
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

    /** The plan's section 6 asks for a GeckoLib beast, not a Java cube mob. Both files have to ship. */
    @Test
    void theGeckoLibModelAndAnimationsShip() {
        JsonObject geo = json(ASSETS + "geo/entity/apex_beast.geo.json");
        assertTrue(geo.has("minecraft:geometry"), "not a Bedrock geometry file");
        JsonObject geometry = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry.apex_beast", description.get("identifier").getAsString());
        assertEquals(128, description.get("texture_width").getAsInt(),
                "the placeholder texture is 128x128 and the UVs are laid out for it");
        assertEquals(128, description.get("texture_height").getAsInt());

        JsonObject animations = json(ASSETS + "animations/entity/apex_beast.animation.json")
                .getAsJsonObject("animations");
        for (String clip : ANIMATIONS) {
            assertTrue(animations.has(clip), "the plan lists " + clip + " and it is not in the file");
        }
    }

    /** Every bone an animation moves has to exist in the geometry, or the clip silently does nothing. */
    @Test
    void everyAnimatedBoneExistsInTheGeometry() {
        Set<String> bones = new HashSet<>();
        JsonArray declared = json(ASSETS + "geo/entity/apex_beast.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");
        for (JsonElement bone : declared) {
            bones.add(bone.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(bones.contains("head"),
                "ApexBeastModel asks DefaultedEntityGeoModel to turn a bone called 'head'");

        JsonObject animations = json(ASSETS + "animations/entity/apex_beast.animation.json")
                .getAsJsonObject("animations");
        for (String clip : ANIMATIONS) {
            JsonObject moved = animations.getAsJsonObject(clip).getAsJsonObject("bones");
            for (String bone : moved.keySet()) {
                assertTrue(bones.contains(bone),
                        clip + " animates a bone the geometry does not have: " + bone);
            }
        }
    }

    /**
     * The mesh has to be exactly as tall as {@code ApexBeast.MODEL_HEIGHT} ({@link #MODEL_HEIGHT}):
     * that constant is both
     * the entity type's hitbox height and the divisor {@code PlayerRenderSwap} scales by, so a mesh
     * that overshoots it puts the beast's head outside its own box and outside the player hitbox it
     * is standing in for.
     */
    @Test
    void theMeshIsExactlyAsTallAsTheHitbox() {
        JsonArray bones = json(ASSETS + "geo/entity/apex_beast.geo.json")
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones");

        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (JsonElement bone : bones) {
            JsonObject object = bone.getAsJsonObject();
            if (!object.has("cubes")) {
                continue;
            }
            for (JsonElement cube : object.getAsJsonArray("cubes")) {
                JsonObject box = cube.getAsJsonObject();
                double bottom = box.getAsJsonArray("origin").get(1).getAsDouble();
                double height = box.getAsJsonArray("size").get(1).getAsDouble();
                lowest = Math.min(lowest, bottom);
                highest = Math.max(highest, bottom + height);
            }
        }

        assertEquals(0.0D, lowest, 1.0E-6D, "the feet have to sit on y = 0");
        assertEquals(MODEL_HEIGHT, (float) (highest / 16.0D), 1.0E-4F,
                "the crown of the mesh is " + highest + " px, which is not MODEL_HEIGHT");
    }

    @Test
    void theGameTestTemplateShips() {
        assertNotNull(resource("/data/" + EvolveFeature.NAMESPACE + "/structure/empty.nbt"),
                "the GameTests all load creator_evolve:empty");
    }
}
