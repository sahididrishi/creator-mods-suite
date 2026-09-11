package dev.riftal.creator.features.powers.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.data.PlayerPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server to client: the whole loadout, so the HUD can be rebuilt from scratch.
 *
 * <p>Sent on join, on respawn, after a dimension change and after every {@code /power} mutation.
 * {@code granted} and {@code readyAt} are parallel lists in slot order - two short lists beat a map
 * codec here because the client wants them in order anyway.
 *
 * @param serverGameTime the server's clock at send time; the client stores the offset against its
 *                       own level clock so the cooldown sweeps are drawn against the server tick
 *                       and never drift
 */
public record SyncPowersPayload(List<ResourceLocation> granted, List<Long> readyAt, long serverGameTime)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPowersPayload> TYPE =
            Payloads.type(PowersFeature.NAMESPACE, "sync_powers");

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPowersPayload> CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(PlayerPowers.MAX_SLOTS)),
                    SyncPowersPayload::granted,
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(PlayerPowers.MAX_SLOTS)),
                    SyncPowersPayload::readyAt,
                    ByteBufCodecs.VAR_LONG, SyncPowersPayload::serverGameTime,
                    SyncPowersPayload::new);

    public SyncPowersPayload {
        granted = List.copyOf(granted);
        readyAt = List.copyOf(readyAt);
    }

    /** Builds the wire form of one player's state. */
    public static SyncPowersPayload of(PlayerPowers powers, long gameTime) {
        List<ResourceLocation> granted = powers.granted();
        List<Long> ready = new ArrayList<>(granted.size());
        for (ResourceLocation id : granted) {
            ready.add(powers.readyTick(id, gameTime));
        }
        return new SyncPowersPayload(granted, ready, gameTime);
    }

    /**
     * Rebuilds a cooldown map from the parallel lists, tolerating a mismatched length rather than
     * throwing on a malformed packet.
     */
    public Map<ResourceLocation, Long> asCooldownMap() {
        Map<ResourceLocation, Long> map = new LinkedHashMap<>();
        int count = Math.min(granted.size(), readyAt.size());
        for (int i = 0; i < count; i++) {
            map.put(granted.get(i), readyAt.get(i));
        }
        return map;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
