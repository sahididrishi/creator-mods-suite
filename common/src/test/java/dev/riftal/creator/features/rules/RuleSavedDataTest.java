package dev.riftal.creator.features.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The per-world persistence round trip: what {@code /rule ... on} writes has to come back in the
 * same order after a restart, together with every rule's private timer tag and the HUD flag.
 *
 * <p>NBT classes are registry-free, so this runs without bootstrapping the game.
 */
class RuleSavedDataTest {

    private static RuleSavedData roundTrip(RuleSavedData original) {
        return RuleSavedData.load(original.save(new CompoundTag(), null), null);
    }

    @Test
    void activeOrderSurvivesTheRoundTrip() {
        RuleSavedData data = new RuleSavedData();
        data.setActive(List.of("random_drops", "crafts_x10", "one_heart"));

        assertEquals(List.of("random_drops", "crafts_x10", "one_heart"), roundTrip(data).active());
    }

    @Test
    void duplicateIdsAreCollapsedOnTheWayIn() {
        RuleSavedData data = new RuleSavedData();
        data.setActive(List.of("one_heart", "one_heart", "gravity_x3"));

        assertEquals(List.of("one_heart", "gravity_x3"), data.active());
        assertEquals(List.of("one_heart", "gravity_x3"), roundTrip(data).active());
    }

    @Test
    void eachRuleKeepsItsOwnPrivateTimerTag() {
        RuleSavedData data = new RuleSavedData();
        CompoundTag roulette = new CompoundTag();
        roulette.putLong("nextRollTick", 12345L);
        CompoundTag shuffle = new CompoundTag();
        shuffle.putLong("nextShuffleTick", 678L);
        data.putRuleState("item_roulette", roulette);
        data.putRuleState("inventory_shuffle", shuffle);

        RuleSavedData loaded = roundTrip(data);

        assertEquals(12345L, loaded.ruleState("item_roulette").getLong("nextRollTick"));
        assertEquals(678L, loaded.ruleState("inventory_shuffle").getLong("nextShuffleTick"));
    }

    @Test
    void anUnknownRuleStateIsAnEmptyTagRatherThanNull() {
        RuleSavedData loaded = roundTrip(new RuleSavedData());

        assertTrue(loaded.ruleState("never_registered").isEmpty());
    }

    @Test
    void theHudFlagDefaultsToShownAndSurvivesBeingTurnedOff() {
        assertTrue(new RuleSavedData().hud());
        assertTrue(roundTrip(new RuleSavedData()).hud());

        RuleSavedData hidden = new RuleSavedData();
        hidden.setHud(false);

        assertFalse(roundTrip(hidden).hud());
    }

    @Test
    void aWorldSavedBeforeTheHudFlagExistedStillShowsTheHud() {
        // No "hud" key at all: an old creator_rules.dat, or a hand-edited one.
        CompoundTag legacy = new CompoundTag();

        assertTrue(RuleSavedData.load(legacy, null).hud());
        assertTrue(RuleSavedData.load(legacy, null).active().isEmpty());
    }

    @Test
    void mutationsMarkTheDataDirtySoItIsActuallyWrittenToDisk() {
        RuleSavedData data = new RuleSavedData();
        assertFalse(data.isDirty());

        data.setActive(List.of("lava_floor"));
        assertTrue(data.isDirty(), "a rule toggle has to mark the SavedData dirty or it is never saved");

        data.setDirty(false);
        data.setHud(false);
        assertTrue(data.isDirty(), "hiding the HUD has to mark the SavedData dirty");

        data.setDirty(false);
        data.putRuleState("item_roulette", new CompoundTag());
        assertTrue(data.isDirty(), "a rule timer flush has to mark the SavedData dirty");
    }

    @Test
    void theActiveListHandedOutIsACopy() {
        RuleSavedData data = new RuleSavedData();
        data.setActive(List.of("one_heart"));

        List<String> active = data.active();

        data.setActive(List.of("gravity_x3"));
        assertEquals(List.of("one_heart"), active, "active() must hand out a snapshot, not the live list");
    }
}
