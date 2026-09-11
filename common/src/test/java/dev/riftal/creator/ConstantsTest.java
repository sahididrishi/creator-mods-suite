package dev.riftal.creator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Trivial smoke test proving the JUnit 5 harness in :common runs.
 * Pure-logic tests belong here; anything that needs registries or an ItemStack needs a bootstrapped
 * game and belongs in a loader module instead.
 */
class ConstantsTest {

    @Test
    void modIdIsAValidResourceNamespace() {
        assertEquals("creatormods", Constants.MOD_ID);
        assertTrue(Constants.MOD_ID.matches("[a-z0-9_.-]+"),
                "mod id must be a legal resource location namespace");
    }
}
