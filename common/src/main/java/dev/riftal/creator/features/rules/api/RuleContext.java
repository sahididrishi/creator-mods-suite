package dev.riftal.creator.features.rules.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Everything a {@link Rule} is allowed to reach: the running server, its levels, its players, a
 * shared unseeded {@link RandomSource} for moment-to-moment chaos and {@link #worldSeed()} for the
 * "same world, same result" rules (notably {@code random_drops}) that must be reproducible across
 * restarts.
 *
 * <p>Created once per server session by {@code RuleManager}; never cached by a rule.
 */
public final class RuleContext {

    /** Mixed into the world seed so our derived seed is not the world seed itself. */
    private static final long SEED_SALT = 0x52554C45L;

    private final MinecraftServer server;
    private final RandomSource random = RandomSource.create();

    public RuleContext(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerLevel overworld() {
        return server.overworld();
    }

    public List<ServerPlayer> players() {
        return server.getPlayerList().getPlayers();
    }

    /** Shared, unseeded. Use for effects that may differ between runs. */
    public RandomSource random() {
        return random;
    }

    /** Stable for the lifetime of the world. Same world, same value, every launch. */
    public long worldSeed() {
        ServerLevel level = overworld();
        return (level == null ? 0L : level.getSeed()) ^ SEED_SALT;
    }

    /** Ticks since the world was created. Used by the rules that keep their own timers. */
    public long gameTime() {
        ServerLevel level = overworld();
        return level == null ? 0L : level.getGameTime();
    }
}
