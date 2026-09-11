package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.cheat.CheatManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** {@code /toolkit cheat god|fly|heal|clear} - the creator conveniences, per player. */
public final class CheatCommands {

    /** The {@code cheat} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("cheat")
                .then(CommandHelper.literal("god")
                        .executes(ctx -> god(ctx.getSource(), null))
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> god(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("fly")
                        .executes(ctx -> fly(ctx.getSource(), null))
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> fly(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("heal").executes(ctx -> heal(ctx.getSource())))
                .then(CommandHelper.literal("clear").executes(ctx -> clear(ctx.getSource())));
    }

    private static int god(CommandSourceStack source, Boolean requested) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        boolean value = requested == null ? !CheatManager.get(player).god() : requested;
        CheatManager.god(player, value);
        return CommandHelper.success(source,
                ToolkitText.of("cheat.god", ToolkitText.onOff(value)));
    }

    private static int fly(CommandSourceStack source, Boolean requested) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        boolean value = requested == null ? !CheatManager.get(player).fly() : requested;
        CheatManager.fly(player, value);
        return CommandHelper.success(source,
                ToolkitText.of("cheat.fly", ToolkitText.onOff(value)));
    }

    private static int heal(CommandSourceStack source) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        CheatManager.heal(player);
        return CommandHelper.success(source, ToolkitText.of("cheat.heal"));
    }

    private static int clear(CommandSourceStack source) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        CheatManager.clearInventory(player);
        return CommandHelper.success(source, ToolkitText.of("cheat.clear"));
    }

    private CheatCommands() {
    }
}
