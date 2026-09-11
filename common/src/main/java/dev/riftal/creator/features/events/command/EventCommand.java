package dev.riftal.creator.features.events.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.util.MathUtil;
import dev.riftal.creator.features.events.EventManager;
import dev.riftal.creator.features.events.EventsFeature;
import dev.riftal.creator.features.events.api.EventPhase;
import dev.riftal.creator.features.events.api.EventRegistry;
import dev.riftal.creator.features.events.api.StopReason;
import dev.riftal.creator.features.events.api.WorldEvent;
import dev.riftal.creator.features.events.util.EventOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * {@code /event} - the director's whole console.
 *
 * <p>{@code list} and {@code status} are readable by anyone; everything that changes the world
 * needs op level 2, so the root node itself is ungated and each verb carries its own requirement.
 */
public final class EventCommand {

    private static final SuggestionProvider<CommandSourceStack> EVENT_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), builder);

    /** Builds the tree. Called from {@code EventsFeature#registerContent()}. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.literal("event")
                .then(CommandHelper.op("start")
                        .then(CommandHelper.arg("event", StringArgumentType.word())
                                .suggests(EVENT_IDS)
                                .executes(ctx -> start(ctx, null, ""))
                                // The coordinate branch is declared first on purpose: Brigadier
                                // tries argument children in insertion order, so a greedy string
                                // declared first would swallow "~ ~ ~" as an option list. A real
                                // option list fails to parse as a coordinate and falls through.
                                .then(CommandHelper.arg("origin", Vec3Argument.vec3())
                                        .executes(ctx -> start(ctx,
                                                Vec3Argument.getVec3(ctx, "origin"), ""))
                                        .then(CommandHelper.arg("options", StringArgumentType.greedyString())
                                                .executes(ctx -> start(ctx,
                                                        Vec3Argument.getVec3(ctx, "origin"),
                                                        StringArgumentType.getString(ctx, "options")))))
                                .then(CommandHelper.arg("options", StringArgumentType.greedyString())
                                        .executes(ctx -> start(ctx, null,
                                                StringArgumentType.getString(ctx, "options"))))))
                .then(CommandHelper.op("stop").executes(EventCommand::stop))
                .then(CommandHelper.op("skip").executes(EventCommand::skip))
                .then(CommandHelper.op("timer")
                        .then(CommandHelper.arg("seconds", IntegerArgumentType.integer(1, 86400))
                                .executes(ctx -> timer(ctx,
                                        IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(CommandHelper.op("reload").executes(EventCommand::reload))
                .then(CommandHelper.op("hud")
                        .then(CommandHelper.arg("visible", BoolArgumentType.bool())
                                .executes(ctx -> hud(ctx, BoolArgumentType.getBool(ctx, "visible")))))
                .then(CommandHelper.literal("list").executes(EventCommand::list))
                .then(CommandHelper.literal("status").executes(EventCommand::status));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, Vec3 explicitOrigin, String options) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "event");
        if (!EventRegistry.contains(id)) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_events.unknown", id));
        }
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        Vec3 origin = explicitOrigin != null ? explicitOrigin : defaultOrigin(source, player);
        UUID starter = player == null ? null : player.getUUID();

        boolean started = EventManager.start(id, source.getServer(), level, origin, starter,
                EventOptions.parse(options));
        if (!started) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_events.unknown", id));
        }
        return CommandHelper.success(source,
                Component.translatable("commands.creator_events.started", displayName(id)));
    }

    private static Vec3 defaultOrigin(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            return source.getPosition();
        }
        HitResult hit = player.pick(64.0D, 0.0F, false);
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }
        return player.position().add(player.getLookAngle().scale(20.0D));
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = EventManager.activeId();
        if (!EventManager.stop(StopReason.STOPPED)) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_events.none"));
        }
        return CommandHelper.success(source,
                Component.translatable("commands.creator_events.stopped", displayName(id)));
    }

    private static int skip(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        WorldEvent active = EventManager.active();
        if (active == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_events.none"));
        }
        String id = active.id();
        String phase = EventManager.skip();
        if (phase.isEmpty()) {
            return CommandHelper.success(source,
                    Component.translatable("commands.creator_events.skipped_end", displayName(id)));
        }
        return CommandHelper.success(source, Component.translatable(
                "commands.creator_events.skipped", displayName(id), phaseName(id, phase)));
    }

    private static int timer(CommandContext<CommandSourceStack> ctx, int seconds) {
        CommandSourceStack source = ctx.getSource();
        if (!EventManager.timer(seconds)) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_events.none"));
        }
        return CommandHelper.success(source,
                Component.translatable("commands.creator_events.timer", seconds));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EventsFeature.reloadData(source.getServer());
        return CommandHelper.success(source,
                Component.translatable("commands.creator_events.reloaded"));
    }

    private static int hud(CommandContext<CommandSourceStack> ctx, boolean visible) {
        EventManager.setHudVisible(visible);
        return CommandHelper.success(ctx.getSource(), Component.translatable(
                visible ? "commands.creator_events.hud_on" : "commands.creator_events.hud_off"));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        return CommandHelper.feedback(ctx.getSource(), Component.translatable(
                "commands.creator_events.list", String.join(", ", EventRegistry.ids())));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        WorldEvent active = EventManager.active();
        if (active == null) {
            String last = EventManager.lastEventId();
            return CommandHelper.feedback(source, last.isEmpty()
                    ? Component.translatable("commands.creator_events.idle")
                    : Component.translatable("commands.creator_events.idle_last", displayName(last)));
        }
        EventPhase phase = EventManager.currentPhase();
        String phaseId = phase == null ? "" : phase.id();
        Component line = Component.translatable("commands.creator_events.status",
                        displayName(active.id()),
                        phaseName(active.id(), phaseId),
                        MathUtil.formatTicks(EventManager.phaseTick()))
                .withStyle(ChatFormatting.AQUA);
        if (active.waveTotal() > 0) {
            line = line.copy().append(Component.translatable("commands.creator_events.status_waves",
                    active.wave(), active.waveTotal(), active.alive()));
        }
        return CommandHelper.feedback(source, line);
    }

    private static Component displayName(String id) {
        return id == null || id.isEmpty()
                ? Component.translatable("commands.creator_events.none")
                : Component.translatable("event.creator_events." + id);
    }

    private static Component phaseName(String eventId, String phaseId) {
        return phaseId.isEmpty()
                ? Component.empty()
                : Component.translatable("phase.creator_events." + eventId + "." + phaseId);
    }

    private EventCommand() {
    }
}
