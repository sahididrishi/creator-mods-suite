package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitDimensions;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.ToolkitState;
import dev.riftal.creator.features.toolkit.arena.ArenaManager;
import dev.riftal.creator.features.toolkit.arena.ArenaSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.StringJoiner;

/** {@code /toolkit arena save|reset|list|delete}. */
public final class ArenaCommands {

    /** Half-width of the box {@code /toolkit arena save <name>} captures when no corners are given. */
    private static final int QUICK_RADIUS = 12;

    /** How far below the director the quick box reaches. */
    private static final int QUICK_DOWN = 2;

    /** How far above the director the quick box reaches. */
    private static final int QUICK_UP = 6;

    /** The {@code arena} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("arena")
                .then(CommandHelper.literal("save")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .executes(ctx -> quickSave(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))
                                .then(CommandHelper.arg("from", BlockPosArgument.blockPos())
                                        .then(CommandHelper.arg("to", BlockPosArgument.blockPos())
                                                .executes(ctx -> save(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        BlockPosArgument.getBlockPos(ctx, "from"),
                                                        BlockPosArgument.getBlockPos(ctx, "to")))))))
                .then(CommandHelper.literal("reset")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .suggests(ToolkitSuggestions.ARENAS)
                                .executes(ctx -> reset(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(CommandHelper.literal("delete")
                        .then(CommandHelper.arg("name", StringArgumentType.word())
                                .suggests(ToolkitSuggestions.ARENAS)
                                .executes(ctx -> delete(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(CommandHelper.literal("list").executes(ctx -> list(ctx.getSource())));
    }

    private static int quickSave(CommandSourceStack source, String name) {
        BlockPos centre = BlockPos.containing(source.getPosition());
        BlockPos from = centre.offset(-QUICK_RADIUS, -QUICK_DOWN, -QUICK_RADIUS);
        BlockPos to = centre.offset(QUICK_RADIUS, QUICK_UP, QUICK_RADIUS);
        return save(source, name, from, to);
    }

    private static int save(CommandSourceStack source, String name, BlockPos from, BlockPos to) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        ServerLevel level = source.getLevel();
        ArenaManager.SaveResult result = ArenaManager.save(level, name, from, to);
        if (!result.ok()) {
            return CommandHelper.error(source, ToolkitText.of("arena.too_big", result.error()));
        }
        ToolkitState.get(server).putArena(result.snapshot());
        return CommandHelper.success(source, ToolkitText.of("arena.saved", name,
                result.snapshot().sizeText(), result.snapshot().entities().size()));
    }

    private static int reset(CommandSourceStack source, String name) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        ArenaSnapshot snapshot = ToolkitState.get(server).arena(name);
        if (snapshot == null) {
            return CommandHelper.error(source, ToolkitText.of("arena.missing", name));
        }
        ServerLevel level = ToolkitDimensions.level(server, snapshot.dimension());
        if (level == null) {
            return CommandHelper.error(source,
                    ToolkitText.of("arena.wrong_dimension", name, snapshot.dimension().toString()));
        }
        ArenaManager.ResetResult result = ArenaManager.reset(level, snapshot);
        return CommandHelper.success(source, ToolkitText.of("arena.reset", snapshot.name(),
                result.removed(), result.restored()));
    }

    private static int delete(CommandSourceStack source, String name) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        if (!ToolkitState.get(server).removeArena(name)) {
            return CommandHelper.error(source, ToolkitText.of("arena.missing", name));
        }
        return CommandHelper.success(source, ToolkitText.of("arena.deleted", name));
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        Collection<ArenaSnapshot> arenas = ToolkitState.get(server).arenas();
        if (arenas.isEmpty()) {
            return CommandHelper.feedback(source, ToolkitText.of("arena.list.empty"));
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (ArenaSnapshot snapshot : arenas) {
            joiner.add(snapshot.name() + " (" + snapshot.sizeText() + ")");
        }
        return CommandHelper.feedback(source, ToolkitText.of("arena.list", joiner.toString()));
    }

    private ArenaCommands() {
    }
}
