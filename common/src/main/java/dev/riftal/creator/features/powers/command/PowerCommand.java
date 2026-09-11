package dev.riftal.creator.features.powers.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.powers.PowersFeature;
import dev.riftal.creator.features.powers.ability.Ability;
import dev.riftal.creator.features.powers.ability.AbilityRegistry;
import dev.riftal.creator.features.powers.ability.UseResult;
import dev.riftal.creator.features.powers.data.PlayerPowers;
import dev.riftal.creator.features.powers.net.PowerHudPayload;
import dev.riftal.creator.features.powers.server.PowerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code /power} - the recording control surface for the Power Kit.
 *
 * <p>Mutating sub-commands need op level 2; {@code list} and {@code hud} are open to everyone, so
 * a guest on a LAN world can see their own loadout and hide the row for their own capture. Every
 * reply goes through {@link CommandHelper}, which routes to the action bar in silent mode and never
 * broadcasts {@code [Player: ...]} to the other ops.
 */
public final class PowerCommand {

    private static final SuggestionProvider<CommandSourceStack> ABILITY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(AbilityRegistry.ids(), builder);

    /** Builds the tree. Called from {@code PowersFeature#registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.literal("power")
                .then(CommandHelper.op("give")
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.literal("all")
                                        .executes(ctx -> giveAll(ctx, targets(ctx))))
                                .then(CommandHelper.arg("ability", ResourceLocationArgument.id())
                                        .suggests(ABILITY_SUGGESTIONS)
                                        .executes(ctx -> give(ctx, targets(ctx))))))
                .then(CommandHelper.op("all")
                        .executes(ctx -> giveAll(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .executes(ctx -> giveAll(ctx, targets(ctx)))))
                .then(CommandHelper.op("clear")
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .executes(ctx -> clear(ctx, targets(ctx)))))
                .then(CommandHelper.op("remove")
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.arg("ability", ResourceLocationArgument.id())
                                        .suggests(ABILITY_SUGGESTIONS)
                                        .executes(ctx -> remove(ctx, targets(ctx))))))
                .then(CommandHelper.op("cooldown")
                        .then(CommandHelper.literal("reset")
                                .executes(ctx -> resetCooldowns(ctx,
                                        List.of(ctx.getSource().getPlayerOrException()), null))
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .executes(ctx -> resetCooldowns(ctx, targets(ctx), null))
                                        .then(CommandHelper.arg("ability", ResourceLocationArgument.id())
                                                .suggests(ABILITY_SUGGESTIONS)
                                                .executes(ctx -> resetOne(ctx, targets(ctx))))))
                        .then(CommandHelper.literal("set")
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .then(CommandHelper.arg("ability", ResourceLocationArgument.id())
                                                .suggests(ABILITY_SUGGESTIONS)
                                                .then(CommandHelper.arg("ticks",
                                                                IntegerArgumentType.integer(0, 72000))
                                                        .executes(ctx -> setCooldown(ctx, targets(ctx))))))))
                .then(CommandHelper.op("use")
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.arg("ability", ResourceLocationArgument.id())
                                        .suggests(ABILITY_SUGGESTIONS)
                                        .executes(ctx -> use(ctx, targets(ctx))))))
                .then(CommandHelper.literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(CommandHelper.arg("target", EntityArgument.player())
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "target")))))
                .then(CommandHelper.literal("hud")
                        .then(CommandHelper.arg("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("on", "off", "radial", "linear"), builder))
                                .executes(PowerCommand::hud)))));
    }

    // ---------------------------------------------------------------- handlers

    private static int give(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players)
            throws CommandSyntaxException {
        Ability ability = ability(ctx);
        if (ability == null) {
            return unknownAbility(ctx);
        }
        int granted = 0;
        int full = 0;
        for (ServerPlayer player : players) {
            if (PowerManager.grant(player, ability)) {
                granted++;
            } else if (PowerManager.powersOf(player).full()) {
                full++;
            }
        }
        if (granted == 0 && full > 0) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_powers.slots_full", PlayerPowers.MAX_SLOTS));
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_powers.granted",
                        ability.displayName(), granted));
    }

    private static int giveAll(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int total = 0;
        for (ServerPlayer player : players) {
            total += PowerManager.grantAll(player);
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_powers.granted_all", total, players.size()));
    }

    private static int clear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            PowerManager.clear(player);
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_powers.cleared", players.size()));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        Ability ability = ability(ctx);
        if (ability == null) {
            return unknownAbility(ctx);
        }
        int removed = 0;
        for (ServerPlayer player : players) {
            if (PowerManager.revoke(player, ability)) {
                removed++;
            }
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_powers.removed", ability.displayName(), removed));
    }

    private static int resetOne(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        Ability ability = ability(ctx);
        if (ability == null) {
            return unknownAbility(ctx);
        }
        return resetCooldowns(ctx, players, ability);
    }

    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx,
                                      Collection<ServerPlayer> players, Ability only) {
        for (ServerPlayer player : players) {
            PowerManager.resetCooldowns(player, only);
        }
        return CommandHelper.success(ctx.getSource(),
                only == null
                        ? Component.translatable("commands.creator_powers.cooldown_reset_all", players.size())
                        : Component.translatable("commands.creator_powers.cooldown_reset",
                                only.displayName(), players.size()));
    }

    private static int setCooldown(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        Ability ability = ability(ctx);
        if (ability == null) {
            return unknownAbility(ctx);
        }
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        for (ServerPlayer player : players) {
            PowerManager.setCooldown(player, ability, ticks);
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable("commands.creator_powers.cooldown_set", ability.displayName(), ticks));
    }

    private static int use(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        Ability ability = ability(ctx);
        if (ability == null) {
            return unknownAbility(ctx);
        }
        int fired = 0;
        for (ServerPlayer player : players) {
            if (PowerManager.forceUse(player, ability) == UseResult.ACTIVATED) {
                fired++;
            }
        }
        if (fired == 0) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_powers.cannot_use", ability.displayName()));
        }
        // Silent by design: this is the "trigger on cue" command and it must not print mid-take.
        return fired;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        PlayerPowers powers = PowerManager.powersOf(target);
        long now = PowerManager.gameTime(target);
        if (powers.granted().isEmpty()) {
            return CommandHelper.feedback(ctx.getSource(),
                    Component.translatable("commands.creator_powers.list_empty", target.getDisplayName()));
        }
        MutableComponent joined = Component.empty();
        boolean first = true;
        for (ResourceLocation id : powers.granted()) {
            if (!first) {
                joined.append(Component.literal(", "));
            }
            first = false;
            Component name = AbilityRegistry.get(id)
                    .<Component>map(Ability::displayName)
                    .orElseGet(() -> Component.literal(id.toString()));
            int remaining = powers.remaining(id, now);
            Component state = remaining == 0
                    ? Component.translatable("commands.creator_powers.ready").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("commands.creator_powers.ticks", remaining)
                            .withStyle(ChatFormatting.GRAY);
            joined.append(name).append(Component.literal(" ")).append(state);
        }
        return CommandHelper.feedback(ctx.getSource(),
                Component.translatable("commands.creator_powers.list", target.getDisplayName(), joined));
    }

    private static int hud(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String word = StringArgumentType.getString(ctx, "mode");
        int mode = PowerHudPayload.parse(word);
        if (mode < 0) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable("commands.creator_powers.bad_hud_mode", word));
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PowerManager.send(player, new PowerHudPayload(mode));
        // Client-side switch only; no chat so it can be flipped between takes.
        return 1;
    }

    // ---------------------------------------------------------------- helpers

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    /**
     * The ability named by the {@code ability} argument, or null.
     *
     * <p>A bare {@code dash} typed during a take parses as {@code minecraft:dash} - vanilla's
     * {@code ResourceLocation} grammar defaults the namespace - and the registry only ever holds
     * {@code creator_powers:*}. Tab completion hides that, but nobody tab-completes with the camera
     * running, so an unqualified path falls back to this feature's own namespace.
     */
    private static Ability ability(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = abilityId(ctx);
        if (id == null) {
            return null;
        }
        Optional<Ability> found = AbilityRegistry.get(id);
        if (found.isPresent()) {
            return found.get();
        }
        if (ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            return AbilityRegistry.get(ResourceLocation.fromNamespaceAndPath(
                    PowersFeature.NAMESPACE, id.getPath())).orElse(null);
        }
        return null;
    }

    private static ResourceLocation abilityId(CommandContext<CommandSourceStack> ctx) {
        try {
            return ResourceLocationArgument.getId(ctx, "ability");
        } catch (IllegalArgumentException notPresent) {
            return null;
        }
    }

    private static int unknownAbility(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = abilityId(ctx);
        // Echo what was typed, not what it parsed to: an unqualified path came in as `dash`, and
        // answering "unknown ability 'minecraft:dash'" would send the creator hunting for a typo
        // they did not make.
        String typed = id == null ? ""
                : ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
        String known = String.join(", ", AbilityRegistry.ordered().stream().map(Ability::path).toList());
        return CommandHelper.error(ctx.getSource(),
                Component.translatable("commands.creator_powers.unknown_ability", typed, known));
    }

    private PowerCommand() {
    }
}
