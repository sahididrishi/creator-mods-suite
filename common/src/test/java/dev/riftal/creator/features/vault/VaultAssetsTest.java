package dev.riftal.creator.features.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftal.creator.features.vault.block.AltarState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the client-visible half of the feature, where every mistake is silent at build time and
 * loud on camera: a blockstate variant pointing at a model nobody wrote is a purple-and-black cube,
 * a model naming a texture nobody wrote is the missing-texture checkerboard, a {@code sounds.json}
 * entry with no {@code .ogg} behind it is silence, and a {@code Component.translatable} key with no
 * lang line renders as {@code item.creator_vault.vault_key} in the hotbar.
 *
 * <p>Walks the real resource tree and the real Java sources off disk. If neither can be located
 * (an IDE run from an unexpected working directory) the test is skipped rather than failed.
 */
class VaultAssetsTest {

    private static final String NS = "creator_vault";
    private static final String PREFIX = NS + ":";

    /** Every translation-key namespace this feature is allowed to emit. */
    private static final Pattern TRANSLATION_KEY = Pattern.compile(
            "\"((?:block|item|entity|itemGroup|feature|subtitles|hud|commands)\\.creator_vault[A-Za-z0-9_.]*)\"");

    private static Path assets;
    private static Path sources;
    private static Map<String, String> lang;

