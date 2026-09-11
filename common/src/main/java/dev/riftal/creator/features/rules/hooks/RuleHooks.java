package dev.riftal.creator.features.rules.hooks;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.features.rules.RuleManager;
import dev.riftal.creator.features.rules.RulesFeature;
import dev.riftal.creator.features.rules.RuleTags;
import dev.riftal.creator.features.rules.preset.RulePresets;
import dev.riftal.creator.features.rules.rules.HeartsCurrencyRule;
import dev.riftal.creator.features.rules.rules.RandomItems;
import dev.riftal.creator.features.rules.shop.ShopOffers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * The single door between vanilla and the rule engine.
 *
 * <p>Every mixin in this feature calls exactly one of these methods and does nothing else, so the
 * mixins stay three lines long and all the behaviour stays in ordinary, readable, testable classes.
 *
 * <p>Every method here is a no-op when the feature is switched off in
 * {@code config/creatormods.json}: a mixin is applied by the loader whether or not the feature is
 * enabled, and a disabled feature has to be completely inert.
 */
public final class RuleHooks {

    private static MinecraftServer boundServer;

    private static boolean disabled() {
        return !CreatorMods.isEnabled(RulesFeature.ID);
    }

    /** End of {@code MinecraftServer#tickServer}. Also where a new server session is detected. */
    public static void onServerTick(MinecraftServer server) {
        if (disabled() || server == null) {
            return;
        }
        if (server != boundServer) {
            RandomItems.invalidate();
            ShopOffers.clear();
            RulePresets.clear();
            // Only latch once the engine really came up. start() gives up when the overworld is not
            // built yet, and latching on a failed attempt left the whole feature dead for the
            // session with no retry - every /rule answering "no world is loaded".
            if (RuleManager.start(server)) {
                boundServer = server;
            }
        }
        RuleManager.tick();
    }

    /** Start of {@code MinecraftServer#stopServer}. */
    public static void onServerStopping(MinecraftServer server) {
        if (disabled()) {
            return;
        }
        RuleManager.stop();
        RandomItems.invalidate();
        ShopOffers.clear();
        boundServer = null;
    }

    /** A player finished connecting. */
    public static void onPlayerJoin(ServerPlayer player) {
        if (disabled() || player == null) {
            return;
        }
        RuleManager.onPlayerJoin(player);
    }

    /** A player respawned; {@code player} is the freshly built entity. */
    public static void onPlayerRespawn(ServerPlayer player) {
        if (disabled() || player == null) {
            return;
        }
        RuleManager.onPlayerRespawn(player);
    }

    /** A player finished changing dimension; {@code player} is the same entity, moved. */
    public static void onPlayerChangedDimension(ServerPlayer player) {
        if (disabled() || player == null) {
            return;
        }
        RuleManager.onPlayerChangedDimension(player);
    }

    /**
     * An entity was added to a server level: a spawn, a spawn egg, a spawner, {@code /summon}. Not
     * a chunk load - those never reach {@code addFreshEntity}.
     */
    public static void onEntityJoin(ServerLevel level, Entity entity) {
        if (disabled() || level == null || entity == null) {
            return;
        }
        RuleManager.onEntityJoin(level, entity);
    }

    /**
     * A player right-clicked a block. The {@code hearts_currency} shop has a physical door as well
     * as {@code /shop}: any block in {@code #creator_rules:shop_blocks} (emerald block by default)
     * opens the same menu, which reads far better on camera than typing a command.
     *
     * <p>Sneaking is deliberately left alone so the block can still be built against, and only the
     * main hand counts so the menu cannot be opened twice by one click.
     *
     * @return true when the shop was opened and vanilla's interaction must be cancelled
     */
    public static boolean onUseShopBlock(ServerPlayer player, Level level, InteractionHand hand,
                                         BlockHitResult hitResult) {
        if (disabled() || player == null || hitResult == null || hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel) || player.isSecondaryUseActive()) {
            return false;
        }
        if (!RuleManager.isActive(HeartsCurrencyRule.ID)) {
            return false;
        }
        if (!serverLevel.getBlockState(hitResult.getBlockPos()).is(RuleTags.SHOP_BLOCKS)) {
            return false;
        }
        HeartsCurrencyRule.openShop(player);
        return true;
    }

    /** A player's block break completed; the block is already out of the world. */
    public static void onBlockDestroyed(ServerPlayer player, ServerLevel level, BlockPos pos,
                                        BlockState state) {
        if (disabled() || player == null || level == null) {
            return;
        }
        RuleManager.onBlockBroken(player, level, pos, state);
    }

    /** A crafting result slot was taken. Called on both sides; the client side is dropped here. */
    public static void onCraftTaken(Player player, ItemStack result) {
        if (disabled()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return;
        }
        RuleManager.onCraftTaken(serverPlayer, result);
    }

    /**
     * Vanilla just computed what a block drops.
     *
     * @return the list to use - {@code original} when no rule wants to change it
     */
    public static List<ItemStack> remapBlockDrops(List<ItemStack> original, BlockState state,
                                                  Level level, BlockPos pos) {
        if (disabled() || original == null || !(level instanceof ServerLevel serverLevel)) {
            return original;
        }
        List<ItemStack> replacement = RuleManager.remapBlockDrops(serverLevel, pos, state, original);
        return replacement == null ? original : replacement;
    }

    /**
     * A mob is about to roll its death loot table.
     *
     * @return the replacement drops, or {@code null} to let vanilla run
     */
    public static List<ItemStack> remapMobDrops(LivingEntity entity) {
        if (disabled() || entity == null || entity.level().isClientSide()) {
            return null;
        }
        return RuleManager.remapMobDrops(entity);
    }

    private RuleHooks() {
    }
}
