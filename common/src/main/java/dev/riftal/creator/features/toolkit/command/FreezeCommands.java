package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.freeze.FreezeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** {@code /toolkit freeze mobs|players|all <on|off>} and {@code /toolkit freeze status}. */
public final class FreezeCommands {

    /** The {@code freeze} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("freeze")
                .then(CommandHelper.literal("mobs")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> mobs(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("players")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> players(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("all")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> all(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("status").executes(ctx -> status(ctx.getSource())));
    }

    private static int mobs(CommandSourceStack source, boolean frozen) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        FreezeManager.setMobs(server, frozen);
        return CommandHelper.success(source,
                ToolkitText.of(frozen ? "freeze.mobs.on" : "freeze.mobs.off"));
    }

    private static int players(CommandSourceStack source, boolean frozen) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        FreezeManager.setPlayers(server, frozen);
        if (frozen) {
            return CommandHelper.success(source,
                    ToolkitText.of("freeze.players.on", FreezeManager.lockedCount()));
        }
        return CommandHelper.success(source, ToolkitText.of("freeze.players.off"));
    }

    private static int all(CommandSourceStack source, boolean frozen) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        FreezeManager.setAll(server, frozen);
        return CommandHelper.success(source,
                ToolkitText.of(frozen ? "freeze.all.on" : "freeze.all.off"));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        return CommandHelper.feedback(source, ToolkitText.of("freeze.status",
                ToolkitText.onOff(FreezeManager.mobsFrozen()),
                ToolkitText.onOff(FreezeManager.playersFrozen()),
                ToolkitText.onOff(FreezeManager.allFrozen(server))));
    }

    private FreezeCommands() {
    }
}
