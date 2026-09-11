package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Guards the half of this feature that no compiler checks and every viewer sees: a
 * {@code Component.translatable} key with no lang line renders as
 * {@code commands.creator_powers.granted} in chat, an {@code Ability#icon()} with no PNG behind it
 * is the missing-texture checkerboard in the middle of the HUD row, and a {@code sounds.json} entry
 * with no {@code .ogg} is simply silence where the ready chime should be.
 *
 * <p>Walks the real resource tree and the real Java sources off disk, so it fails in
 * {@code :common:test} rather than on camera. If neither tree can be located (an IDE run from an
 * unexpected working directory) the test is skipped rather than failed.
 */
class PowersAssetsTest {

    private static final String NS = PowersFeature.NAMESPACE;
    private static final String PREFIX = NS + ":";

    /** Every translation-key family this feature is allowed to emit, except the key mappings. */
    private static final Pattern TRANSLATION_KEY = Pattern.compile(
            "\"((?:ability|commands|feature|hud|subtitles)\\.creator_powers[A-Za-z0-9_.]*)\"");

    /** {@code key.creator_powers.slot1} .. {@code slot6}, built by concatenation in PowerKeys. */
    private static final Pattern KEY_MAPPING_KEY = Pattern.compile(
            "\"(key\\.creator_powers\\.[A-Za-z0-9_]*)\"");

    private static Path assets;
    private static Path sources;
    private static Map<String, String> lang;

    @BeforeAll
    static void locateTrees() {
        AbilityRegistry.bootstrap();
        assets = firstExisting(
                "src/main/resources/assets/" + NS,
                "common/src/main/resources/assets/" + NS,
                "../common/src/main/resources/assets/" + NS);
        sources = firstExisting(
                "src/main/java/dev/riftal/creator/features/powers",
                "common/src/main/java/dev/riftal/creator/features/powers",
                "../common/src/main/java/dev/riftal/creator/features/powers");
        if (assets == null || sources == null) {
            Assumptions.abort("creator_powers trees not found from " + Path.of(".").toAbsolutePath());
        }
        lang = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json(assets.resolve("lang/en_us.json")).entrySet()) {
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
     * {@code Ability#displayName()} builds its key as {@code "ability.creator_powers." + path()},
     * so a literal that ends in a dot stands for one key per registered ability.
     */
    private static List<String> expand(String literal) {
        if (!literal.endsWith(".")) {
            return List.of(literal);
        }
        List<String> keys = new ArrayList<>();
        for (Ability ability : AbilityRegistry.ordered()) {
            keys.add(literal + ability.path());
        }
        assertFalse(keys.isEmpty(), "no abilities registered, so " + literal + " expands to nothing");
        return keys;
    }

    @Test
    void theSixKeyMappingsAndTheirCategoryAreNamed() {
        // The mappings are built as "key.creator_powers.slot" + (i + 1) in PowerKeys, so the source
        // scan only ever sees the prefix - the six real keys have to be asserted by hand.
        for (int slot = 1; slot <= 6; slot++) {
            assertTrue(lang.containsKey("key." + NS + ".slot" + slot),
                    "no lang line for ability slot " + slot + "; Options > Controls would show the raw key");
        }
        assertTrue(lang.containsKey("key.categories." + NS),
                "the controls screen would list the six keys under a raw category id");

        Set<String> prefixes = new HashSet<>();
        for (Path source : javaSources()) {
            Matcher matcher = KEY_MAPPING_KEY.matcher(read(source));
            while (matcher.find()) {
                prefixes.add(matcher.group(1));
            }
        }
        assertTrue(prefixes.contains("key." + NS + ".slot"),
                "PowerKeys no longer builds its mappings from 'key.creator_powers.slot'; this test "
                        + "and the lang file both need to follow it, found " + prefixes);
    }

    @Test
    void theFeatureNameIsTranslated() {
        // Feature#displayName() is inherited from core and emits feature.<namespace>.name, so no
        // source file in this package mentions it.
        assertEquals("Power Kit", lang.get("feature." + NS + ".name"));
    }

    @Test
    void noCommandLangLineIsDeadWeight() {
        String commands = read(sources.resolve("command/PowerCommand.java"));
        Set<String> unused = new TreeSet<>();
        for (String key : lang.keySet()) {
            if (key.startsWith("commands." + NS + ".") && !commands.contains('"' + key + '"')) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "lang lines nothing can ever print: " + unused);
    }

    @Test
    void noLangValueIsBlankOrStillTheKey() {
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            assertFalse(entry.getValue().isBlank(), "blank translation for " + entry.getKey());
            assertFalse(entry.getValue().equals(entry.getKey()),
                    "placeholder translation for " + entry.getKey());
        }
    }

    // -------------------------------------------------------------- textures

    @Test
    void everyAbilityIconExistsAndIsSixteenSquare() {
        for (Ability ability : AbilityRegistry.ordered()) {
            String id = ability.icon().toString();
            assertTrue(id.startsWith(PREFIX), "foreign icon referenced: " + id);
            int[] size = pngSize(assets.resolve(ability.icon().getPath()));
            assertEquals(16, size[0], ability.path() + " icon width");
            assertEquals(16, size[1], ability.path() + " icon height");
        }
    }

    @Test
    void theSlotFramesAreTheThirtyTwoPixelSheetsTheHudBlits() {
        // PowerHudLayer blits a 22x22 region out of a 32x32 sheet (FRAME / FRAME_SHEET). Shrinking
        // the PNG to 22x22 would not fail the build - it would stretch the frame on screen.
        for (String name : List.of("slot", "slot_ready")) {
            int[] size = pngSize(assets.resolve("textures/gui/" + name + ".png"));
            assertEquals(32, size[0], name + " sheet width");
            assertEquals(32, size[1], name + " sheet height");
        }
    }

    @Test
    void everyTextureIsAPowerOfTwoPng() {
        List<Path> textures = walk(assets.resolve("textures"), ".png");
        assertEquals(8, textures.size(), "six ability icons plus two slot frames");
        for (Path texture : textures) {
            int[] size = pngSize(texture);
            assertTrue(isPowerOfTwo(size[0]) && isPowerOfTwo(size[1]),
                    texture.getFileName() + " is " + size[0] + "x" + size[1]
                            + ", which CONTRACT.md section 9.1 does not allow");
        }
    }

    @Test
    void noTextureIsOrphaned() {
        Set<String> referenced = new HashSet<>();
        referenced.add("gui/slot.png");
        referenced.add("gui/slot_ready.png");
        for (Ability ability : AbilityRegistry.ordered()) {
            referenced.add(ability.icon().getPath().substring("textures/".length()));
        }
        Path root = assets.resolve("textures");
        for (Path texture : walk(root, ".png")) {
            String relative = root.relativize(texture).toString().replace('\\', '/');
            assertTrue(referenced.contains(relative),
                    "texture nothing can ever draw: " + relative);
        }
    }

    // ---------------------------------------------------------------- sounds

    @Test
    void theReadyChimeHasAnOggASubtitleAndTheIdTheCodeRegisters() {
        JsonObject sounds = json(assets.resolve("sounds.json"));
        assertEquals(1, sounds.size(), "this feature registers exactly one sound event");
        assertTrue(sounds.has(PowersFeature.READY_SOUND_PATH),
                "sounds.json must define " + PowersFeature.READY_SOUND_PATH
                        + ", the path PowersFeature registers");

        JsonObject definition = sounds.getAsJsonObject(PowersFeature.READY_SOUND_PATH);
        String subtitle = definition.get("subtitle").getAsString();
        assertEquals("subtitles." + NS + "." + PowersFeature.READY_SOUND_PATH, subtitle,
                "subtitle key should mirror the sound event id");
        assertTrue(lang.containsKey(subtitle), "no lang line for " + subtitle);

        for (JsonElement sound : definition.getAsJsonArray("sounds")) {
            String id = sound.getAsString();
            assertTrue(id.startsWith(PREFIX), "foreign sound file referenced: " + id);
            Path ogg = assets.resolve("sounds").resolve(id.substring(PREFIX.length()) + ".ogg");
            assertTrue(Files.isRegularFile(ogg), "sounds.json names a missing file: " + ogg);
            assertTrue(size(ogg) > 512, "suspiciously small sound file: " + ogg);
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

    // ------------------------------------------------------------- structure

    @Test
    void thisFeatureShipsNoModelsOrBlockstates() {
        // It registers no block, item or entity, so any of these would be a leftover from a
        // copy-paste and would quietly shadow nothing at all.
        for (String directory : List.of("models", "blockstates")) {
            assertFalse(Files.isDirectory(assets.resolve(directory)),
                    "creator_powers registers no block or item, so assets/" + directory
                            + " should not exist");
        }
    }

    // ----------------------------------------------------------------- utils

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
        return new int[] {intAt(header, 16), intAt(header, 20)};
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

    private static List<Path> javaSources() {
        List<Path> java = walk(sources, ".java");
        assertFalse(java.isEmpty(), "no powers sources found under " + sources);
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
