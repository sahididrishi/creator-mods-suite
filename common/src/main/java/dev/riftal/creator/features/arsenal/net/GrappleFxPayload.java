package dev.riftal.creator.features.arsenal.net;

import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: "the hook just did a thing, here".
 *
 * <p>The hook's state already syncs through {@code SynchedEntityData}, but that only lands on the
 * next tracker update - which is a visible beat late for the bite. This payload lets every tracking
 * client play the bite and return effects on the exact tick they happen. It is cosmetic only:
 * dropping it changes nothing about the mechanic.
 *
 * <p>The codec is built from {@code ByteBufCodecs} primitives only, so it is declared over a bare
 * {@link ByteBuf} - it still satisfies {@code Payloads.registerS2C}, which takes a
 * {@code StreamCodec<? super RegistryFriendlyByteBuf, T>}, and it round trips in a plain unit test.
 */
public record GrappleFxPayload(int hookEntityId, byte state, float x, float y, float z)
        implements CustomPacketPayload {

    /** The hook bit something. */
    public static final byte STATE_BITE = 1;

    /** The hook let go and is flying home. */
    public static final byte STATE_RETURN = 2;

    public static final Type<GrappleFxPayload> TYPE = Payloads.type(ArsenalFeature.NAMESPACE, "grapple_fx");

    public static final StreamCodec<ByteBuf, GrappleFxPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GrappleFxPayload::hookEntityId,
                    ByteBufCodecs.BYTE, GrappleFxPayload::state,
                    ByteBufCodecs.FLOAT, GrappleFxPayload::x,
                    ByteBufCodecs.FLOAT, GrappleFxPayload::y,
                    ByteBufCodecs.FLOAT, GrappleFxPayload::z,
                    GrappleFxPayload::new);

    /** Convenience factory from an entity and a world position. */
    public static GrappleFxPayload of(Entity hook, byte state, Vec3 pos) {
        return new GrappleFxPayload(hook.getId(), state, (float) pos.x, (float) pos.y, (float) pos.z);
    }

    /** The position the effect happened at. */
    public Vec3 pos() {
        return new Vec3(this.x, this.y, this.z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
