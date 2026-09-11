package dev.riftal.creator.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTest bodies live here, in common, using nothing but vanilla API. Each loader adds a thin stub
 * class that carries the loader-specific discovery annotations and delegates to these methods.
 */
public final class CommonGameTests {

    /** Smoke test: proves the GameTest harness boots, loads our structure and runs our code. */
    public static void harnessBoots(GameTestHelper helper) {
        helper.succeed();
    }

    private CommonGameTests() {
    }
}
