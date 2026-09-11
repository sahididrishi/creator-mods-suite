package dev.riftal.creator.features.toolkit.cam;

import dev.riftal.creator.features.toolkit.ToolkitDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Camera bookmarks: the director stands where the shot should be, runs
 * {@code /toolkit cam save hero}, and from then on {@code /toolkit cam go hero} puts any crew member
 * on that exact spot with that exact yaw and pitch.
 *
 * <p>Bookmarks are stored server side in {@code ToolkitState}, so every crew member on the server
 * shares the same shot list and the list survives a restart.
 */
public final class CameraManager {

    /** Captures a player's position and rotation as a bookmark. */
    public static CameraBookmark capture(String name, ServerPlayer player) {
        return new CameraBookmark(name, player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /**
     * Moves {@code player} onto {@code bookmark}.
     *
     * @return false when the bookmark's dimension no longer exists on this server
     */
    public static boolean go(MinecraftServer server, ServerPlayer player, CameraBookmark bookmark) {
        ServerLevel level = ToolkitDimensions.level(server, bookmark.dimension());
        if (level == null) {
            return false;
        }
        if (!isFinite(bookmark)) {
            return false;
        }
        player.teleportTo(level, bookmark.x(), bookmark.y(), bookmark.z(),
                bookmark.yaw(), bookmark.pitch());
        return true;
    }

    /** Guards against a hand-edited save file full of NaNs. */
    public static boolean isFinite(CameraBookmark bookmark) {
        return Double.isFinite(bookmark.x()) && Double.isFinite(bookmark.y())
                && Double.isFinite(bookmark.z())
                && Float.isFinite(bookmark.yaw()) && Float.isFinite(bookmark.pitch());
    }

    private CameraManager() {
    }
}
