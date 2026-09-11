package dev.riftal.creator.features.rules;

import dev.riftal.creator.features.rules.mixin.RulesGameRulesBooleanValueMixin;
import dev.riftal.creator.features.rules.mixin.RulesGameRulesMixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import static dev.riftal.creator.Constants.LOG;

/**
 * The one vanilla gamerule this feature owns: {@code creator_rules.rulesHud}.
 *
 * <p>The plan wants a pack or a server operator to be able to hide the rule list without an op
 * typing {@code /rule hud off} every session, and a gamerule is the only per-world switch vanilla
 * already persists, syncs to the world file and exposes in the world-creation screen. Its
 * translation key is {@code gamerule.} + the rule name, which is why the name carries the
 * namespace: {@code gamerule.creator_rules.rulesHud}.
 *
 * <p>Registration goes through two static {@code @Invoker}s rather than a loader API, because
 * {@code GameRules#register} and {@code GameRules.BooleanValue#create} are both hidden in vanilla
 * (see {@link RulesGameRulesMixin}). If that ever fails the feature degrades to the
 * {@code RuleSavedData} flag instead of crashing: {@code /rule hud} keeps working either way.
 */
public final class RuleGameRules {

    /** Gamerule name, and therefore {@code gamerule.creator_rules.rulesHud} in the lang file. */
    public static final String HUD_RULE = "creator_rules.rulesHud";

    private static GameRules.Key<GameRules.BooleanValue> hudKey;
    private static boolean attempted;

    /**
     * Declares the gamerule. Called from {@code RulesFeature#registerContent()}, i.e. during mod
     * construction on both loaders - which is before any {@code GameRules} instance is built for a
     * world, the one ordering requirement vanilla has here. Idempotent.
     */
    public static void register() {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            hudKey = RulesGameRulesMixin.creator_rules$register(
                    HUD_RULE,
                    GameRules.Category.MISC,
                    RulesGameRulesBooleanValueMixin.creator_rules$create(
                            true, (server, value) -> RuleManager.broadcast()));
            LOG.info("[rules] gamerule {} registered", HUD_RULE);
        } catch (Throwable failure) {
            hudKey = null;
            LOG.warn("[rules] could not register the {} gamerule; /rule hud still hides the list",
                    HUD_RULE, failure);
        }
    }

    /** True once {@link #register()} has succeeded. */
    public static boolean isRegistered() {
        return hudKey != null;
    }

    /** The gamerule key, or {@code null} when registration failed. */
    public static GameRules.Key<GameRules.BooleanValue> hudKey() {
        return hudKey;
    }

    /**
     * The gamerule's value, or {@code fallback} when there is no server or no gamerule.
     */
    public static boolean hud(MinecraftServer server, boolean fallback) {
        if (server == null || hudKey == null) {
            return fallback;
        }
        return server.getGameRules().getBoolean(hudKey);
    }

    /** Writes the gamerule, which also fires its change listener and re-broadcasts the HUD state. */
    public static void setHud(MinecraftServer server, boolean value) {
        if (server == null || hudKey == null) {
            return;
        }
        server.getGameRules().getRule(hudKey).set(value, server);
    }

    private RuleGameRules() {
    }
}
