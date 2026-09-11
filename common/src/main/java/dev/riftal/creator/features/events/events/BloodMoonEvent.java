package dev.riftal.creator.features.events.events;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventContext;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.SkyTint;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.hooks.EventHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;

import java.util.List;

/**
 * {@code bloodmoon} - the sky floods red, monsters glow through the trees and the spawn cap doubles
 * until dawn.
 *
 * <p>Three phases: a 40-tick {@code rise} that ramps the tint in and time-lapses the clock to dusk,
 * an open {@code night} that ends at dawn, and a 40-tick {@code fade}.
 */
public final class BloodMoonEvent implements WorldEvent {

    /** Ticks the tint takes to ramp in and out. */
    public static final int RAMP_TICKS = 40;

    /** How much extra monster headroom a blood moon asks for. */
    public static final float SPAWN_MULTIPLIER = 2.0F;

    /** Safety net for {@code doDaylightCycle=false}: the night cannot outlive this. */
    public static final int MAX_NIGHT_TICKS = 6000;

    /** How far from a player a monster has to be to get the glow. */
    private static final double GLOW_RADIUS = 96.0D;

    private static final int GLOW_PERIOD = 20;
    private static final int DRONE_PERIOD = 100;
    private static final long DUSK = 13000L;

    private float tintStrength;
    private long timeJumpRemaining;
    private long timeJumpPerTick;

    @Override
    public String id() {
        return "bloodmoon";
    }

    @Override
    public List<EventPhase> phases() {
        return List.of(
                EventPhase.ticks("rise", RAMP_TICKS),
                EventPhase.open("night"),
                EventPhase.ticks("fade", RAMP_TICKS));
    }

    @Override
    public void onStart(EventContext ctx, boolean resumed) {
        EventHooks.setMonsterSpawnMultiplier(SPAWN_MULTIPLIER);
        if (resumed) {
            tintStrength = SkyTint.BLOOD.strength();
            return;
        }
        ServerLevel level = ctx.level();
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        timeJumpRemaining = timeOfDay < DUSK ? DUSK - timeOfDay : 0L;
        timeJumpPerTick = Math.max(1L, timeJumpRemaining / RAMP_TICKS);
        glowNearbyMonsters(ctx);
    }

    @Override
    public void onPhaseStart(EventContext ctx, EventPhase phase, int phaseIndex) {
        if ("night".equals(phase.id())) {
            tintStrength = SkyTint.BLOOD.strength();
            // Land exactly on dusk even if the per-tick step left a remainder.
            if (timeJumpRemaining > 0L) {
                ctx.level().setDayTime(ctx.level().getDayTime() + timeJumpRemaining);
                timeJumpRemaining = 0L;
            }
        }
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        switch (phase.id()) {
            case "rise" -> {
                tintStrength = SkyTint.BLOOD.strength() * Math.min(1.0F, (float) phaseTick / RAMP_TICKS);
                if (timeJumpRemaining > 0L) {
                    long step = Math.min(timeJumpPerTick, timeJumpRemaining);
                    ctx.level().setDayTime(ctx.level().getDayTime() + step);
                    timeJumpRemaining -= step;
                }
            }
            case "night" -> {
                tintStrength = SkyTint.BLOOD.strength();
                if (phaseTick % GLOW_PERIOD == 0) {
                    glowNearbyMonsters(ctx);
                }
                if (phaseTick % DRONE_PERIOD == 1) {
                    playDrone(ctx);
                }
            }
            case "fade" -> tintStrength =
                    SkyTint.BLOOD.strength() * Math.max(0.0F, 1.0F - (float) phaseTick / RAMP_TICKS);
            default -> {
            }
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("night".equals(phase.id())) {
            if (phaseTick >= MAX_NIGHT_TICKS) {
                return true;
            }
            if (!ctx.level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                return false;
            }
            return Math.floorMod(ctx.level().getDayTime(), 24000L) < 12000L;
        }
        return WorldEvent.super.isPhaseComplete(ctx, phase, phaseTick);
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        EventHooks.setMonsterSpawnMultiplier(1.0F);
        tintStrength = 0.0F;
        if (ctx == null) {
            return;
        }
        for (Entity entity : ctx.level().getAllEntities()) {
            if (entity.getTags().contains(EventHooks.TAG_BLOODMOON)) {
                entity.setGlowingTag(false);
                entity.removeTag(EventHooks.TAG_BLOODMOON);
            }
        }
    }

    @Override
    public void onPlayerJoin(EventContext ctx, ServerPlayer player) {
        glowAround(ctx.level(), player);
    }

    @Override
    public SkyTint skyTint() {
        return SkyTint.BLOOD.withStrength(tintStrength);
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("night".equals(phase.id())) {
            long timeOfDay = Math.floorMod(ctx.level().getDayTime(), 24000L);
            if (timeOfDay < DUSK) {
                return 1.0F;
            }
            return (float) (timeOfDay - DUSK) / (24000.0F - DUSK);
        }
        return WorldEvent.super.progress(ctx, phase, phaseTick);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putFloat("tint", tintStrength);
        tag.putLong("time_jump", timeJumpRemaining);
    }

    @Override
    public void load(CompoundTag tag) {
        tintStrength = tag.getFloat("tint");
        timeJumpRemaining = tag.getLong("time_jump");
        timeJumpPerTick = Math.max(1L, timeJumpRemaining / RAMP_TICKS);
    }

    private void glowNearbyMonsters(EventContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            glowAround(ctx.level(), player);
        }
    }

    private void glowAround(ServerLevel level, ServerPlayer player) {
        List<Monster> monsters = Selection.around(level, Monster.class, player.position(), GLOW_RADIUS,
                monster -> !monster.getTags().contains(EventHooks.TAG_BLOODMOON));
        for (Monster monster : monsters) {
            monster.addTag(EventHooks.TAG_BLOODMOON);
            monster.setGlowingTag(true);
        }
    }

    private void playDrone(EventContext ctx) {
        for (ServerPlayer player : ctx.players()) {
            Fx.sound(ctx.level(), player.position(), EventsFeature.bloodmoonDrone(),
                    SoundSource.AMBIENT, 0.6F, 1.0F);
        }
    }
}
