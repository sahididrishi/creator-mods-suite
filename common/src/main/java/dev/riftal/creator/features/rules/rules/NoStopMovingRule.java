package dev.riftal.creator.features.rules.rules;

import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Minecraft but you must keep moving.</b>
 *
 * <p>Two seconds of standing still and the player starts taking magic damage twice a second.
 * Sneaking does not help; creative and spectator players are exempt; joining, respawning and
 * changing dimension all buy five seconds of grace. An action-bar warning appears at the halfway
 * mark so the damage never feels unfair on camera.
 */
public final class NoStopMovingRule implements Rule {

    /** Still ticks before the first hit. */
    public static final int GRACE_TICKS = 40;

    /** Ticks between hits once the player is over the limit. */
    public static final int DAMAGE_INTERVAL = 10;

    /** Grace granted on join, respawn and dimension change. */
    public static final int RESPAWN_GRACE_TICKS = 100;

    private static final float DAMAGE = 1.0F;

    private final StillTracker tracker = new StillTracker();

    @Override
    public String id() {
        return "no_stop_moving";
    }

    @Override
    public void onEnable(RuleContext ctx) {
        tracker.clear();
        for (ServerPlayer player : ctx.players()) {
            tracker.grace(player.getUUID(), RESPAWN_GRACE_TICKS);
        }
    }

    @Override
    public void onDisable(RuleContext ctx) {
        tracker.clear();
    }

    @Override
    public void onPlayerJoin(RuleContext ctx, ServerPlayer player) {
        tracker.grace(player.getUUID(), RESPAWN_GRACE_TICKS);
    }

    @Override
    public void onPlayerRespawn(RuleContext ctx, ServerPlayer player) {
        tracker.grace(player.getUUID(), RESPAWN_GRACE_TICKS);
    }

    @Override
    public void tick(RuleContext ctx) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : ctx.players()) {
            UUID uuid = player.getUUID();
            seen.add(uuid);
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                tracker.grace(uuid, RESPAWN_GRACE_TICKS);
                continue;
            }
            Vec3 pos = player.position();
            int still = tracker.update(uuid, pos.x, pos.y, pos.z);
            if (still == GRACE_TICKS / 2) {
                player.displayClientMessage(
                        Component.translatable("hud.creator_rules.move").withStyle(ChatFormatting.RED),
                        true);
            }
            if (still >= GRACE_TICKS && (still - GRACE_TICKS) % DAMAGE_INTERVAL == 0) {
                player.hurt(player.serverLevel().damageSources().magic(), DAMAGE);
            }
        }
        if (tracker.size() > seen.size()) {
            tracker.retainAll(seen);
        }
    }
}
