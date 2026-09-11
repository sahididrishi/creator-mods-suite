package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The six abilities as data: ids, slot order, the balance numbers from plan 03 section 5, and the
 * asset paths the HUD will ask the resource manager for (plan 03 section 9, unit tests 11 and 12).
 *
 * <p>No game bootstrap is needed - an {@code Ability} is a plain object, which is exactly why the
 * registry is a hand-rolled list rather than a vanilla registry.
 */
class AbilityRegistryTest {

    @BeforeAll
    static void bootstrap() {
        AbilityRegistry.bootstrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path);
    }

    @Test
    void bootstrapIsIdempotent() {
        AbilityRegistry.bootstrap();
        AbilityRegistry.bootstrap();

        assertEquals(6, AbilityRegistry.size(), "bootstrap must not double-register on a second call");
    }

    @Test
    void theSixAbilitiesAreInTheDocumentedSlotOrder() {
        assertEquals(
                List.of(id("dash"), id("fire_burst"), id("ground_pound"),
                        id("ender_pull"), id("shield_dome"), id("mob_freeze")),
                AbilityRegistry.ids(),
                "slot order is the keybind order R F G V C X");

        assertEquals(0, AbilityRegistry.canonicalSlot(id("dash")));
        assertEquals(5, AbilityRegistry.canonicalSlot(id("mob_freeze")));
        assertEquals(-1, AbilityRegistry.canonicalSlot(id("nope")));
    }

    @Test
    void idsAreUniqueAndLiveInThisFeatureNamespace() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (Ability ability : AbilityRegistry.ordered()) {
            assertTrue(seen.add(ability.id()), "duplicate ability id " + ability.id());
            assertEquals(PowersFeature.NAMESPACE, ability.id().getNamespace());
            assertEquals(ability.path(), ability.id().getPath());
        }
        assertEquals(6, seen.size());
    }

    @Test
    void cooldownsMatchThePlansBalanceTable() {
        assertEquals(60, cooldownOf("dash"));
        assertEquals(100, cooldownOf("fire_burst"));
        assertEquals(160, cooldownOf("ground_pound"));
        assertEquals(120, cooldownOf("ender_pull"));
        assertEquals(400, cooldownOf("shield_dome"));
        assertEquals(300, cooldownOf("mob_freeze"));
    }

    @Test
    void everyCooldownIsPositiveSoNothingIsSpammable() {
        for (Ability ability : AbilityRegistry.ordered()) {
            assertTrue(ability.cooldownTicks() > 0, ability.path() + " has no cooldown");
        }
    }

    @Test
    void anUnknownIdResolvesToEmptyRatherThanThrowing() {
        assertTrue(AbilityRegistry.get(id("teleport")).isEmpty());
        assertTrue(AbilityRegistry.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "dash")).isEmpty(),
                "the right path in the wrong namespace is still not one of ours");
        assertTrue(AbilityRegistry.get(id("dash")).isPresent());
    }

    @Test
    void everyAbilityNamesAnIconThatThisFeatureShips() {
        for (Ability ability : AbilityRegistry.ordered()) {
            ResourceLocation icon = ability.icon();
            assertEquals(PowersFeature.NAMESPACE, icon.getNamespace());
            assertEquals("textures/gui/abilities/" + ability.path() + ".png", icon.getPath());
        }
    }

    @Test
    void everyAbilityHasAnOpaqueHudTint() {
        for (Ability ability : AbilityRegistry.ordered()) {
            int alpha = (ability.hudColor() >>> 24) & 0xFF;
            assertEquals(0xFF, alpha, ability.path() + " has a transparent HUD tint");
        }
    }

    @Test
    void displayNamesUseThisFeaturesLangKeys() {
        List<String> keys = new ArrayList<>();
        for (Ability ability : AbilityRegistry.ordered()) {
            assertNotNull(ability.displayName());
            keys.add("ability." + PowersFeature.NAMESPACE + "." + ability.path());
        }
        assertEquals(6, keys.size());
        assertFalse(keys.contains("ability.creator_powers."), "no empty ability path");
    }

    private static int cooldownOf(String path) {
        return AbilityRegistry.get(id(path))
                .map(Ability::cooldownTicks)
                .orElseThrow(() -> new AssertionError("ability " + path + " is not registered"));
    }
}