    @BeforeAll
    static void locateTrees() {
        assets = firstExisting(
                "src/main/resources/assets/" + NS,
                "common/src/main/resources/assets/" + NS,
                "../common/src/main/resources/assets/" + NS);
        sources = firstExisting(
                "src/main/java/dev/riftal/creator/features/vault",
                "common/src/main/java/dev/riftal/creator/features/vault",
                "../common/src/main/java/dev/riftal/creator/features/vault");
        if (assets == null || sources == null) {
            Assumptions.abort("creator_vault trees not found from " + Path.of(".").toAbsolutePath());
        }
        lang = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry
                : json(assets.resolve("lang/en_us.json")).entrySet()) {
            lang.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    // ------------------------------------------------------------------ lang

    @Test
    void everyTranslationKeyTheCodeEmitsHasALangLine() {
        Set<String> missing = new TreeSet<>();
        for (Path source : javaSources()) {
            String text = read(source);
            Matcher matcher = TRANSLATION_KEY.matcher(text);
            while (matcher.find()) {
                for (String key : expand(matcher.group(1))) {
                    if (!lang.containsKey(key)) {
                        missing.add(key + "  (" + source.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "translation keys with no en_us.json line: " + missing);
    }

    /**
     * The code builds two families of key by concatenation
     * ({@code "hud.creator_vault.state." + state.getSerializedName()}), so a literal that ends in a
     * dot stands for one key per {@link AltarState}.
     */
    private static List<String> expand(String literal) {
        if (!literal.endsWith(".")) {
            return List.of(literal);
        }
        List<String> keys = new ArrayList<>();
        for (AltarState state : AltarState.values()) {
            keys.add(literal + state.getSerializedName());
        }
        return keys;
    }

    @Test
    void everyRegisteredIdIsNamedInTheLangFile() {
        assertEquals("Cursed Vault", lang.get("itemGroup." + NS));
        for (String block : List.of("cursed_altar", "sealed_chest")) {
            assertTrue(lang.containsKey("block." + NS + "." + block), "block." + NS + "." + block);
        }
        for (String item : List.of("vault_key", "cursed_altar", "sealed_chest",
                "vault_keeper_spawn_egg")) {
            assertTrue(lang.containsKey("item." + NS + "." + item), "item." + NS + "." + item);
        }
        assertTrue(lang.containsKey("entity." + NS + ".vault_keeper"));
        assertTrue(lang.containsKey("feature." + NS + ".name"));
    }

    @Test
    void noLangValueIsBlankOrStillTheKey() {
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            assertFalse(entry.getValue().isBlank(), "blank translation for " + entry.getKey());
            assertFalse(entry.getValue().equals(entry.getKey()),
                    "placeholder translation for " + entry.getKey());
        }
    }

    // ---------------------------------------------------------------- models

    @Test
    void everyBlockstateVariantPointsAtAModelWeShip() {
        Set<String> models = new LinkedHashSet<>();
        for (Path blockstate : filesIn("blockstates")) {
            JsonObject variants = json(blockstate).getAsJsonObject("variants");
            assertTrue(variants != null && variants.size() > 0, "no variants in " + blockstate);
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                models.add(variant.getValue().getAsJsonObject().get("model").getAsString());
            }
        }
        assertFalse(models.isEmpty(), "no blockstates found at all");
        for (String model : models) {
            assertModelExists(model);
        }
    }

    @Test
    void cursedAltarHasOneVariantPerState() {
        JsonObject variants = json(assets.resolve("blockstates/cursed_altar.json"))
                .getAsJsonObject("variants");
        assertEquals(AltarState.values().length, variants.size(),
                "a missing variant renders as the purple-and-black cube");
        for (AltarState state : AltarState.values()) {
            assertTrue(variants.has("state=" + state.getSerializedName()),
                    "no blockstate variant for " + state);
        }
    }

    @Test
    void everyModelTextureAndParentResolves() {
        List<Path> models = new ArrayList<>();
        models.addAll(filesIn("models/block"));
        models.addAll(filesIn("models/item"));
        assertFalse(models.isEmpty(), "no models found at all");

        for (Path model : models) {
            JsonObject root = json(model);
            if (root.has("parent")) {
                assertModelExists(root.get("parent").getAsString());
            }
            if (!root.has("textures")) {
                continue;
            }
            for (Map.Entry<String, JsonElement> texture : root.getAsJsonObject("textures").entrySet()) {
                String value = texture.getValue().getAsString();
                if (value.startsWith("#")) {
                    // A slot reference; the concrete texture comes from a child model.
                    continue;
                }
                assertTextureExists(value, model);
            }
        }
    }

    @Test
    void noModelLeavesATextureSlotUnfilled() {
        // cursed_altar_template declares #base and #crystal; every model that uses it as a parent
        // must fill both or the altar renders untextured.
        Set<String> slots = Set.of("base", "crystal");
        for (Path model : filesIn("models/block")) {
            JsonObject root = json(model);
            if (!root.has("parent")
                    || !root.get("parent").getAsString().endsWith("block/cursed_altar_template")) {
                continue;
            }
            JsonObject textures = root.getAsJsonObject("textures");
            for (String slot : slots) {
                assertTrue(textures != null && textures.has(slot),
                        model.getFileName() + " does not fill the #" + slot + " slot");
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    @Test
    void everySoundEventHasAnOggAndASubtitle() {
        JsonObject sounds = json(assets.resolve("sounds.json"));
        assertEquals(8, sounds.size(), "sounds.json should describe all eight events");
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            String subtitle = definition.get("subtitle").getAsString();
            assertEquals("subtitles." + NS + "." + entry.getKey(), subtitle,
                    "subtitle key should mirror the sound event id");
            assertTrue(lang.containsKey(subtitle), "no lang line for " + subtitle);

            for (JsonElement sound : definition.getAsJsonArray("sounds")) {
                String id = sound.getAsString();
                assertTrue(id.startsWith(PREFIX), "foreign sound file referenced: " + id);
                Path ogg = assets.resolve("sounds")
                        .resolve(id.substring(PREFIX.length()) + ".ogg");
                assertTrue(Files.isRegularFile(ogg), "sounds.json names a missing file: " + ogg);
                assertTrue(size(ogg) > 512, "suspiciously small sound file: " + ogg);
            }
        }
    }

    @Test
    void everyShippedOggIsReferencedBySoundsJson() {
        Set<String> referenced = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : json(assets.resolve("sounds.json")).entrySet()) {
            for (JsonElement sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                referenced.add(sound.getAsString().substring(PREFIX.length()) + ".ogg");
            }
        }
        Path root = assets.resolve("sounds");
        for (Path ogg : walk(root, ".ogg")) {
            String relative = root.relativize(ogg).toString().replace('\\', '/');
            assertTrue(referenced.contains(relative),
                    "orphan sound file, nothing can ever play it: " + relative);
        }
    }

    // -------------------------------------------------------------- textures

    @Test
    void everyTextureIsAPowerOfTwoPng() {
        List<Path> textures = walk(assets.resolve("textures"), ".png");
        assertFalse(textures.isEmpty(), "no textures found at all");
        for (Path texture : textures) {
            int[] size = pngSize(texture);
            assertTrue(isPowerOfTwo(size[0]) && isPowerOfTwo(size[1]),
                    texture.getFileName() + " is " + size[0] + "x" + size[1]
                            + ", which the block atlas cannot stitch");
        }
    }

    @Test
    void theKeeperTextureMatchesTheHumanoidSkinLayout() {
        // VaultKeeperModel bakes HumanoidModel.createMesh into a 64x64 layer; anything else and the
        // UVs land on the wrong pixels.
        int[] size = pngSize(assets.resolve("textures/entity/vault_keeper.png"));
        assertEquals(64, size[0], "entity sheet width");
        assertEquals(64, size[1], "entity sheet height");
    }

    // ----------------------------------------------------------------- utils

    private static void assertModelExists(String id) {
        if (!id.startsWith(PREFIX)) {
            assertTrue(id.startsWith("minecraft:"), "foreign model referenced: " + id);
            return;
        }
        Path path = assets.resolve("models").resolve(id.substring(PREFIX.length()) + ".json");
        assertTrue(Files.isRegularFile(path), "missing model " + path);
    }

    private static void assertTextureExists(String id, Path from) {
        if (!id.startsWith(PREFIX)) {
            assertTrue(id.startsWith("minecraft:"), "foreign texture referenced: " + id);
            return;
        }
        Path path = assets.resolve("textures").resolve(id.substring(PREFIX.length()) + ".png");
        assertTrue(Files.isRegularFile(path),
                from.getFileName() + " names a texture that does not exist: " + path);
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    /** Width and height straight out of the PNG IHDR chunk - no image library needed. */
    private static int[] pngSize(Path path) {
        assertTrue(Files.isRegularFile(path), "missing texture " + path);
        byte[] header = new byte[24];
        try (var in = Files.newInputStream(path)) {
            assertEquals(24, in.readNBytes(header, 0, 24), "truncated PNG " + path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals((byte) 0x89, header[0], "not a PNG: " + path);
        assertEquals((byte) 'P', header[1], "not a PNG: " + path);
        return new int[]{intAt(header, 16), intAt(header, 20)};
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static Path firstExisting(String... candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private static List<Path> filesIn(String relative) {
        return walk(assets.resolve(relative), ".json");
    }

    private static List<Path> javaSources() {
        List<Path> java = walk(sources, ".java");
        assertFalse(java.isEmpty(), "no vault sources found under " + sources);
        return java;
    }

    private static List<Path> walk(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject json(Path path) {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
