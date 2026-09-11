package dev.riftal.creator.features.events.events;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;

import java.util.List;

/**
 * {@code bloodmoon} - the sky floods red, monsters glow through the trees and the spawn cap doubles
 * until dawn.
 *
 * <p>Three phases: a 40-tick {@code rise} that ramps the tint in and time-lapses the clock to dusk,
 * an open {@code night} that ends at dawn, and a 40-tick {@code fade} that time-lapses it back to
 * morning - so {@code /event skip} out of the night actually brings the sun up, which is the beat
 * the demo clip is cut around.
 */
public final class BloodMoonEvent implements WorldEvent {

    /** Ticks the tint takes to ramp in and out, and the clock takes to lapse. */
    public static final int RAMP_TICKS = 40;

    /** How much extra monster headroom a blood moon asks for. */
    public static final float SPAWN_MULTIPLIER = 2.0F;

    /** Safety net for {@code doDaylightCycle=false}: with a frozen clock the night cannot outlive this. */
    public static final int MAX_NIGHT_TICKS = 6000;

    /**
     * The looping bed the client plays. Matches the sound event {@code EventsFeature} registers as
     * {@code bloodmoon.drone}; written out rather than read off the registry entry so this class
     * stays usable before the registries are bound (the asset-coverage unit test builds one).
     */
    public static final String DRONE_LOOP = EventsFeature.NAMESPACE + ":bloodmoon.drone";

    /** How far from a player a monster has to be to get the glow. */
    private static final double GLOW_RADIUS = 96.0D;

    private static final int GLOW_PERIOD = 20;
    private static final long DUSK = 13000L;
    private static final long DAY_LENGTH = 24000L;

    private float tintStrength;
    private long timeJumpRemaining;
    private long timeJumpPerTick;
    private boolean droning = true;

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
        EventHooks.setMonsterSpawnMultiplier(SPAWN_MULTIPLIER, ctx.level().dimension());
        if (resumed) {
            tintStrength = SkyTint.BLOOD.strength();
            return;
        }
        ServerLevel level = ctx.level();
        long timeOfDay = Math.floorMod(level.getDayTime(), DAY_LENGTH);
        timeJumpRemaining = timeOfDay < DUSK ? DUSK - timeOfDay : 0L;
        timeJumpPerTick = Math.max(1L, timeJumpRemaining / RAMP_TICKS);
        droning = true;
        glowNearbyMonsters(ctx);
    }

    @Override
    public void onPhaseStart(EventContext ctx, EventPhase phase, int phaseIndex) {
        switch (phase.id()) {
            case "night" -> {
                tintStrength = SkyTint.BLOOD.strength();
                droning = true;
                // Land exactly on dusk even if the per-tick step left a remainder.
                landTimeJump(ctx);
            }
            case "fade" -> {
                droning = false;
                // Dawn. This is the whole point of `/event skip` on a blood moon: the natural exit
                // waits for the clock to reach morning on its own, but a skip arrives with the sun
                // still down, and a "fade" that only drained the red left the world at night.
                // Ramped over the same 40 ticks as `rise`, so it reads as a time-lapse on camera.
                timeJumpRemaining = 0L;
                if (!ctx.level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                    // A deliberately frozen clock is left exactly where the director put it.
                    return;
                }
                long timeOfDay = Math.floorMod(ctx.level().getDayTime(), DAY_LENGTH);
                if (timeOfDay >= 12000L) {
                    timeJumpRemaining = DAY_LENGTH - timeOfDay;
                    timeJumpPerTick = Math.max(1L, timeJumpRemaining / RAMP_TICKS);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void tick(EventContext ctx, EventPhase phase, int phaseTick) {
        switch (phase.id()) {
            case "rise" -> {
                tintStrength = SkyTint.BLOOD.strength() * Math.min(1.0F, (float) phaseTick / RAMP_TICKS);
                stepTimeJump(ctx);
            }
            case "night" -> {
                tintStrength = SkyTint.BLOOD.strength();
                if (phaseTick % GLOW_PERIOD == 0) {
                    glowNearbyMonsters(ctx);
                }
            }
            case "fade" -> {
                tintStrength = SkyTint.BLOOD.strength()
                        * Math.max(0.0F, 1.0F - (float) phaseTick / RAMP_TICKS);
                stepTimeJump(ctx);
                if (phaseTick >= RAMP_TICKS) {
                    // The phase ends on this tick; put whatever the per-tick step left on the clock.
                    landTimeJump(ctx);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean isPhaseComplete(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("night".equals(phase.id())) {
            if (!ctx.level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                // A frozen clock never reaches dawn, so this is the only phase that needs a
                // fallback length. With the cycle running, a real night from dusk to dawn is about
                // 11 000 ticks and the cap must not cut it short at 6 000.
                return phaseTick >= MAX_NIGHT_TICKS;
            }
            return Math.floorMod(ctx.level().getDayTime(), DAY_LENGTH) < 12000L;
        }
        return WorldEvent.super.isPhaseComplete(ctx, phase, phaseTick);
    }

    @Override
    public void onStop(EventContext ctx, StopReason reason) {
        EventHooks.setMonsterSpawnMultiplier(1.0F, null);
        tintStrength = 0.0F;
        droning = false;
        if (ctx == null) {
            return;
        }
        // Only the loaded chunks can be swept here; anything that unloaded mid-event is caught by
        // EventsEntityCallbacksMixin the next time it loads.
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
    public String ambientLoop() {
        return droning ? DRONE_LOOP : "";
    }

    @Override
    public float progress(EventContext ctx, EventPhase phase, int phaseTick) {
        if ("night".equals(phase.id())) {
            long timeOfDay = Math.floorMod(ctx.level().getDayTime(), DAY_LENGTH);
            if (timeOfDay < DUSK) {
                return 1.0F;
            }
            return (float) (timeOfDay - DUSK) / (DAY_LENGTH - DUSK);
        }
        return WorldEvent.super.progress(ctx, phase, phaseTick);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putFloat("tint", tintStrength);
        tag.putLong("time_jump", timeJumpRemaining);
        tag.putBoolean("droning", droning);
    }

    @Override
    public void load(CompoundTag tag) {
        tintStrength = tag.getFloat("tint");
        timeJumpRemaining = tag.getLong("time_jump");
        timeJumpPerTick = Math.max(1L, timeJumpRemaining / RAMP_TICKS);
        droning = !tag.contains("droning") || tag.getBoolean("droning");
    }

    /** One tick of the clock lapse, if one is in progress. */
    private void stepTimeJump(EventContext ctx) {
        if (timeJumpRemaining <= 0L) {
            return;
        }
        long step = Math.min(timeJumpPerTick, timeJumpRemaining);
        ctx.level().setDayTime(ctx.level().getDayTime() + step);
        timeJumpRemaining -= step;
    }

    /** Applies whatever is left of the lapse in one go. */
    private void landTimeJump(EventContext ctx) {
        if (timeJumpRemaining <= 0L) {
            return;
        }
        ctx.level().setDayTime(ctx.level().getDayTime() + timeJumpRemaining);
        timeJumpRemaining = 0L;
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
}
