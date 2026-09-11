package dev.riftal.creator.features.rules.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.rules.RuleManager;
import dev.riftal.creator.features.rules.api.Rule;
import dev.riftal.creator.features.rules.api.RuleRegistry;
import dev.riftal.creator.features.rules.preset.RulePreset;
import dev.riftal.creator.features.rules.preset.RulePresets;
import dev.riftal.creator.features.rules.rules.HeartsCurrencyRule;
import dev.riftal.creator.features.rules.rules.RandomDropsRule;
import dev.riftal.creator.features.rules.rules.RandomItems;
import dev.riftal.creator.features.rules.shop.ShopOffers;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /rule} and {@code /shop}.
 *
 * <p>{@code /rule list} and {@code /rule status} (and {@code /shop}) are open to everyone; anything
 * that changes the world needs op level 2, which is why the root literal is ungated and the
 * mutating children carry the permission instead.
 *
 * <p>The shop is reachable as both {@code /shop} and {@code /rule shop}: the short form is what the
 * demo types, the long form still works if another mod on the pack owns {@code /shop}.
 *
 * <p>Every reply goes through {@link CommandHelper}, so it lands on the action bar when the human
 * has {@code /creator silent true} on and never broadcasts to other operators.
 */
public final class RuleCommand {

    private static final SuggestionProvider<CommandSourceStack> RULE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(RuleRegistry.ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> PRESET_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    RulePresets.ids(ctx.getSource().getServer()), builder);

