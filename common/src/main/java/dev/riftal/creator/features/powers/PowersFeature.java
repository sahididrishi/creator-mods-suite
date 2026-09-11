package dev.riftal.creator.features.powers;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.registry.Registrar;
import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import dev.riftal.creator.features.powers.client.ClientPowers;
import dev.riftal.creator.features.powers.client.PowersClient;
import dev.riftal.creator.features.powers.command.PowerCommand;
import dev.riftal.creator.features.powers.effect.ActiveEffects;
import dev.riftal.creator.features.powers.net.CooldownStartPayload;
import dev.riftal.creator.features.powers.net.PowerHudPayload;
import dev.riftal.creator.features.powers.net.SyncPowersPayload;
import dev.riftal.creator.features.powers.net.UseAbilityPayload;
import dev.riftal.creator.features.powers.server.PowerManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

import static dev.riftal.creator.Constants.LOG;

/**
 * Power Kit - see {@code plans/03-power-kit.md}.
 *
 * <p>Six keybind abilities (dash, fire burst, ground pound, ender pull, shield dome, mob freeze)
 * with server-authoritative cooldowns and a cooldown row on the HUD.
 *
 * <p>Shape of the feature:
 * <ul>
 *   <li>{@code ability/} - the six abilities and their ordered registry. Server side, pure vanilla.</li>
 *   <li>{@code data/} - the persisted per-player loadout and the pure cooldown arithmetic.</li>
 *   <li>{@code effect/} - transient server state: domes, frozen mobs, dash i-frames, pounds.</li>
 *   <li>{@code net/} - one C2S use packet and three S2C mirrors of the state the HUD needs.</li>
 *   <li>{@code server/} - {@code PowerManager}, the one place that decides whether an ability fires.</li>
 *   <li>{@code command/} - the {@code /power} tree.</li>
 *   <li>{@code client/} - key mappings, the client mirror and the HUD layer. Client only.</li>
 *   <li>{@code mixin/} - the damage gate for the dome and dash i-frames, the join/respawn/logout
 *       hooks the HUD and the effect teardown need, and the server-stopping wipe.</li>
 * </ul>
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_powers/**} and {@code data/creator_powers/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-powers.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/powers/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/powers/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class PowersFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "powers";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_powers";

    /** Attachment path for the per-player loadout: {@code creator_powers:powers}. */
    public static final String ATTACHMENT_PATH = "powers";

    /**
     * Sound event path of the HUD "this slot is ready again" chime, registered as
     * {@code creator_powers:ui.ability_ready} and defined in {@code assets/creator_powers/sounds.json}.
     */
    public static final String READY_SOUND_PATH = "ui.ability_ready";

    private static RegistryEntry<SoundEvent> readyChime;

    /** Static twin of {@link Feature#rl(String)}, for the many static helpers in this feature. */
    public static ResourceLocation res(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    /** Full texture path for a GUI sprite: {@code creator_powers:textures/gui/<path>.png}. */
    public static ResourceLocation guiTexture(String path) {
        return res("textures/gui/" + path + ".png");
    }

    /**
     * The registered ready chime, or {@code null} before the registry flush (and on a server that
     * never got one). The client falls back to a vanilla sound rather than crashing.
     */
    public static SoundEvent readyChime() {
        return readyChime != null && readyChime.isBound() ? readyChime.get() : null;
    }

    /** Owner tag for every scheduled task this feature queues, so it can cancel exactly its own. */
    public static ResourceLocation taskTag() {
        return res("abilities");
    }

    /** True when the feature is switched on in {@code config/creatormods.json}. */
    public static boolean enabled() {
        return CreatorMods.isEnabled(ID);
    }

    /**
     * Damage gate for {@code PowersServerPlayerMixin}: true when a Shield Dome or a dash i-frame
     * window should swallow this hit. Lives here so the mixin stays a three-line delegation.
     */
    public static boolean shouldCancelDamage(ServerPlayer player, DamageSource source) {
        if (!enabled()) {
            return false;
        }
        return ActiveEffects.shouldCancelDamage(player, source, player.level().getGameTime());
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        AbilityRegistry.bootstrap();
        PowerManager.registerData();

        // The one registry entry this feature owns: the HUD chime that fires when a slot refills.
        // Declared on both sides - the client plays it, and a dedicated server still has to know the
        // id exists so the sound is not "unregistered" in a resource-pack check.
        Registrar<SoundEvent> sounds = registrar(Registries.SOUND_EVENT);
        readyChime = sounds.register(READY_SOUND_PATH,
                () -> SoundEvent.createVariableRangeEvent(res(READY_SOUND_PATH)));

        // C2S: the keybind. Never trusted - PowerManager re-checks grant, cooldown and canUse.
        Payloads.registerC2S(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC, (payload, sender) -> {
            if (!enabled()) {
                return;
            }
            PowerManager.handleUse(sender, payload.abilityId());
        });

        // S2C. Registered on BOTH sides, not just in initClient(): a dedicated server has to know
        // the payload types to be allowed to send them, and the handler bodies below only ever
        // execute on a client, so the client-only classes they name are never loaded on a server.
        Payloads.registerS2C(SyncPowersPayload.TYPE, SyncPowersPayload.CODEC,
                payload -> ClientPowers.onSync(payload));
        Payloads.registerS2C(CooldownStartPayload.TYPE, CooldownStartPayload.CODEC,
                payload -> ClientPowers.onCooldown(payload));
        Payloads.registerS2C(PowerHudPayload.TYPE, PowerHudPayload.CODEC,
                payload -> ClientPowers.onHudMode(payload));

        PowerCommand.register();

        // The command registrar is replayed every time the server builds its dispatcher - start-up
        // and every /reload - which is the one loader-neutral "a server is live now" hook core
        // exposes. Use it to arm the per-tick driver. startTicking() is a no-op while the driver is
        // already running, so a /reload mid-take does not double it up and does not drop the dome,
        // the frozen mobs or the ability FX that are in flight; the state wipe belongs to
        // PowersMinecraftServerMixin's server-stopping hook instead.
        CommandHelper.register(dispatcher -> PowerManager.startTicking());

        LOG.info("[powers] {} abilities declared", AbilityRegistry.size());
    }

    @Override
    public void initCommon() {
        // Nothing extra: everything this feature needs is declared in registerContent(), and the
        // per-tick driver is armed when a server actually starts.
    }

    @Override
    public void initClient() {
        PowersClient.init(rl("cooldown_row"));
    }
}
