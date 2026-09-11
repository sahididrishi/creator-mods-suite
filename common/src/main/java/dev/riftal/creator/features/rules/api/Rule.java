package dev.riftal.creator.features.rules.api;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * One "Minecraft but..." rule. The whole pitch of this feature is that a rule is a single class:
 * implement {@link #id()}, override the two or three hooks you care about, register it in
 * {@code RulesFeature#registerContent()} and add a lang key. Nothing else.
 *
 * <p>Every method runs on the server thread. A rule is a singleton shared by every world in the
 * session, so any per-world state it keeps must be reset in {@link #onEnable} / {@link #onDisable}.
 *
 * <p><b>{@link #onDisable} must undo everything {@link #onEnable} did.</b> A rule that leaves an
 * attribute modifier behind ruins the next take.
 */
public interface Rule {

    /** Stable snake_case id. Also the command argument and the lang key suffix. */
    String id();

    /** Display name, from {@code rule.creator_rules.<id>} in this feature's lang file. */
    default Component displayName() {
        return Component.translatable("rule.creator_rules." + id());
    }

    /** One-line description, from {@code rule.creator_rules.<id>.desc}. Shown by {@code /rule list}. */
    default Component description() {
        return Component.translatable("rule.creator_rules." + id() + ".desc");
    }

    /** {@link #tick} is called every N server ticks. 1 = every tick, 20 = once a second. */
    default int tickInterval() {
        return 1;
    }

    /** Switched on. Apply modifiers, snapshot state, seed timers. */
    default void onEnable(RuleContext ctx) {
    }

    /** Switched off. Undo <em>everything</em> {@link #onEnable} did, for online and loaded entities. */
    default void onDisable(RuleContext ctx) {
    }

    /** Called every {@link #tickInterval()} server ticks while the rule is active. */
    default void tick(RuleContext ctx) {
    }

    /**
     * Runs whatever this rule normally waits for a timer to do, right now, for every online player.
     * Reached from {@code /rule <id> fire}, which exists so a 60-second rule can be filmed in one
     * take instead of one minute.
     *
     * @return true when the rule actually did something; false (the default) when it has no
     *         on-demand effect, which the command reports rather than pretending to have fired
     */
    default boolean fire(RuleContext ctx) {
        return false;
    }

    /** A player joined while the rule is active. Re-apply per-player effects. */
    default void onPlayerJoin(RuleContext ctx, ServerPlayer player) {
    }

    /** A player respawned: this is a brand-new entity, so per-player effects must be re-applied. */
    default void onPlayerRespawn(RuleContext ctx, ServerPlayer player) {
    }

    /**
     * A player finished changing dimension (or was teleported within one). The move usually looks
     * like a huge jump in position, so rules that watch movement have to be told about it.
     */
    default void onPlayerChangedDimension(RuleContext ctx, ServerPlayer player) {
    }

    /**
     * An entity was just added to a server level - a natural spawn, a spawn egg, a spawner, a
     * structure, {@code /summon}. Chunk loads do <em>not</em> come through here, which is why the
     * rules that stamp something onto mobs also keep a slow sweep as a backstop.
     */
    default void onEntityJoin(RuleContext ctx, ServerLevel level, Entity entity) {
    }

    /**
     * Strips anything this rule leaves in persistent player NBT. Called on join for every
     * <em>inactive</em> rule, so a player who was offline when the rule was switched off does not
     * come back carrying its modifiers.
     */
    default void stripFrom(ServerPlayer player) {
    }

    /** A survival player finished breaking a block. The block is already gone. */
    default void onBlockBroken(RuleContext ctx, ServerPlayer player, ServerLevel level, BlockPos pos,
                               BlockState state) {
    }

    /** A player took a stack out of a crafting result slot. Server side only. */
    default void onCraftTaken(RuleContext ctx, ServerPlayer player, ItemStack result) {
    }

    /**
     * Chance to replace what a block drops. Return {@code null} to leave the vanilla list alone.
     * {@code original} is never modified in place.
     */
    default List<ItemStack> remapBlockDrops(RuleContext ctx, ServerLevel level, BlockPos pos,
                                            BlockState state, List<ItemStack> original) {
        return null;
    }

    /**
     * Chance to replace what a mob drops on death. Return {@code null} to leave vanilla alone,
     * or a (possibly empty) list to take over the drop entirely.
     */
    default List<ItemStack> remapMobDrops(RuleContext ctx, LivingEntity entity) {
        return null;
    }

    /** Rule-private state that must survive a restart (timers, counters). */
    default void save(CompoundTag tag) {
    }

    /** Counterpart of {@link #save}. The tag may be empty on a fresh world. */
    default void load(CompoundTag tag) {
    }
}
