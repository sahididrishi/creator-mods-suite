package dev.riftal.creator.features.arsenal.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mechanic.GrappleManager;
import dev.riftal.creator.features.arsenal.mechanic.GravitySlam;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code /arsenal} - the recording controls for this feature.
 *
 * <pre>
 * /arsenal give &lt;targets&gt; &lt;grapple_blade|storm_bow|gravity_hammer|soul_scythe&gt;
 * /arsenal give &lt;targets&gt; all
 * /arsenal cooldown reset [&lt;targets&gt;]
 * /arsenal hook retract [&lt;targets&gt;]
 * /arsenal slam cancel [&lt;targets&gt;]
 * </pre>
 *
 * <p>All feedback goes through {@link CommandHelper}, so {@code /creator silent true} moves it to
 * the action bar and nothing is ever broadcast to other ops.
 */
public final class ArsenalCommands {

    /** Arrows handed out alongside the Storm Bow. */
    public static final int ARROWS_WITH_BOW = 64;

    /** Declares the command tree. Call from {@code registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.op("arsenal")
                .then(CommandHelper.literal("give")
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.literal("all")
                                        .executes(ctx -> give(ctx, targets(ctx), List.of(Weapon.values()))))
                                .then(CommandHelper.arg("weapon", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(Weapon.ids(), builder))
                                        .executes(ArsenalCommands::giveOne))))
                .then(CommandHelper.literal("cooldown")
                        .then(CommandHelper.literal("reset")
                                .executes(ctx -> cooldownReset(ctx, self(ctx)))
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .executes(ctx -> cooldownReset(ctx, targets(ctx))))))
                .then(CommandHelper.literal("hook")
                        .then(CommandHelper.literal("retract")
                                .executes(ctx -> hookRetract(ctx, self(ctx)))
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .executes(ctx -> hookRetract(ctx, targets(ctx))))))
                .then(CommandHelper.literal("slam")
                        .then(CommandHelper.literal("cancel")
                                .executes(ctx -> slamCancel(ctx, self(ctx)))
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .executes(ctx -> slamCancel(ctx, targets(ctx))))))));
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return List.of(ctx.getSource().getPlayerOrException());
    }

    private static int giveOne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String requested = StringArgumentType.getString(ctx, "weapon");
        Optional<Weapon> weapon = Weapon.byId(requested);
        if (weapon.isEmpty()) {
            return CommandHelper.error(ctx.getSource(), Component.translatable(
                    key("unknown_weapon"), requested, String.join(", ", Weapon.ids())));
        }
        return give(ctx, targets(ctx), List.of(weapon.get()));
    }

    private static int give(CommandContext<CommandSourceStack> ctx,
                            Collection<ServerPlayer> players,
                            List<Weapon> weapons) {
        for (ServerPlayer player : players) {
            boolean gaveBow = false;
            for (Weapon weapon : weapons) {
                player.getInventory().placeItemBackInInventory(new ItemStack(weapon.item()));
                gaveBow |= weapon == Weapon.STORM_BOW;
            }
            if (gaveBow) {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, ARROWS_WITH_BOW));
            }
        }

        if (weapons.size() == 1) {
            return CommandHelper.success(ctx.getSource(), Component.translatable(key("give"),
                    Component.translatable(weapons.get(0).item().getDescriptionId()),
                    describe(players)));
        }
        return CommandHelper.success(ctx.getSource(), Component.translatable(key("give_all"),
                weapons.size(), describe(players)));
    }

    private static int cooldownReset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            for (Weapon weapon : Weapon.values()) {
                player.getCooldowns().removeCooldown(weapon.item());
            }
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(key("cooldown_reset"), describe(players)));
    }

    private static int hookRetract(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int cleared = 0;
        for (ServerPlayer player : players) {
            if (GrappleManager.clear(player)) {
                cleared++;
            }
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(key("hook_retract"), cleared));
    }

    private static int slamCancel(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int cancelled = 0;
        for (ServerPlayer player : players) {
            if (GravitySlam.cancel(player)) {
                cancelled++;
            }
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(key("slam_cancel"), cancelled));
    }

    private static Component describe(Collection<ServerPlayer> players) {
        if (players.size() == 1) {
            return players.iterator().next().getDisplayName();
        }
        return Component.translatable(key("players"), players.size());
    }

    private static String key(String verb) {
        return "commands." + ArsenalFeature.NAMESPACE + "." + verb;
    }

    private ArsenalCommands() {
    }
}
