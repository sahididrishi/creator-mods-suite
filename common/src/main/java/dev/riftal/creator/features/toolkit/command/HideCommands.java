package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.command.SilentMode;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.net.HideStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /toolkit hide hud|chat|nametags|commands <on|off>} - the clean-frame switches.
 *
 * <p>The three visual switches are per player and only ever affect the player who typed the command;
 * an op can never blank a crew member's screen from across the map. {@code commands} is the
 * server-wide silent mode that moves all feedback to the action bar.
 */
public final class HideCommands {

    /** What each player has hidden. Purely a mirror so a second command can flip one switch. */
    private static final Map<UUID, HideStatePayload> STATE = new HashMap<>();

    /** The {@code hide} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("hide")
                .then(CommandHelper.literal("hud")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.HUD,
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("chat")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.CHAT,
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("nametags")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.NAMETAGS,
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("commands")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> silent(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))));
    }

    /** Drops the per-player mirror; the client resets its own flags on disconnect. */
    public static void reset() {
        STATE.clear();
    }

    private enum Part {
        HUD, CHAT, NAMETAGS
    }

    private static int set(CommandSourceStack source, Part part, boolean value) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        HideStatePayload current = STATE.getOrDefault(player.getUUID(),
                new HideStatePayload(false, false, false));
        HideStatePayload updated = switch (part) {
            case HUD -> new HideStatePayload(value, current.chat(), current.nametags());
            case CHAT -> new HideStatePayload(current.hud(), value, current.nametags());
            case NAMETAGS -> new HideStatePayload(current.hud(), current.chat(), value);
        };
        STATE.put(player.getUUID(), updated);
        ToolkitNet.send(player, updated);
        return CommandHelper.success(source, ToolkitText.of(
                switch (part) {
                    case HUD -> "hide.hud";
                    case CHAT -> "hide.chat";
                    case NAMETAGS -> "hide.nametags";
                },
                ToolkitText.onOff(value)));
    }

    private static int silent(CommandSourceStack source, boolean value) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        // Announce the change while output is still visible, then flip.
        if (value) {
            Component notice = ToolkitText.of("hide.commands.on");
            source.sendSuccess(() -> notice, false);
            SilentMode.set(server, true);
            return 1;
        }
        SilentMode.set(server, false);
        return CommandHelper.success(source, ToolkitText.of("hide.commands.off"));
    }

    private HideCommands() {
    }
}
