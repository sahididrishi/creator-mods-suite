package dev.riftal.creator.features.rules;

import com.mojang.serialization.Codec;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import dev.riftal.creator.features.rules.client.RulesClient;
import dev.riftal.creator.features.rules.command.RuleCommand;
import dev.riftal.creator.features.rules.rules.BlocksExplodeRule;
import dev.riftal.creator.features.rules.rules.CraftsX10Rule;
import dev.riftal.creator.features.rules.rules.GiantMobsRule;
import dev.riftal.creator.features.rules.rules.GravityX3Rule;
import dev.riftal.creator.features.rules.rules.HeartsCurrencyRule;
import dev.riftal.creator.features.rules.rules.InventoryShuffleRule;
import dev.riftal.creator.features.rules.rules.ItemRouletteRule;
import dev.riftal.creator.features.rules.rules.LavaFloorRule;
import dev.riftal.creator.features.rules.rules.NoStopMovingRule;
import dev.riftal.creator.features.rules.rules.OneHeartRule;
import dev.riftal.creator.features.rules.rules.RandomDropsRule;
import dev.riftal.creator.features.rules.net.RuleToastPayload;
import dev.riftal.creator.features.rules.net.RulesSyncPayload;

import static dev.riftal.creator.Constants.LOG;

/**
 * Rule Engine - see {@code plans/04-rule-engine.md}.
 *
 * <p>Eleven toggleable "Minecraft but..." rules, listed on the HUD, bundled into data-pack presets
 * and persisted with the world. {@code /rule random_drops on} and the next block drops a saddle.
 *
 * <p>The feature registers no blocks, items or entities: it has no creative tab and no art. What it
 * does declare is one per-player attachment, one gamerule, two server-to-client payloads and two
 * command roots.
 * Everything else hangs off {@link RuleManager}, which is driven by the mixins in
 * {@code dev.riftal.creator.features.rules.mixin} through
 * {@link dev.riftal.creator.features.rules.hooks.RuleHooks} - and every one of those hooks is a
 * no-op while the feature is switched off in {@code config/creatormods.json}.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_rules/**} and {@code data/creator_rules/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-rules.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/rules/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/rules/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class RulesFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "rules";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_rules";

    /**
     * Hearts spent in the {@code hearts_currency} shop. Survives death, so the debt cannot be
     * cleared by respawning. Assigned in {@link #registerContent()}; null until then.
     */
    private static PlayerData<Integer> heartsSpent;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        heartsSpent = PlayerData.register(rl("hearts_spent"), Codec.INT, () -> 0, true);

        // Before any world exists, on both sides: vanilla builds a GameRules instance per world and
        // anything registered after that is simply not in it.
        RuleGameRules.register();

        RuleRegistry.register(new RandomDropsRule());
        RuleRegistry.register(new CraftsX10Rule());
        RuleRegistry.register(new LavaFloorRule());
        RuleRegistry.register(new BlocksExplodeRule());
        RuleRegistry.register(new GiantMobsRule());
        RuleRegistry.register(new GravityX3Rule());
        RuleRegistry.register(new ItemRouletteRule());
        RuleRegistry.register(new HeartsCurrencyRule());
        RuleRegistry.register(new OneHeartRule());
        RuleRegistry.register(new NoStopMovingRule());
        RuleRegistry.register(new InventoryShuffleRule());

        // Both payloads are declared here, on BOTH physical sides, because NeoForge flushes
        // payload registrations in a mod-bus event that fires straight after construction - and
        // because a dedicated server has to know the type to be allowed to send it. The handler
        // bodies touch client-only classes, but a lambda body is not resolved until it runs, and
        // these only ever run on a physical client.
        Payloads.registerS2C(RulesSyncPayload.TYPE, RulesSyncPayload.CODEC, payload ->
                dev.riftal.creator.features.rules.client.ClientRuleState.accept(payload));
        Payloads.registerS2C(RuleToastPayload.TYPE, RuleToastPayload.CODEC, payload ->
                dev.riftal.creator.features.rules.client.ClientRuleState.toast(payload));

        RuleCommand.register();
    }

    @Override
    public void initCommon() {
        LOG.info("[rules] {} rule(s) available; toggle them with /rule <name> on", RuleRegistry.size());
    }

    @Override
    public void initClient() {
        RulesClient.init();
    }

    /** The hearts-spent attachment, or null before {@link #registerContent()} has run. */
    public static PlayerData<Integer> heartsSpent() {
        return heartsSpent;
    }
}
