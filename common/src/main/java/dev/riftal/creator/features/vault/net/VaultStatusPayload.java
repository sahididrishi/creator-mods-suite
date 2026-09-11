package dev.riftal.creator.features.vault.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.vault.VaultFeature;
import dev.riftal.creator.features.vault.block.AltarState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: everything the vault HUD shows about the altar a player is standing next to.
 *
 * <p>Sent by {@code CursedAltarBlockEntity} to players inside
 * {@code AltarStateMachine.STATUS_BROADCAST_RANGE}. The blockstate already tells the client which
 * {@link AltarState} the altar is in; this payload carries the parts the client cannot see - the
 * charge counter, how many chests are wired to the altar, the Keeper's health, and the player's own
 * (server-side, unsynced) key counter.
 *
 * @param altarPos      altar block position, purely so the client can tell two altars apart
 * @param stateId       {@link AltarState#ordinal()}
 * @param chargeTicks   0..{@code CHARGE_TICKS}
 * @param sealedChests  how many Sealed Chests this altar will open
 * @param keeperHealth  0..1 fraction, or a negative value when no Keeper is bound
 * @param keysUsed      how many keys the receiving player has spent on altars, ever
 */
public record VaultStatusPayload(BlockPos altarPos, byte stateId, int chargeTicks,
                                 int sealedChests, float keeperHealth, int keysUsed)
        implements CustomPacketPayload {

    public static final Type<VaultStatusPayload> TYPE =
            Payloads.type(VaultFeature.NAMESPACE, "vault_status");

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultStatusPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, VaultStatusPayload::altarPos,
                    ByteBufCodecs.BYTE, VaultStatusPayload::stateId,
                    ByteBufCodecs.VAR_INT, VaultStatusPayload::chargeTicks,
                    ByteBufCodecs.VAR_INT, VaultStatusPayload::sealedChests,
                    ByteBufCodecs.FLOAT, VaultStatusPayload::keeperHealth,
                    ByteBufCodecs.VAR_INT, VaultStatusPayload::keysUsed,
                    VaultStatusPayload::new);

    /** Convenience factory that takes the enum instead of its ordinal. */
    public static VaultStatusPayload of(BlockPos pos, AltarState state, int chargeTicks,
                                        int sealedChests, float keeperHealth, int keysUsed) {
        return new VaultStatusPayload(pos, (byte) state.ordinal(), chargeTicks, sealedChests,
                keeperHealth, keysUsed);
    }

    /** The state this payload describes, tolerant of an out-of-range ordinal. */
    public AltarState state() {
        AltarState[] values = AltarState.values();
        int index = this.stateId & 0xFF;
        return index >= 0 && index < values.length ? values[index] : AltarState.SEALED;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
