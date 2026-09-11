package dev.riftal.creator.core.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

/** On-screen title cards, driven from the server. Used for phase banners and event announcements. */
public final class Titles {

    /** Title plus subtitle with explicit timing, all in ticks. */
    public static void show(ServerPlayer player, Component title, Component subtitle,
                            int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Title plus subtitle with vanilla default timing (10 / 70 / 20). */
    public static void show(ServerPlayer player, Component title, Component subtitle) {
        show(player, title, subtitle, 10, 70, 20);
    }

    private Titles() {
    }
}
