package dev.riftal.creator.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Permission-gated Brigadier building plus silent-mode-aware feedback.
 *
 * <pre>{@code
 * CommandHelper.register(dispatcher -> dispatcher.register(
 *         CommandHelper.op("toolkit")
 *                 .then(CommandHelper.literal("take")
 *                         .executes(ctx -> CommandHelper.success(ctx.getSource(),
 *                                 Component.literal("take started"))))));
 * }</pre>
 *
 * <p>Never call {@code source.sendSuccess(msg, true)} directly - the {@code true} broadcasts
 * "[Player: ...]" to every other op, which is exactly the chat noise this suite exists to avoid.
 * Use {@link #feedback}, {@link #success} or {@link #error}.
 */
public final class CommandHelper {

    /** Op level 2: the "cheats" level a recording creator has in single player. */
    public static final int OP_LEVEL = 2;

    private static final List<Consumer<CommandDispatcher<CommandSourceStack>>> REGISTRARS = new ArrayList<>();

    /** A literal node that requires op level 2. */
    public static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return op(name, OP_LEVEL);
    }

    /** A literal node that requires the given permission level. */
    public static LiteralArgumentBuilder<CommandSourceStack> op(String name, int level) {
        return Commands.literal(name).requires(src -> src.hasPermission(level));
    }

    /** An ungated literal node, for sub-commands under an already gated root. */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return Commands.literal(name);
    }

    /** An argument node. Shorthand for {@link Commands#argument}. */
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> arg(String name, ArgumentType<T> type) {
        return Commands.argument(name, type);
    }

    /**
     * Queues a command tree for registration on both loaders. Call from
     * {@code Feature#registerContent()}; the registrar is replayed every time the server builds its
     * dispatcher (start-up and {@code /reload}).
     */
    public static void register(Consumer<CommandDispatcher<CommandSourceStack>> registrar) {
        REGISTRARS.add(registrar);
    }

    /** Loader glue only: every queued command registrar. */
    public static List<Consumer<CommandDispatcher<CommandSourceStack>>> registrars() {
        return List.copyOf(REGISTRARS);
    }

    /** Loader glue only: replays every registrar into a freshly built dispatcher. */
    public static void applyAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (Consumer<CommandDispatcher<CommandSourceStack>> registrar : registrars()) {
            registrar.accept(dispatcher);
        }
    }

    /**
     * Replies to the command source. Chat when silent mode is off, action bar when it is on. Never
     * broadcasts to other ops.
     *
     * @return {@link Command#SINGLE_SUCCESS}, so it can be the whole body of an {@code executes}
     */
    public static int feedback(CommandSourceStack source, Component message) {
        MinecraftServer server = source.getServer();
        if (SilentMode.isSilent(server)) {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                player.displayClientMessage(message, true);
            }
        } else {
            source.sendSuccess(() -> message, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** {@link #feedback} in green. */
    public static int success(CommandSourceStack source, Component message) {
        return feedback(source, message.copy().withStyle(ChatFormatting.GREEN));
    }

    /** Red failure reply. Returns 0 so Brigadier records the command as failed. */
    public static int error(CommandSourceStack source, Component message) {
        MinecraftServer server = source.getServer();
        if (SilentMode.isSilent(server)) {
            ServerPlayer player = source.getPlayer();
            if (player != null) {
                player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
            }
        } else {
            source.sendFailure(message);
        }
        return 0;
    }

    private CommandHelper() {
    }
}
