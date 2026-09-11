package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.take.Mark;
import dev.riftal.creator.features.toolkit.take.TakeFormat;
import dev.riftal.creator.features.toolkit.take.TakeLog;
import dev.riftal.creator.features.toolkit.take.TakeManager;
import dev.riftal.creator.features.toolkit.take.TakeState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** {@code /toolkit take start|stop|mark|status|set}. */
public final class TakeCommands {

    /** The {@code take} subtree. {@code status} is readable by anyone; the rest needs op. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.literal("take")
                .then(CommandHelper.op("start").executes(ctx -> start(ctx.getSource())))
                .then(CommandHelper.op("stop").executes(ctx -> stop(ctx.getSource())))
                .then(CommandHelper.op("mark")
                        .executes(ctx -> mark(ctx.getSource(), ""))
                        .then(CommandHelper.arg("label", StringArgumentType.greedyString())
                                .executes(ctx -> mark(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "label")))))
                .then(CommandHelper.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(CommandHelper.op("set")
                        .then(CommandHelper.arg("number", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> set(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "number")))));
    }

    private static int start(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        TakeState state = TakeManager.start(server);
        if (state == null) {
            return CommandHelper.error(source, ToolkitText.of("take.already",
                    TakeFormat.takeNumber(TakeManager.state().number())));
        }
        return CommandHelper.success(source,
                ToolkitText.of("take.started", TakeFormat.takeNumber(state.number())));
    }

    private static int stop(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        TakeState state = TakeManager.stop(server);
        if (state == null) {
            return CommandHelper.error(source, ToolkitText.of("take.none"));
        }
        TakeLog log = TakeManager.log();
        return CommandHelper.success(source, ToolkitText.of("take.stopped",
                TakeFormat.takeNumber(state.number()),
                TakeFormat.formatRta(state.stoppedRtaMs()),
                state.marks().size(),
                log == null ? "-" : log.fileName()));
    }

    private static int mark(CommandSourceStack source, String label) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        Mark mark = TakeManager.mark(server, source.getTextName(), label);
        if (mark == null) {
            return CommandHelper.error(source, ToolkitText.of("take.none"));
        }
        return CommandHelper.success(source, ToolkitText.of("take.mark",
                mark.index(), TakeFormat.formatRta(mark.rtaMillis())));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        TakeState state = TakeManager.state();
        if (state.running()) {
            return CommandHelper.feedback(source, ToolkitText.of("take.status.running",
                    TakeFormat.takeNumber(state.number()),
                    TakeFormat.formatRta(state.rtaMillis(System.currentTimeMillis())),
                    state.marks().size()));
        }
        return CommandHelper.feedback(source, ToolkitText.of("take.status.idle",
                TakeFormat.takeNumber(TakeManager.nextNumber(server))));
    }

    private static int set(CommandSourceStack source, int number) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        TakeManager.setNextNumber(server, number);
        return CommandHelper.success(source,
                ToolkitText.of("take.set", TakeFormat.takeNumber(number)));
    }

    private TakeCommands() {
    }
}
