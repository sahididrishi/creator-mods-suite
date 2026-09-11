package dev.riftal.creator.features.powers.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's granted abilities and their cooldowns. Immutable: every mutator returns a new
 * instance, which is what {@code PlayerData} requires - a stored value that is mutated in place
 * never gets written back to disk.
 *
 * <p>{@code granted} is ordered and <em>is</em> the slot order: index 0 is keybind slot 1.
 * {@code readyAt} maps an ability id to the absolute game tick it becomes usable again; an absent
 * entry means "ready".
 */
public record PlayerPowers(List<ResourceLocation> granted, Map<ResourceLocation, Long> readyAt) {

    /** Keybind slots, and therefore the hard cap on granted abilities. */
    public static final int MAX_SLOTS = 6;

    /** Nothing granted, nothing on cooldown. */
    public static final PlayerPowers EMPTY = new PlayerPowers(List.of(), Map.of());

    public static final Codec<PlayerPowers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("granted", List.of())
                    .forGetter(PlayerPowers::granted),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG)
                    .optionalFieldOf("ready_at", Map.of())
                    .forGetter(PlayerPowers::readyAt))
            .apply(instance, PlayerPowers::new));

    public PlayerPowers {
        granted = List.copyOf(granted);
        readyAt = Map.copyOf(readyAt);
    }

    /** True when {@code id} already occupies a slot. */
    public boolean has(ResourceLocation id) {
        return granted.contains(id);
    }

    /** Slot index (0-based) of {@code id}, or {@code -1}. */
    public int slotOf(ResourceLocation id) {
        return granted.indexOf(id);
    }

    /** True when every slot is taken. */
    public boolean full() {
        return granted.size() >= MAX_SLOTS;
    }

    /**
     * Appends {@code id} to the next free slot. Granting something already granted, or granting
     * into a full loadout, returns {@code this} unchanged - the command layer reports that.
     */
    public PlayerPowers grant(ResourceLocation id) {
        if (has(id) || full()) {
            return this;
        }
        List<ResourceLocation> next = new ArrayList<>(granted);
        next.add(id);
        return new PlayerPowers(next, readyAt);
    }

    /** Removes {@code id} and its cooldown; the slots after it shift left. */
    public PlayerPowers revoke(ResourceLocation id) {
        if (!has(id)) {
            return this;
        }
        List<ResourceLocation> next = new ArrayList<>(granted);
        next.remove(id);
        Map<ResourceLocation, Long> cooldowns = new LinkedHashMap<>(readyAt);
        cooldowns.remove(id);
        return new PlayerPowers(next, cooldowns);
    }

    /** Revokes everything, cooldowns included. */
    public PlayerPowers clear() {
        return EMPTY;
    }

    /** True when {@code id} has no cooldown entry, or its cooldown has elapsed. */
    public boolean isReady(ResourceLocation id, long now) {
        Long until = readyAt.get(id);
        return until == null || now >= until;
    }

    /** Ticks of cooldown left on {@code id}, 0 when ready. */
    public int remaining(ResourceLocation id, long now) {
        Long until = readyAt.get(id);
        return until == null ? 0 : CooldownMath.remaining(until, now);
    }

    /** Absolute ready tick for {@code id}, or {@code now} when it is ready. */
    public long readyTick(ResourceLocation id, long now) {
        Long until = readyAt.get(id);
        return until == null ? now : until;
    }

    /** Puts {@code id} on cooldown for {@code ticks}, starting at {@code now}. */
    public PlayerPowers startCooldown(ResourceLocation id, long now, int ticks) {
        return withReadyAt(id, CooldownMath.readyAt(now, ticks));
    }

    /** Forces the absolute ready tick of {@code id}. */
    public PlayerPowers withReadyAt(ResourceLocation id, long tick) {
        Map<ResourceLocation, Long> cooldowns = new LinkedHashMap<>(readyAt);
        cooldowns.put(id, tick);
        return new PlayerPowers(granted, cooldowns);
    }

    /** Clears the cooldown of one ability. */
    public PlayerPowers clearCooldown(ResourceLocation id) {
        if (!readyAt.containsKey(id)) {
            return this;
        }
        Map<ResourceLocation, Long> cooldowns = new LinkedHashMap<>(readyAt);
        cooldowns.remove(id);
        return new PlayerPowers(granted, cooldowns);
    }

    /** Clears every cooldown, keeping the granted list. */
    public PlayerPowers clearCooldowns() {
        return readyAt.isEmpty() ? this : new PlayerPowers(granted, Map.of());
    }

    /**
     * Drops cooldown entries for abilities that are no longer granted and for cooldowns that have
     * already elapsed, so the attachment does not grow forever.
     */
    public PlayerPowers pruned(long now) {
        if (readyAt.isEmpty()) {
            return this;
        }
        Map<ResourceLocation, Long> cooldowns = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Long> entry : readyAt.entrySet()) {
            if (granted.contains(entry.getKey()) && entry.getValue() > now) {
                cooldowns.put(entry.getKey(), entry.getValue());
            }
        }
        return cooldowns.size() == readyAt.size() ? this : new PlayerPowers(granted, cooldowns);
    }
}
