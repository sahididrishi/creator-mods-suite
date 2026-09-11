package dev.riftal.creator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Guards the parallel-development contract: eight features, unique ids, one namespace each. */
class FeatureWiringTest {

    private static final List<String> EXPECTED_IDS = List.of(
            "toolkit", "colossus", "powers", "rules", "evolve", "events", "arsenal", "vault");

    @Test
    void allEightFeaturesAreWiredInOrder() {
        assertEquals(EXPECTED_IDS, CreatorMods.FEATURES.stream().map(Feature::id).toList());
    }

    @Test
    void idsAndNamespacesAreUniqueAndWellFormed() {
        Set<String> ids = Set.copyOf(CreatorMods.FEATURES.stream().map(Feature::id).toList());
        assertEquals(CreatorMods.FEATURES.size(), ids.size(), "feature ids must be unique");

        for (Feature feature : CreatorMods.FEATURES) {
            assertTrue(feature.id().matches("[a-z0-9_]+"), feature.id() + " is not a legal id");
            assertEquals("creator_" + feature.id(), feature.namespace());
            assertTrue(feature.namespace().matches("[a-z0-9_.-]+"),
                    feature.namespace() + " is not a legal resource namespace");
            assertEquals(feature.namespace(), feature.rl("thing").getNamespace());
        }
    }

    @Test
    void displayNameUsesTheFeatureLangKey() {
        for (Feature feature : CreatorMods.FEATURES) {
            ComponentContents contents = feature.displayName().getContents();
            assertInstanceOf(TranslatableContents.class, contents);
            assertEquals("feature." + feature.namespace() + ".name",
                    ((TranslatableContents) contents).getKey());
        }
    }

    @Test
    void everyFeatureShipsItsOwnLangFileWithThatKey() throws Exception {
        for (Feature feature : CreatorMods.FEATURES) {
            String path = "assets/" + feature.namespace() + "/lang/en_us.json";
            try (InputStream in = FeatureWiringTest.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(in, "missing " + path);
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(json.contains("\"feature." + feature.namespace() + ".name\""),
                        path + " must define feature." + feature.namespace() + ".name");
            }
        }
    }

    @Test
    void everyFeatureOwnsAMixinConfigAndAGameTestTemplate() {
        ClassLoader loader = FeatureWiringTest.class.getClassLoader();
        for (Feature feature : CreatorMods.FEATURES) {
            assertNotNull(loader.getResource("creatormods-" + feature.id() + ".mixins.json"),
                    "missing mixin config for " + feature.id());
            assertNotNull(loader.getResource("data/" + feature.namespace() + "/structure/empty.nbt"),
                    "missing empty GameTest template for " + feature.id());
        }
    }
}
