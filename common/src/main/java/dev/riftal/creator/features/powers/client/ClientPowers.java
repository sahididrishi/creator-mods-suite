package dev.riftal.creator.features.powers.client;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import dev.riftal.creator.features.powers.net.CooldownStartPayload;
import dev.riftal.creator.features.powers.net.PowerHudPayload;
import dev.riftal.creator.features.powers.net.SyncPowersPayload;
import dev.riftal.creator.features.powers.net.UseAbilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's mirror of its own loadout: what is in which slot, when each slot becomes ready, and
 * the little bits of presentation state (flash, refusal shake, HUD mode) that never leave this
 * machine.
 *
 * <p><b>Client only.</b> Reached from {@code initClient()}, from the S2C payload handlers and from
 * the HUD layer - never from common server code.
 *
 * <p>Cooldowns are held as the pair (started, ready) on the <em>server</em> clock, exactly as the
 * server sent them, plus an offset against this client's own level clock. That is what makes the
 * sweep land on the same tick the ability actually becomes usable rather than drifting by a few
 * frames over a long cooldown.
 */
public final class ClientPowers {

    private static final int FLASH_TICKS = 5;
    private static final int DENY_SHAKE_TICKS = 8;

    private static final List<ResourceLocation> GRANTED = new ArrayList<>();
    private static final Map<ResourceLocation, long[]> COOLDOWNS = new HashMap<>();
    private static final Set<ResourceLocation> READY_LAST_FRAME = new HashSet<>();

    private static long serverTimeOffset;
    private static long flashUntil;
    private static ResourceLocation flashing;
    private static long denyUntil;
    private static int denySlot = -1;

    private static boolean hudVisible = true;
    private static boolean hudRadial = true;

    /** Identity of the connection the current state belongs to, so a new server starts clean. */
    private static Object connection;

    // ---------------------------------------------------------------- payload handlers

    /** Full state replacement, sent on join, respawn, dimension change and every mutation. */
    public static void onSync(SyncPowersPayload payload) {
        GRANTED.clear();
        GRANTED.addAll(payload.granted());
        COOLDOWNS.clear();
        Map<ResourceLocation, Long> ready = payload.asCooldownMap();
        for (Map.Entry<ResourceLocation, Long> entry : ready.entrySet()) {
            long readyAt = entry.getValue();
            // The server only stores the ready tick, so reconstruct the window from the ability's
            // own cooldown length. That is what makes a sweep survive a relog at the right fill.
            int length = AbilityRegistry.get(entry.getKey()).map(Ability::cooldownTicks).orElse(0);
            COOLDOWNS.put(entry.getKey(), new long[] {readyAt - length, readyAt});
        }
        serverTimeOffset = payload.serverGameTime() - clientGameTime();
        READY_LAST_FRAME.clear();
        for (ResourceLocation id : GRANTED) {
            if (isReady(id)) {
                READY_LAST_FRAME.add(id);
            }
        }
    }

    /** One slot changed: either it just fired, or the server is correcting a wrong prediction. */
    public static void onCooldown(CooldownStartPayload payload) {
        COOLDOWNS.put(payload.abilityId(), new long[] {payload.startedAt(), payload.readyAt()});
        serverTimeOffset = payload.serverGameTime() - clientGameTime();
        if (payload.startedAt() == payload.serverGameTime() && payload.readyAt() > payload.serverGameTime()) {
            flashing = payload.abilityId();
            flashUntil = now() + FLASH_TICKS;
            READY_LAST_FRAME.remove(payload.abilityId());
        }
    }

