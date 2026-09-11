package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.ToolkitState;
import dev.riftal.creator.features.toolkit.cam.CameraBookmark;
import dev.riftal.creator.features.toolkit.cam.CameraManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.StringJoiner;

/** {@code /toolkit cam save|go|list|del} - the shot list. */
public final class CamCommands {

    /** The {@code cam} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("cam")
                .then(CommandHelper.literal("save")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .executes(ctx -> save(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(CommandHelper.literal("go")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .suggests(ToolkitSuggestions.CAMERAS)
                                .executes(ctx -> go(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))
                                // The documented shape is `cam go <name> [glideTicks]`. The glide
                                // itself is a stretch goal (it needs a Camera#setup mixin), so the
                                // argument is accepted and the cut is an instant snap - the plan's
                                // own MVP line is "instant snap". Erroring on a documented argument
                                // on camera is the worse failure.
                                .then(CommandHelper.arg("glideTicks",
                                                IntegerArgumentType.integer(0, 200))
                                        .executes(ctx -> go(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(CommandHelper.literal("del")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .suggests(ToolkitSuggestions.CAMERAS)
                                .executes(ctx -> del(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(CommandHelper.literal("list").executes(ctx -> list(ctx.getSource())));
    }

    private static int save(CommandSourceStack source, String name) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        CameraBookmark bookmark = CameraManager.capture(name, player);
        ToolkitState.get(server).putCamera(bookmark);
        return CommandHelper.success(source, ToolkitText.of("cam.saved", name));
    }

    private static int go(CommandSourceStack source, String name) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        CameraBookmark bookmark = ToolkitState.get(server).camera(name);
        if (bookmark == null) {
            return CommandHelper.error(source, ToolkitText.of("cam.missing", name));
        }
        if (!CameraManager.go(server, player, bookmark)) {
            return CommandHelper.error(source, ToolkitText.of("cam.failed", name));
        }
        // Deliberately silent on success: a camera cut should leave nothing on screen.
        return 1;
    }

    private static int del(CommandSourceStack source, String name) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        if (!ToolkitState.get(server).removeCamera(name)) {
            return CommandHelper.error(source, ToolkitText.of("cam.missing", name));
        }
        return CommandHelper.success(source, ToolkitText.of("cam.gone", name));
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        Collection<CameraBookmark> cameras = ToolkitState.get(server).cameras();
        if (cameras.isEmpty()) {
            return CommandHelper.feedback(source, ToolkitText.of("cam.list.empty"));
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (CameraBookmark bookmark : cameras) {
            joiner.add(bookmark.describe());
        }
        return CommandHelper.feedback(source, ToolkitText.of("cam.list", joiner.toString()));
    }

    private CamCommands() {
    }
}
