package dev.riftal.creator.features.colossus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the hand-written resources against the mistakes that fail <em>silently</em> in game.
 *
 * <p>A GeckoLib model with no {@code head} bone stops the mob tracking the player; a cube whose UV
 * runs off the sheet renders as a smear; an animation the code triggers but the JSON does not
 * define is a frozen boss; a {@code sounds.json} entry pointing at a missing {@code .ogg} is one
 * log line nobody reads. None of these fail the build - so they fail here instead.
 *
 * <p>Reads the resource tree off disk rather than through the classpath, so it does not depend on
 * how the {@code test} source set is wired. If the tree cannot be found the test is skipped.
 */
class ColossusAssetsTest {

    private static final String NS = "creator_colossus";

    private static Path assets;
    private static Path data;

    @BeforeAll
    static void locateResources() {
        assets = locate("assets");
        data = locate("data");
        Assumptions.assumeTrue(assets != null && data != null,
                "creator_colossus resource tree not found from " + Path.of(".").toAbsolutePath());
    }

    private static Path locate(String root) {
        Path[] candidates = {
                Path.of("src/main/resources/" + root + "/" + NS),
                Path.of("common/src/main/resources/" + root + "/" + NS),
                Path.of("../common/src/main/resources/" + root + "/" + NS)
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static JsonObject json(Path path) {
        assertTrue(Files.isRegularFile(path), "missing resource: " + path);
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ geometry

    private static JsonObject geometry(String entity) {
        JsonObject root = json(assets.resolve("geo/entity/" + entity + ".geo.json"));
        assertTrue(root.has("format_version"), entity + ".geo.json needs a format_version");
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        assertNotNull(geometries, entity + ".geo.json needs a minecraft:geometry array");
        assertEquals(1, geometries.size(), entity + " should define exactly one geometry");
        return geometries.get(0).getAsJsonObject();
    }

    private static Set<String> boneNames(JsonObject geometry) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonElement bone : geometry.getAsJsonArray("bones")) {
            names.add(bone.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static void assertGeometryIsSane(String entity, int expectedTextureSize) {
        JsonObject geometry = geometry(entity);
        JsonObject description = geometry.getAsJsonObject("description");
        assertEquals("geometry." + entity, description.get("identifier").getAsString());

        int texW = description.get("texture_width").getAsInt();
        int texH = description.get("texture_height").getAsInt();
        assertEquals(expectedTextureSize, texW, entity + " texture_width");
        assertEquals(expectedTextureSize, texH, entity + " texture_height");

        Set<String> bones = boneNames(geometry);
        assertTrue(bones.contains("head"),
                entity + " needs a bone literally named 'head' - DefaultedEntityGeoModel(.., true) "
                        + "looks it up by that name to make the mob track the player");
        assertTrue(bones.contains("root"), entity + " needs a root bone");
        // boneNames() de-duplicates, so compare against the raw count: two bones with the same
        // name is a silent "half my animation does nothing".
        assertEquals(geometry.getAsJsonArray("bones").size(), bones.size(),
                entity + " has duplicate bone names");

        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            assertTrue(bone.has("pivot"), name + " has no pivot");
            assertEquals(3, bone.getAsJsonArray("pivot").size(), name + " pivot must be [x, y, z]");
            if (bone.has("parent")) {
                assertTrue(bones.contains(bone.get("parent").getAsString()),
                        name + " is parented to a bone that does not exist: " + bone.get("parent"));
            }
            if (!bone.has("cubes")) {
                continue;
            }
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeElement.getAsJsonObject();
                JsonArray size = cube.getAsJsonArray("size");
                JsonArray uv = cube.getAsJsonArray("uv");
                assertEquals(3, size.size(), name + " cube size must be [w, h, d]");
                assertEquals(3, cube.getAsJsonArray("origin").size(), name + " cube origin");
                assertEquals(2, uv.size(), name + " cube uv must be [u, v]");

                int w = size.get(0).getAsInt();
                int h = size.get(1).getAsInt();
                int d = size.get(2).getAsInt();
                int u = uv.get(0).getAsInt();
                int v = uv.get(1).getAsInt();
                // Box unwrap footprint: 2 * (depth + width) across, depth + height down.
                assertTrue(u + 2 * (d + w) <= texW,
                        name + " cube UV runs off the right edge: " + (u + 2 * (d + w)) + " > " + texW);
                assertTrue(v + d + h <= texH,
                        name + " cube UV runs off the bottom edge: " + (v + d + h) + " > " + texH);
            }
        }
    }

    @Test
    void theColossusModelIsAValidGeckoLibModel() {
        assertGeometryIsSane("ashen_colossus", 128);
        Set<String> bones = boneNames(geometry("ashen_colossus"));
        assertTrue(bones.containsAll(List.of("body", "head", "arm_left", "arm_right",
                        "leg_left", "leg_right", "hand_left", "hand_right")),
                "the animations drive these bones by name, got " + bones);
    }

    @Test
    void theMinionModelIsAValidGeckoLibModel() {
        assertGeometryIsSane("ashen_minion", 64);
    }

    @Test
    void noTwoCubesShareTheSameCornerOfTheSheet() {
        Set<String> corners = new HashSet<>();
        for (JsonElement element : geometry("ashen_colossus").getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes")) {
                continue;
            }
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonArray uv = cubeElement.getAsJsonObject().getAsJsonArray("uv");
                String key = uv.get(0).getAsInt() + "," + uv.get(1).getAsInt();
                assertTrue(corners.add(key),
                        "two cubes start at UV " + key + " - they would share a texture island");
            }
        }
    }