    /** Registers both roots. Call from {@code RulesFeature#registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> {
            dispatcher.register(ruleTree());
            dispatcher.register(shopTree());
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> ruleTree() {
        return CommandHelper.literal("rule")
                .executes(RuleCommand::status)
                .then(CommandHelper.literal("list").executes(RuleCommand::list))
                .then(CommandHelper.literal("status").executes(RuleCommand::status))
                .then(CommandHelper.literal("shop").executes(RuleCommand::openShop))
                .then(CommandHelper.op("clear").executes(RuleCommand::clear))
                .then(CommandHelper.op("reload").executes(RuleCommand::reload))
                .then(CommandHelper.op("hud")
                        .then(CommandHelper.literal("on").executes(ctx -> hud(ctx, true)))
                        .then(CommandHelper.literal("off").executes(ctx -> hud(ctx, false))))
                .then(CommandHelper.literal("preset")
                        .then(CommandHelper.literal("list").executes(RuleCommand::presetList))
                        .then(CommandHelper.arg("preset", StringArgumentType.word())
                                .requires(source -> source.hasPermission(CommandHelper.OP_LEVEL))
                                .suggests(PRESET_IDS)
                                .executes(RuleCommand::applyPreset)))
                .then(CommandHelper.arg("rule", StringArgumentType.word())
                        .requires(source -> source.hasPermission(CommandHelper.OP_LEVEL))
                        .suggests(RULE_IDS)
                        .then(CommandHelper.literal("on").executes(ctx -> set(ctx, true)))
                        .then(CommandHelper.literal("off").executes(ctx -> set(ctx, false)))
                        .then(CommandHelper.literal("toggle").executes(RuleCommand::toggle))
                        .then(CommandHelper.literal("fire").executes(RuleCommand::fire)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shopTree() {
        return CommandHelper.literal("shop").executes(RuleCommand::openShop);
    }

    /** Shared by {@code /shop} and the {@code /rule shop} alias, in case another mod owns /shop. */
    private static int openShop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_rules.shop.player_only"));
        }
        HeartsCurrencyRule.openShop(player);
        return 1;
    }

    // ---------------------------------------------------------------- actions

    private static int set(CommandContext<CommandSourceStack> ctx, boolean on) {
        String id = StringArgumentType.getString(ctx, "rule");
        return report(ctx, id, RuleManager.set(id, on));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "rule");
        return report(ctx, id, RuleManager.toggle(id));
    }

    /**
     * {@code /rule item_roulette fire} - do the thing now instead of at the end of the timer. The
     * 60-second roulette and the 30-second shuffle are otherwise impossible to film in one take.
     */
    private static int fire(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "rule");
        return report(ctx, id, RuleManager.fire(id));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, String id, RuleManager.Result result) {
        CommandSourceStack source = ctx.getSource();
        return switch (result) {
            case ENABLED -> CommandHelper.success(source,
                    Component.translatable("commands.creator_rules.on", name(id)));
            case DISABLED -> CommandHelper.feedback(source,
                    Component.translatable("commands.creator_rules.off", name(id))
                            .withStyle(ChatFormatting.GRAY));
            case ALREADY -> CommandHelper.feedback(source,
                    Component.translatable("commands.creator_rules.already", name(id))
                            .withStyle(ChatFormatting.GRAY));
            case UNKNOWN -> CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.unknown", id));
            case NO_SERVER -> CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.no_world"));
            case FIRED -> CommandHelper.success(source,
                    Component.translatable("commands.creator_rules.fired", name(id)));
            case INACTIVE -> CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.inactive", name(id)));
            case UNSUPPORTED -> CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.unsupported", name(id)));
        };
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MutableComponent message = Component.translatable("commands.creator_rules.list.header",
                RuleManager.active().size(), RuleRegistry.size()).withStyle(ChatFormatting.GOLD);
        for (String id : RuleManager.active()) {
            message.append(row(id, true));
        }
        for (Rule rule : RuleRegistry.all()) {
            if (!RuleManager.isActive(rule.id())) {
                message.append(row(rule.id(), false));
            }
        }
        return CommandHelper.feedback(source, message);
    }

    private static MutableComponent row(String id, boolean active) {
        Rule rule = RuleRegistry.byId(id);
        MutableComponent line = Component.literal("\n")
                .append(Component.literal(active ? " ✔ " : " ✘ ")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                .append(Component.literal(id)
                        .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        if (rule != null) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(rule.description().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        List<String> active = RuleManager.active();
        if (active.isEmpty()) {
            return CommandHelper.feedback(ctx.getSource(),
                    Component.translatable("commands.creator_rules.status.none")
                            .withStyle(ChatFormatting.GRAY));
        }
        return CommandHelper.feedback(ctx.getSource(),
                Component.translatable("commands.creator_rules.status",
                        active.size(), String.join(", ", active)).withStyle(ChatFormatting.GOLD));
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int count = RuleManager.clearAll();
        if (count < 0) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_rules.no_world"));
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_rules.cleared", count));
    }

    private static int hud(CommandContext<CommandSourceStack> ctx, boolean visible) {
        if (!RuleManager.isRunning()) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_rules.no_world"));
        }
        RuleManager.setHudEnabled(visible);
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(visible
                        ? "commands.creator_rules.hud.on"
                        : "commands.creator_rules.hud.off"));
    }

    private static int presetList(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> ids = RulePresets.ids(server);
        if (ids.isEmpty()) {
            return CommandHelper.feedback(ctx.getSource(),
                    Component.translatable("commands.creator_rules.preset.none")
                            .withStyle(ChatFormatting.GRAY));
        }
        return CommandHelper.feedback(ctx.getSource(),
                Component.translatable("commands.creator_rules.preset.list", String.join(", ", ids))
                        .withStyle(ChatFormatting.GOLD));
    }

    private static int applyPreset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "preset");
        RulePreset preset = RulePresets.byId(source.getServer(), id);
        if (preset == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.preset.unknown", id));
        }
        int active = RuleManager.applyPreset(preset);
        if (active < 0) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_rules.no_world"));
        }
        return CommandHelper.success(source,
                Component.translatable("commands.creator_rules.preset.applied", id, active));
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        // Everything this feature reads out of a data pack is cached against the ResourceManager,
        // so re-reading the presets means re-reading the shop and the tag-filtered item pool too.
        ShopOffers.clear();
        RandomItems.invalidate();
        if (RuleRegistry.byId("random_drops") instanceof RandomDropsRule randomDrops) {
            randomDrops.invalidate();
        }
        int count = RulePresets.reload(ctx.getSource().getServer());
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_rules.preset.reloaded", count));
    }

    private static Component name(String id) {
        return Component.literal(id);
    }

    private RuleCommand() {
    }
}
