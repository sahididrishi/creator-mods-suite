package dev.riftal.creator.features.powers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.powers.data.PlayerPowers;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The persisted loadout record: slot order, the six-slot cap, cooldown bookkeeping and the
 * immutability {@code PlayerData} depends on (plan 03 section 9, unit tests 4-8).
 */
class PlayerPowersTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PowersFeature.NAMESPACE, path);
    }

    private static final ResourceLocation DASH = id("dash");
    private static final ResourceLocation FIRE = id("fire_burst");
    private static final ResourceLocation POUND = id("ground_pound");

    // ------------------------------------------------------------------ grants

    @Test
    void grantsKeepInsertionOrderBecauseThatIsSlotOrder() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(POUND).grant(DASH).grant(FIRE);

        assertEquals(List.of(POUND, DASH, FIRE), powers.granted());
        assertEquals(0, powers.slotOf(POUND));
        assertEquals(2, powers.slotOf(FIRE));
        assertEquals(-1, powers.slotOf(id("mob_freeze")));
    }

    @Test
    void grantingIsCappedAtSixSlots() {
        PlayerPowers powers = PlayerPowers.EMPTY;
        for (int i = 0; i < PlayerPowers.MAX_SLOTS; i++) {
            powers = powers.grant(id("ability_" + i));
        }
        assertTrue(powers.full());

        PlayerPowers overflowed = powers.grant(id("ability_seven"));
        assertSame(powers, overflowed, "a seventh grant must be a no-op, not a silent drop of slot 1");
        assertEquals(PlayerPowers.MAX_SLOTS, overflowed.granted().size());
    }

    @Test
    void grantingSomethingAlreadyHeldIsANoOp() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH);
        assertSame(powers, powers.grant(DASH));
        assertEquals(1, powers.granted().size());
    }

    @Test
    void revokeShiftsTheSlotsAfterItAndDropsThatCooldown() {
        PlayerPowers powers = PlayerPowers.EMPTY
                .grant(DASH).grant(FIRE).grant(POUND)
                .startCooldown(FIRE, 100L, 100)
                .startCooldown(POUND, 100L, 160);

        PlayerPowers after = powers.revoke(FIRE);

        assertEquals(List.of(DASH, POUND), after.granted());
        assertEquals(1, after.slotOf(POUND), "ground pound moves from slot 3 to slot 2");
        assertTrue(after.isReady(FIRE, 100L), "the revoked ability's cooldown goes with it");
        assertEquals(160, after.remaining(POUND, 100L), "the other cooldowns are untouched");
    }

    @Test
    void revokingSomethingNotHeldIsANoOp() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH);
        assertSame(powers, powers.revoke(FIRE));
    }

    @Test
    void clearEmptiesBothTheSlotsAndTheCooldowns() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH).startCooldown(DASH, 0L, 60);

        PlayerPowers cleared = powers.clear();

        assertTrue(cleared.granted().isEmpty());
        assertTrue(cleared.readyAt().isEmpty());
    }

    // ------------------------------------------------------------------ cooldowns

    @Test
    void anAbilityWithNoEntryIsReady() {
        assertTrue(PlayerPowers.EMPTY.isReady(DASH, 0L));
        assertEquals(0, PlayerPowers.EMPTY.remaining(DASH, 0L));
        assertEquals(500L, PlayerPowers.EMPTY.readyTick(DASH, 500L),
                "with no cooldown the ready tick is 'now', which is what the sync packet ships");
    }

    @Test
    void startCooldownBecomesReadyOnExactlyTheRightTick() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH).startCooldown(DASH, 100L, 60);

        assertFalse(powers.isReady(DASH, 159L));
        assertEquals(1, powers.remaining(DASH, 159L));
        assertTrue(powers.isReady(DASH, 160L));
        assertEquals(0, powers.remaining(DASH, 160L));
        assertEquals(160L, powers.readyTick(DASH, 100L));
    }

    @Test
    void cooldownsAreAbsoluteSoTheySurviveARelog() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH).startCooldown(DASH, 1_000L, 400);

        // "Log out at 1100, log back in at 1300": nothing recalculated, still the same ready tick.
        assertEquals(300, powers.remaining(DASH, 1_100L));
        assertEquals(100, powers.remaining(DASH, 1_300L));
        assertEquals(1_400L, powers.readyTick(DASH, 1_300L));
    }

    @Test
    void clearCooldownTouchesOnlyThatAbility() {
        PlayerPowers powers = PlayerPowers.EMPTY
                .grant(DASH).grant(FIRE)
                .startCooldown(DASH, 0L, 60)
                .startCooldown(FIRE, 0L, 100);

        PlayerPowers after = powers.clearCooldown(DASH);

        assertTrue(after.isReady(DASH, 0L));
        assertEquals(100, after.remaining(FIRE, 0L));
        assertSame(after, after.clearCooldown(DASH), "clearing a cleared cooldown is a no-op");

        PlayerPowers all = powers.clearCooldowns();
        assertTrue(all.readyAt().isEmpty());
        assertEquals(List.of(DASH, FIRE), all.granted(), "resetting cooldowns must not revoke anything");
    }

    @Test
    void pruningDropsElapsedAndUngrantedEntriesOnly() {
        PlayerPowers powers = PlayerPowers.EMPTY
                .grant(DASH).grant(FIRE)
                .startCooldown(DASH, 0L, 60)
                .startCooldown(FIRE, 0L, 400)
                .withReadyAt(POUND, 900L);

        PlayerPowers pruned = powers.pruned(100L);

        assertEquals(Map.of(FIRE, 400L), pruned.readyAt(),
                "dash has elapsed and ground pound is not granted; only fire burst survives");
        assertEquals(List.of(DASH, FIRE), pruned.granted());
        assertSame(pruned, pruned.pruned(100L), "pruning twice changes nothing and allocates nothing");
    }

    // ------------------------------------------------------------------ immutability

    @Test
    void theRecordCopiesItsInputsSoAStoredValueCanNeverBeMutatedInPlace() {
        List<ResourceLocation> granted = new ArrayList<>(List.of(DASH));
        PlayerPowers powers = new PlayerPowers(granted, Map.of(DASH, 60L));

        granted.add(FIRE);

        assertEquals(List.of(DASH), powers.granted(), "the record kept its own copy");
        assertThrows(UnsupportedOperationException.class, () -> powers.granted().add(POUND));
        assertThrows(UnsupportedOperationException.class, () -> powers.readyAt().put(FIRE, 1L));
    }

    @Test
    void everyMutatorReturnsANewInstance() {
        PlayerPowers powers = PlayerPowers.EMPTY.grant(DASH);

        assertNotSame(powers, powers.grant(FIRE));
        assertNotSame(powers, powers.startCooldown(DASH, 0L, 60));
        assertNotSame(powers, powers.revoke(DASH));
        assertEquals(List.of(DASH), powers.granted(), "the original is untouched by all of that");
    }

    @Test
    void equalityIsByValueBecauseTheSyncSweepComparesSnapshots() {
        PlayerPowers a = PlayerPowers.EMPTY.grant(DASH).startCooldown(DASH, 10L, 60);
        PlayerPowers b = PlayerPowers.EMPTY.grant(DASH).startCooldown(DASH, 10L, 60);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame(a, b);
    }
}