    /** {@code /power hud <mode>} arrived. */
    public static void onHudMode(PowerHudPayload payload) {
        switch (payload.mode()) {
            case PowerHudPayload.MODE_OFF -> hudVisible = false;
            case PowerHudPayload.MODE_ON -> hudVisible = true;
            case PowerHudPayload.MODE_RADIAL -> {
                hudVisible = true;
                hudRadial = true;
            }
            case PowerHudPayload.MODE_LINEAR -> {
                hudVisible = true;
                hudRadial = false;
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- keybind path

    /**
     * A slot key was pressed. Sends the use packet when the client believes the slot is ready and
     * predicts the cooldown locally so the sweep starts on the same frame; a wrong guess is
     * corrected by the {@link CooldownStartPayload} that comes back.
     */
    public static void pressSlot(int slot) {
        if (slot < 0 || slot >= GRANTED.size()) {
            return;
        }
        ResourceLocation id = GRANTED.get(slot);
        if (!isReady(id)) {
            denySlot = slot;
            denyUntil = now() + DENY_SHAKE_TICKS;
            return;
        }
        Ability ability = AbilityRegistry.get(id).orElse(null);
        if (ability == null) {
            return;
        }
        long start = now();
        COOLDOWNS.put(id, new long[] {start, start + ability.cooldownTicks()});
        flashing = id;
        flashUntil = start + FLASH_TICKS;
        READY_LAST_FRAME.remove(id);
        Payloads.sendToServer(new UseAbilityPayload(id));
    }

    // ---------------------------------------------------------------- HUD reads

    public static List<ResourceLocation> granted() {
        return List.copyOf(GRANTED);
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static boolean hudRadial() {
        return hudRadial;
    }

    /** The server clock, as best this client knows it. */
    public static long now() {
        return clientGameTime() + serverTimeOffset;
    }

    public static boolean isReady(ResourceLocation id) {
        long[] window = COOLDOWNS.get(id);
        return window == null || now() >= window[1];
    }

    /** How much of the sweep is still greyed out: 1 right after a use, 0 when ready. */
    public static float remainingFraction(ResourceLocation id) {
        long[] window = COOLDOWNS.get(id);
        if (window == null) {
            return 0.0F;
        }
        long started = window[0];
        long readyAt = window[1];
        long length = readyAt - started;
        if (length <= 0L) {
            return 0.0F;
        }
        float left = (readyAt - now()) / (float) length;
        return Math.max(0.0F, Math.min(1.0F, left));
    }

    /** Remaining cooldown in ticks, for the number under the icon. */
    public static int remainingTicks(ResourceLocation id) {
        long[] window = COOLDOWNS.get(id);
        if (window == null) {
            return 0;
        }
        return (int) Math.max(0L, window[1] - now());
    }

    public static boolean isFlashing(ResourceLocation id) {
        return id.equals(flashing) && now() < flashUntil;
    }

    public static boolean isShaking(int slot) {
        return slot == denySlot && now() < denyUntil;
    }

    /**
     * Per-frame housekeeping, driven from the HUD layer so it works on both loaders without a
     * client-tick hook of its own: drops state left over from a previous server, then edge-detects
     * "this slot just became usable" and plays the ready chime.
     *
     * <p>Dropping the state on a new connection matters for more than tidiness - it is what stops
     * a stale row of icons being drawn, and being clickable, after joining a server that does not
     * have this feature at all.
     */
    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        Object current = minecraft.getConnection();
        if (current != connection) {
            boolean hadPrevious = connection != null;
            connection = current;
            if (hadPrevious) {
                reset();
            }
        }
        if (minecraft.player == null) {
            return;
        }
        for (ResourceLocation id : GRANTED) {
            boolean ready = isReady(id);
            if (ready && READY_LAST_FRAME.add(id)) {
                // creator_powers:ui.ability_ready, with a vanilla stand-in for the (impossible, but
                // cheap to guard) case of the registry not having flushed yet.
                SoundEvent chime = PowersFeature.readyChime();
                minecraft.getSoundManager().play(chime != null
                        ? SimpleSoundInstance.forUI(chime, 1.0F, 0.6F)
                        : SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.5F));
            } else if (!ready) {
                READY_LAST_FRAME.remove(id);
            }
        }
    }

    /** Wipes the mirror. Called when the client connects somewhere new. */
    public static void reset() {
        GRANTED.clear();
        COOLDOWNS.clear();
        READY_LAST_FRAME.clear();
        serverTimeOffset = 0L;
        flashing = null;
        flashUntil = 0L;
        denySlot = -1;
        denyUntil = 0L;
    }

    private static long clientGameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private ClientPowers() {
    }
}
