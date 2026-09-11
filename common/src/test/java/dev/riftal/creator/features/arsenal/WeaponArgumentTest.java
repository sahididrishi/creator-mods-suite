package dev.riftal.creator.features.arsenal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.arsenal.command.Weapon;
import dev.riftal.creator.features.arsenal.item.GrappleBladeItem;
import dev.riftal.creator.features.arsenal.item.GravityHammerItem;
import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.item.StormBowItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * {@code /arsenal give &lt;targets&gt; &lt;weapon&gt;} is typed live on camera, so the argument has
 * to accept exactly the four registry paths, suggest exactly those four, and refuse anything else
 * rather than handing out a fallback weapon.
 *
 * <p>{@code Weapon.item()} is not called here - it reads the item registry, which is empty in a
 * unit test. The GameTest asserts the items themselves.
 */
class WeaponArgumentTest {

    @Test
    void theIdsAreTheRegistryPaths() {
        assertEquals(GrappleBladeItem.PATH, Weapon.GRAPPLE_BLADE.id());
        assertEquals(StormBowItem.PATH, Weapon.STORM_BOW.id());
        assertEquals(GravityHammerItem.PATH, Weapon.GRAVITY_HAMMER.id());
        assertEquals(SoulScytheItem.PATH, Weapon.SOUL_SCYTHE.id());
    }

    @Test
    void everyIdRoundTrips() {
        for (Weapon weapon : Weapon.values()) {
            assertEquals(Optional.of(weapon), Weapon.byId(weapon.id()),
                    "byId should return " + weapon + " for '" + weapon.id() + "'");
        }
    }

    @Test
    void unknownAndMisspeltWeaponsAreRejected() {
        assertTrue(Weapon.byId("banana").isEmpty());
        assertTrue(Weapon.byId("").isEmpty());
        assertTrue(Weapon.byId("all").isEmpty(), "'all' is a separate literal node, not a weapon id");
        assertTrue(Weapon.byId("Soul_Scythe").isEmpty(), "ids are case sensitive");
        assertTrue(Weapon.byId("creator_arsenal:soul_scythe").isEmpty(),
                "the argument takes a bare path, not a namespaced id");
    }

    @Test
    void suggestionsListAllFourWeaponsExactlyOnce() {
        List<String> ids = Weapon.ids();

        assertEquals(4, ids.size());
        assertEquals(List.of("grapple_blade", "storm_bow", "gravity_hammer", "soul_scythe"), ids);
        assertEquals(ids.size(), ids.stream().distinct().count(), "no duplicate suggestions");
        assertFalse(ids.contains("all"));
    }

    @Test
    void theGiveAllListIsTheWholeEnum() {
        assertEquals(4, Weapon.values().length);
        for (Weapon weapon : Weapon.values()) {
            assertTrue(Weapon.ids().contains(weapon.id()), weapon + " is missing from ids()");
        }
    }
}
