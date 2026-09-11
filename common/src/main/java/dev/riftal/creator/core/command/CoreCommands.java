package dev.riftal.creator.core.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.config.CreatorConfig;
import dev.riftal.creator.core.sched.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The suite-level {@code /creator} command. Owned by core; features register their own roots.
 *
 * <ul>
 *   <li>{@code /creator silent <true|false>} - recording mode: replies move to the action bar and
 *       vanilla command echo is switched off.</li>
 *   <li>{@code /creator features} - lists every feature and whether it is enabled.</li>
 *   <li>{@code /creator feature <id> <true|false>} - writes the config toggle; takes effect on the
 *       next game start.</li>
 * </ul>
 */
public final class CoreCommands {

    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.op("creator")
                .then(CommandHelper.literal("silent")
                        .then(CommandHelper.arg("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "enabled");
                                    SilentMode.set(ctx.getSource().getServer(), on);
                                    return CommandHelper.success(ctx.getSource(), Component.translatable(
                                            on ? "commands.creatormods.silent.on"
                                                    : "commands.creatormods.silent.off"));
                                })))
                .then(CommandHelper.literal("features")
                        .executes(ctx -> {
                            for (Feature feature : CreatorMods.FEATURES) {
                                boolean on = CreatorMods.isEnabled(feature.id());
                                CommandHelper.feedback(ctx.getSource(), Component.literal(" - ")
                                        .append(Component.literal(feature.id())
                                                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                                        .append(Component.literal(" "))
                                        .append(feature.displayName()));
                            }
                            return CreatorMods.active().size();
                        }))
                .then(CommandHelper.literal("feature")
                        .then(CommandHelper.arg("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(CommandHelper.arg("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            String id = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(ctx, "id");
                                            if (CreatorMods.byId(id).isEmpty()) {
                                                return CommandHelper.error(ctx.getSource(),
                                                        Component.translatable(
                                                                "commands.creatormods.feature.unknown", id));
                                            }
                                            boolean on = BoolArgumentType.getBool(ctx, "enabled");
                                            CreatorConfig.setEnabled(id, on);
                                            return CommandHelper.success(ctx.getSource(),
                                                    Component.translatable(
                                                            "commands.creatormods.feature.set", id, on));
                                        }))))
                .then(CommandHelper.literal("tasks")
                        .executes(ctx -> CommandHelper.feedback(ctx.getSource(),
                                Component.translatable("commands.creatormods.tasks", TickScheduler.size()))))));
    }

    private CoreCommands() {
    }
}
