package dev.riftal.creator.features.events.events;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.SkyTint;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.util.VoidPlane;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code voidrise} - a black kill-plane climbs out of the world floor while the director pillars up.
 *
 * <p>Nothing is destroyed: the plane is a height, a particle curtain and a HUD readout, so
 * {@code /event stop} restores the world by doing nothing at all.
 *
 * <p>Options: {@code speed} (blocks per tick, default 0.05), {@code maxY} (default 40),
 * {@code minY} (default the world floor).
 */
public final class VoidRiseEvent implements WorldEvent {

    /** Default climb rate: one block a second. */
    public static final double DEFAULT_SPEED = 0.05D;

    /** Default ceiling. */
    public static final double DEFAULT_MAX_Y = 40.0D;

    private static final int KILL_PERIOD = 5;
    private static final int PARTICLE_PERIOD = 4;
    private static final double CURTAIN_RADIUS = 24.0D;
    private static final double WARN_DISTANCE = 10.0D;

    private double planeY;
    private double startY;
    private double maxY = DEFAULT_MAX_Y;
    private double speed = DEFAULT_SPEED;

    @Override
    public String id() {
        return "voidrise";
    }

    @Override
    public List<EventPhase> phases() {
        return List.of(EventPhase.open("rise"), EventPhase.ticks("hold", 200));
    }

    @Override
    public void onStart(EventContext ctx, boolean resumed) {
        if (resumed) {
            return;
        }
        ServerLevel level = ctx.level();
        startY = ctx.options().getDouble("minY", level.getMinBuildHeight(),
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        planeY = startY;
        maxY = ctx.options().getDouble("maxY", DEFAULT_MAX_Y, startY + 1.0D, level.getMaxBuildHeight());
        speed = ctx.options().getDouble("speed", DEFAULT_SPEED, 0.001D, 4.0D);
    }

    @Override
    public boolean onTimer(EventContext ctx, int seconds) {
        speed = VoidPlane.speedForTicks(planeY, maxY, seconds * 20);
        return true;
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("rise".equals(phase.id())) {
            planeY = VoidPlane.advance(planeY, speed, maxY);
        }
        if (phaseTick % KILL_PERIOD == 0) {
            cull(ctx);
        }
        if (phaseTick % PARTICLE_PERIOD == 0) {
            curtain(ctx);
        }
        if (phaseTick % 20 == 0) {
            warn(ctx);
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("rise".equals(phase.id())) {
            return planeY >= maxY - 1.0E-6D;
        }
        return WorldEvent.super.isPhaseComplete(ctx, phase, phaseTick);
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        // Nothing to undo - the void never removed a block.
    }

    @Override
    public SkyTint skyTint() {
        return new SkyTint(0.0F, 0.0F, 0.0F, 0.30F);
    }

    @Override
    public double voidY() {
        return planeY;
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("rise".equals(phase.id())) {
            return VoidPlane.progress(startY, planeY, maxY);
        }
        return WorldEvent.super.progress(ctx, phase, phaseTick);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putDouble("plane_y", planeY);
        tag.putDouble("start_y", startY);
        tag.putDouble("max_y", maxY);
        tag.putDouble("speed", speed);
    }

    @Override
    public void load(CompoundTag tag) {
        planeY = tag.getDouble("plane_y");
        startY = tag.getDouble("start_y");
        maxY = tag.getDouble("max_y");
        speed = tag.getDouble("speed");
        if (speed <= 0.0D) {
            speed = DEFAULT_SPEED;
        }
    }

    /** One pass over the level's entities, every {@link #KILL_PERIOD} ticks, never per tick. */
    private void cull(EventContext ctx) {
        ServerLevel level = ctx.level();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getY() >= planeY || entity.isRemoved()) {
                continue;
            }
            if (entity instanceof ServerPlayer player) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                player.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            } else if (entity instanceof LivingEntity living) {
                living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            } else if (entity instanceof ItemEntity) {
                entity.discard();
            }
        }
    }

    private void curtain(EventContext ctx) {
        ServerLevel level = ctx.level();
        for (ServerPlayer player : ctx.players()) {
            Vec3 centre = new Vec3(player.getX(), planeY, player.getZ());
            Fx.particleRing(level, ParticleTypes.SQUID_INK, centre, CURTAIN_RADIUS, 40);
            Fx.particleRing(level, ParticleTypes.ASH, centre, CURTAIN_RADIUS * 0.6D, 24);
        }
    }

    private void warn(EventContext ctx) {
        Component warning = Component.translatable("hud.creator_events.void_rising")
                .withStyle(ChatFormatting.DARK_PURPLE);
        boolean anyWarned = false;
        for (ServerPlayer player : ctx.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (player.getY() - planeY <= WARN_DISTANCE) {
                player.displayClientMessage(warning, true);
                anyWarned = true;
            }
        }
        if (anyWarned) {
            Fx.sound(ctx.level(), ctx.focus(), EventsFeature.voidHum(), SoundSource.AMBIENT, 1.0F, 0.7F);
        }
    }
}