    // ------------------------------------------------------------------ animations

    private static JsonObject animations(String entity) {
        return json(assets.resolve("animations/entity/" + entity + ".animation.json"))
                .getAsJsonObject("animations");
    }

    @Test
    void everyAnimationTheColossusTriggersExists() {
        JsonObject clips = animations("ashen_colossus");
        Set<String> required = new TreeSet<>(List.of("animation.colossus.idle", "animation.colossus.walk"));
        for (AttackKind kind : AttackKind.values()) {
            if (kind != AttackKind.NONE) {
                required.add("animation.colossus." + kind.animName());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            if (!clips.has(name)) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(), "animation.json is missing " + missing);
    }

    @Test
    void everyAnimationTheMinionTriggersExists() {
        JsonObject clips = animations("ashen_minion");
        for (String name : List.of("animation.minion.idle", "animation.minion.walk",
                "animation.minion.attack")) {
            assertTrue(clips.has(name), "animation.json is missing " + name);
        }
    }

    @Test
    void onlyIdleAndWalkLoopAndOnlyDeathHolds() {
        JsonObject clips = animations("ashen_colossus");
        for (String name : clips.keySet()) {
            JsonElement loop = clips.getAsJsonObject(name).get("loop");
            boolean isIdleOrWalk = name.endsWith(".idle") || name.endsWith(".walk");
            boolean isDeath = name.endsWith(".death");
            if (isIdleOrWalk) {
                assertTrue(loop != null && loop.isJsonPrimitive() && loop.getAsBoolean(),
                        name + " must loop");
            } else if (isDeath) {
                assertEquals("hold_on_last_frame", loop.getAsString(),
                        "the corpse must stay on the floor");
            } else {
                // hold_on_last_frame on a replayable clip is the classic "it only plays once" bug,
                // and a looping attack clip never ends.
                assertTrue(loop == null || (loop.isJsonPrimitive()
                                && loop.getAsJsonPrimitive().isBoolean() && !loop.getAsBoolean()),
                        name + " must be play-once: neither a loop nor a hold, got " + loop);
            }
        }
    }

    @Test
    void clipLengthsCoverTheServerTimeline() {
        JsonObject clips = animations("ashen_colossus");
        for (AttackKind kind : AttackKind.values()) {
            if (kind == AttackKind.NONE) {
                continue;
            }
            JsonObject clip = clips.getAsJsonObject("animation.colossus." + kind.animName());
            double seconds = clip.get("animation_length").getAsDouble();
            double serverSeconds = kind.durationTicks() / 20.0D;
            assertEquals(serverSeconds, seconds, 1.0E-6D,
                    kind + ": the clip and AttackKind.durationTicks() must agree, or damage lands "
                            + "off the animation");
        }
    }

    @Test
    void everyAnimatedBoneExistsInTheModel() {
        for (String entity : List.of("ashen_colossus", "ashen_minion")) {
            Set<String> bones = boneNames(geometry(entity));
            JsonObject clips = animations(entity);
            for (String clipName : clips.keySet()) {
                JsonObject clip = clips.getAsJsonObject(clipName);
                if (!clip.has("bones")) {
                    continue;
                }
                for (String bone : clip.getAsJsonObject("bones").keySet()) {
                    assertTrue(bones.contains(bone),
                            clipName + " animates '" + bone + "', which " + entity + " does not have");
                }
            }
        }
    }

    @Test
    void everyKeyframeSitsInsideItsOwnClip() {
        for (String entity : List.of("ashen_colossus", "ashen_minion")) {
            JsonObject clips = animations(entity);
            for (String clipName : clips.keySet()) {
                JsonObject clip = clips.getAsJsonObject(clipName);
                double length = clip.get("animation_length").getAsDouble();
                if (!clip.has("bones")) {
                    continue;
                }
                JsonObject bones = clip.getAsJsonObject("bones");
                for (String bone : bones.keySet()) {
                    JsonObject channels = bones.getAsJsonObject(bone);
                    for (String channel : channels.keySet()) {
                        for (String time : channels.getAsJsonObject(channel).keySet()) {
                            double t = Double.parseDouble(time);
                            assertTrue(t >= 0.0D && t <= length + 1.0E-6D,
                                    clipName + "/" + bone + "/" + channel + " has a keyframe at "
                                            + t + "s, outside its " + length + "s clip");
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sounds

    @Test
    void everySoundEventHasAFileAndASubtitle() {
        JsonObject sounds = json(assets.resolve("sounds.json"));
        JsonObject lang = json(assets.resolve("lang/en_us.json"));

        Set<String> expected = new TreeSet<>(List.of(
                "colossus.roar", "colossus.swing", "colossus.slam", "colossus.step",
                "colossus.hurt", "colossus.death", "minion.hurt", "minion.death"));
        assertEquals(expected, new TreeSet<>(sounds.keySet()),
                "sounds.json keys must match the SoundEvents registered in ColossusFeature");

        for (String key : sounds.keySet()) {
            JsonObject entry = sounds.getAsJsonObject(key);
            String subtitle = entry.get("subtitle").getAsString();
            assertEquals("subtitles." + NS + "." + key, subtitle, key + " subtitle key");
            assertTrue(lang.has(subtitle), "lang/en_us.json has no line for " + subtitle);

            JsonArray files = entry.getAsJsonArray("sounds");
            assertTrue(files.size() > 0, key + " names no sound file");
            for (JsonElement file : files) {
                String id = file.getAsString();
                assertTrue(id.startsWith(NS + ":"), id + " must live in this feature's namespace");
                Path ogg = assets.resolve("sounds/" + id.substring(NS.length() + 1) + ".ogg");
                assertTrue(Files.isRegularFile(ogg), "sounds.json points at a missing file: " + ogg);
                assertTrue(sizeOf(ogg) > 1024L, ogg + " looks empty");
            }
        }
    }

    @Test
    void noOrphanedOggFiles() {
        JsonObject sounds = json(assets.resolve("sounds.json"));
        Set<String> declared = new HashSet<>();
        for (String key : sounds.keySet()) {
            for (JsonElement file : sounds.getAsJsonObject(key).getAsJsonArray("sounds")) {
                declared.add(file.getAsString().substring(NS.length() + 1) + ".ogg");
            }
        }
        Path root = assets.resolve("sounds");
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".ogg"))
                    .forEach(p -> assertTrue(
                            declared.contains(root.relativize(p).toString().replace('\\', '/')),
                            p + " ships in the jar but no sounds.json entry names it"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ textures

    @Test
    void everyTextureTheRenderersAskForExists() {
        record Texture(String name, int size) {
        }
        List<Texture> expected = List.of(
                new Texture("ashen_colossus", 128),
                new Texture("ashen_colossus_glowmask", 128),
                new Texture("ashen_colossus_enraged", 128),
                new Texture("ashen_colossus_enraged_glowmask", 128),
                new Texture("ashen_minion", 64),
                new Texture("ashen_minion_glowmask", 64));

        for (Texture texture : expected) {
            Path png = assets.resolve("textures/entity/" + texture.name() + ".png");
            assertTrue(Files.isRegularFile(png), "missing texture " + png);
            int[] dimensions = pngSize(png);
            assertEquals(texture.size(), dimensions[0], png + " width");
            assertEquals(texture.size(), dimensions[1], png + " height");
        }
    }

    @Test
    void theTextureSheetMatchesTheModelUvSize() {
        JsonObject description = geometry("ashen_colossus").getAsJsonObject("description");
        int[] png = pngSize(assets.resolve("textures/entity/ashen_colossus.png"));
        assertEquals(description.get("texture_width").getAsInt(), png[0]);
        assertEquals(description.get("texture_height").getAsInt(), png[1]);
    }

    // ------------------------------------------------------------------ lang and data

    @Test
    void theLangFileCoversEveryNameTheGameWillShow() {
        JsonObject lang = json(assets.resolve("lang/en_us.json"));
        List<String> required = List.of(
                "entity." + NS + ".ashen_colossus",
                "entity." + NS + ".ashen_minion",
                "entity." + NS + ".ash_bomb",
                "item." + NS + ".ashen_colossus_spawn_egg",
                "item." + NS + ".ashen_minion_spawn_egg",
                "itemGroup." + NS,
                "hud." + NS + ".ring",
                "hud." + NS + ".ring_warning",
                "commands." + NS + ".spawn",
                "commands." + NS + ".phase",
                "commands." + NS + ".hp",
                "commands." + NS + ".stagger",
                "commands." + NS + ".kill",
                "commands." + NS + ".none",
                "commands." + NS + ".arena.set",
                "commands." + NS + ".arena.cleared",
                "commands." + NS + ".arena.unknown",
                "commands." + NS + ".status.health",
                "commands." + NS + ".status.attack",
                "commands." + NS + ".status.arena");
        for (String key : required) {
            assertTrue(lang.has(key), "lang/en_us.json is missing " + key);
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }

    @Test
    void bothSpawnEggsHaveAnItemModel() {
        for (String egg : List.of("ashen_colossus_spawn_egg", "ashen_minion_spawn_egg")) {
            JsonObject model = json(assets.resolve("models/item/" + egg + ".json"));
            assertEquals("minecraft:item/template_spawn_egg", model.get("parent").getAsString());
        }
    }

    @Test
    void lootTablesUseTheSingularNineteenTwentyOneFolder() {
        for (String entity : List.of("ashen_colossus", "ashen_minion")) {
            Path table = data.resolve("loot_table/entities/" + entity + ".json");
            assertTrue(Files.isRegularFile(table), "missing loot table " + table);
            JsonObject root = json(table);
            assertEquals("minecraft:entity", root.get("type").getAsString());
            assertTrue(root.getAsJsonArray("pools").size() > 0, entity + " drops nothing");
        }
        assertTrue(Files.notExists(data.resolve("loot_tables")),
                "1.21 renamed loot_tables to loot_table; the plural folder is never read");
    }

    @Test
    void theGameTestArenaShipsAlongsideTheEmptyTemplate() {
        assertTrue(Files.isRegularFile(data.resolve("structure/empty.nbt")));
        assertTrue(Files.isRegularFile(data.resolve("structure/arena_24.nbt")),
                "the boss GameTests need a floor bigger than the 9x9 empty template");
        assertTrue(Files.notExists(data.resolve("structures")),
                "1.21 renamed structures to structure");
    }

    // ------------------------------------------------------------------ helpers

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads width and height straight out of the PNG IHDR chunk. */
    private static int[] pngSize(Path path) {
        byte[] header;
        try {
            header = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(header.length > 24, path + " is not a PNG");
        assertEquals((byte) 0x89, header[0], path + " is not a PNG");
        int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
        int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
        return new int[]{width, height};
    }
}
