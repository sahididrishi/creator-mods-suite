package dev.riftal.creator.features.events.api;

import dev.riftal.creator.features.events.util.EventOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Everything a {@link WorldEvent} is allowed to reach: one level, one origin, the starter, options. */
public final class EventContext {

    private final MinecraftServer server;
    private final ServerLevel level;
    private final Vec3 origin;
    private final UUID starterId;
    private final EventOptions options;

    public EventContext(MinecraftServer server, ServerLevel level, Vec3 origin,
                        UUID starterId, EventOptions options) {
        this.server = server;
        this.level = level;
        this.origin = origin;
        this.starterId = starterId;
        this.options = options == null ? EventOptions.empty() : options;
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerLevel level() {
        return level;
    }

    /** Where the event is anchored: the meteor target, the siege centre, the void column. */
    public Vec3 origin() {
        return origin;
    }

    public BlockPos originPos() {
        return BlockPos.containing(origin);
    }

    public EventOptions options() {
        return options;
    }

    public RandomSource random() {
        return level.getRandom();
    }

    /** The player who typed the command, if they are still online and in this level. */
    public ServerPlayer starter() {
        if (starterId == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(starterId);
        return player != null && player.level() == level ? player : null;
    }

    public UUID starterId() {
        return starterId;
    }

    /** Everyone in the event's level. Events never reach across dimensions. */
    public List<ServerPlayer> players() {
        return level.players();
    }

    /**
     * The best anchor for "where the action is": the starter if they are online, otherwise the
     * first player in the level, otherwise the frozen origin.
     */
    public Vec3 focus() {
        ServerPlayer starter = starter();
        if (starter != null) {
            return starter.position();
        }
        List<ServerPlayer> players = players();
        return players.isEmpty() ? origin : players.get(0).position();
    }
}
