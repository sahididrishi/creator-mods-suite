package dev.riftal.creator.features.evolve.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.evolve.EvolveFeature;
import dev.riftal.creator.features.evolve.data.EvolutionData;
import dev.riftal.creator.features.evolve.net.TransformFxPayload;
import dev.riftal.creator.features.evolve.net.XpPopupPayload;
import dev.riftal.creator.features.evolve.perk.StagePerks;
import dev.riftal.creator.features.evolve.progression.EvolveManager;
import dev.riftal.creator.features.evolve.progression.Transformation;
import dev.riftal.creator.features.evolve.stage.EvolutionStage;
import dev.riftal.creator.features.evolve.stage.StageModifiers;
import dev.riftal.creator.features.evolve.stage.Stages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * {@code /evolve} - the recording controls.
 *
 * <pre>
 * evolve
 * |- set   &lt;targets&gt; &lt;1..5&gt; [instant]   [2]  set the stage, with or without the sequence
 * |- xp    &lt;targets&gt; &lt;amount&gt;           [2]  add (or remove) evolution XP, auto-evolves
 * |- reset &lt;targets&gt;                    [2]  back to Hatchling, modifiers and perks cleared
 * |- info  [&lt;target&gt;]                   [0]  print stage, XP, kills and the live attribute values
 * |- roar  [&lt;targets&gt;]                  [2]  play the Apex roar on cue
 * |- fx    &lt;targets&gt; start [ticks]|stop [2]  the transformation fx alone, for b-roll
 * '- model &lt;targets&gt; on|off|auto        [2]  force or release the beast model swap
 * </pre>
 *
 * <p>The root sits at permission 0 so {@code info} is usable by anyone; every mutating branch
 * requires level 2. All feedback goes through {@link CommandHelper}, so {@code /creator silent
 * true} moves it to the action bar.
 */
public final class EvolveCommands {

    private static final String KEY = "commands.creator_evolve.";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(CommandHelper.op("evolve", 0)
                .then(CommandHelper.literal("set")
                        .requires(EvolveCommands::isOperator)
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.arg("stage", IntegerArgumentType.integer(Stages.MIN, Stages.MAX))
                                        .executes(ctx -> set(ctx, false))
                                        .then(CommandHelper.literal("instant")
                                                .executes(ctx -> set(ctx, true))))))

                .then(CommandHelper.literal("xp")
                        .requires(EvolveCommands::isOperator)
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.arg("amount", IntegerArgumentType.integer(
                                                -EvolveManager.MAX_COMMAND_XP, EvolveManager.MAX_COMMAND_XP))
                                        .executes(EvolveCommands::xp))))

                .then(CommandHelper.literal("reset")
                        .requires(EvolveCommands::isOperator)
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .executes(EvolveCommands::reset)))

                .then(CommandHelper.literal("info")
                        .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                        .then(CommandHelper.arg("target", EntityArgument.player())
                                .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "target")))))

                .then(CommandHelper.literal("roar")
                        .requires(EvolveCommands::isOperator)
                        .executes(ctx -> roar(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .executes(ctx -> roar(ctx, EntityArgument.getPlayers(ctx, "targets")))))

                .then(CommandHelper.literal("fx")
                        .requires(EvolveCommands::isOperator)
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.literal("start")
                                        .executes(ctx -> fx(ctx, true, Transformation.TICKS))
                                        .then(CommandHelper.arg("ticks", IntegerArgumentType.integer(1, 400))
                                                .executes(ctx -> fx(ctx, true,
                                                        IntegerArgumentType.getInteger(ctx, "ticks")))))
                                .then(CommandHelper.literal("stop")
                                        .executes(ctx -> fx(ctx, false, 0)))))

                .then(CommandHelper.literal("model")
                        .requires(EvolveCommands::isOperator)
                        .then(CommandHelper.arg("targets", EntityArgument.players())
                                .then(CommandHelper.literal("on")
                                        .executes(ctx -> model(ctx, EvolutionData.MODEL_FORCED_ON)))
                                .then(CommandHelper.literal("off")
                                        .executes(ctx -> model(ctx, EvolutionData.MODEL_FORCED_OFF)))
                                .then(CommandHelper.literal("auto")
                                        .executes(ctx -> model(ctx, EvolutionData.MODEL_AUTO))))));
    }

    private static boolean isOperator(CommandSourceStack source) {
        return source.hasPermission(CommandHelper.OP_LEVEL);
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean instant)
            throws CommandSyntaxException {
        if (notReady(ctx)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int stage = IntegerArgumentType.getInteger(ctx, "stage");
        for (ServerPlayer player : targets) {
            EvolveManager.setStage(player, stage, instant);
        }
        EvolutionStage reached = Stages.byOrdinal(stage);
        Component name = Component.translatable(reached.nameKey());
        if (targets.size() == 1) {
            return CommandHelper.success(ctx.getSource(), Component.translatable(KEY + "set.single",
                    targets.iterator().next().getDisplayName(), stage, name));
        }
        return CommandHelper.success(ctx.getSource(), Component.translatable(KEY + "set.many",
                targets.size(), stage, name));
    }

    private static int xp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (notReady(ctx)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        for (ServerPlayer player : targets) {
            EvolveManager.addXp(player, amount, XpPopupPayload.SOURCE_COMMAND);
        }
        if (targets.size() == 1) {
            ServerPlayer only = targets.iterator().next();
            EvolutionData state = EvolveManager.data(only);
            return CommandHelper.success(ctx.getSource(), Component.translatable(KEY + "xp.single",
                    signed(amount), only.getDisplayName(), state.xp(),
                    nextThresholdLabel(state.stage())));
        }
        return CommandHelper.success(ctx.getSource(), Component.translatable(KEY + "xp.many",
                signed(amount), targets.size()));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (notReady(ctx)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            EvolveManager.reset(player);
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(KEY + "reset", targets.size()));
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (notReady(ctx)) {
            return 0;
        }
        EvolutionData state = EvolveManager.data(target);
        EvolutionStage stage = Stages.byOrdinal(state.stage());
        return CommandHelper.feedback(ctx.getSource(), Component.translatable(KEY + "info",
                target.getDisplayName(),
                state.stage(),
                Component.translatable(stage.nameKey()),
                state.xp(),
                nextThresholdLabel(state.stage()),
                state.totalKills(),
                format(target.getAttributeValue(Attributes.SCALE)),
                format(target.getMaxHealth()),
                modifiers(target),
                Component.translatable(stage.perkNameKey())));
    }

    private static int roar(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        if (notReady(ctx)) {
            return 0;
        }
        int scattered = 0;
        for (ServerPlayer player : targets) {
            Fx.sound(player.level(), player.position(), EvolveFeature.roarApex(),
                    SoundSource.PLAYERS, 1.6F, 0.9F);
            if (player.level() instanceof ServerLevel level) {
                Fx.particleRing(level, ParticleTypes.SOUL_FIRE_FLAME,
                        player.position().add(0.0D, 1.0D, 0.0D), 2.0D, 24);
            }
            scattered += StagePerks.roar(player, EvolveManager.data(player));
            // Trackers turn this into one play of animation.apex.roar on that player's beast.
            Payloads.sendToTracking(player, TransformFxPayload.roar(player.getUUID(),
                    EvolveManager.data(player).stage()));
        }
        return CommandHelper.success(ctx.getSource(),
                Component.translatable(KEY + "roar", targets.size(), scattered));
    }

    private static int fx(CommandContext<CommandSourceStack> ctx, boolean start, int ticks)
            throws CommandSyntaxException {
        if (notReady(ctx)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int affected = 0;
        for (ServerPlayer player : targets) {
            if (start) {
                // Refused outright on a player who is genuinely mid-transformation: b-roll must
                // never cancel a real sequence's finish task and leave the target frozen.
                if (Transformation.playFxOnly(player, ticks)) {
                    affected++;
                }
            } else {
                // Only the b-roll tag. A live sequence keeps its spiral, its lock and its finish.
                Transformation.stopFxOnly(player);
                affected++;
            }
        }
        if (start && affected == 0) {
            return CommandHelper.error(ctx.getSource(),
                    Component.translatable(KEY + "fx.busy", targets.size()));
        }
        return CommandHelper.success(ctx.getSource(), start
                ? Component.translatable(KEY + "fx.start", affected, ticks)
                : Component.translatable(KEY + "fx.stop", affected));
    }

    private static int model(CommandContext<CommandSourceStack> ctx, int override)
            throws CommandSyntaxException {
        if (notReady(ctx)) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            EvolveManager.setModelOverride(player, override);
        }
        String mode = switch (override) {
            case EvolutionData.MODEL_FORCED_ON -> "on";
            case EvolutionData.MODEL_FORCED_OFF -> "off";
            default -> "auto";
        };
        return CommandHelper.success(ctx.getSource(), Component.translatable(KEY + "model",
                Component.translatable(KEY + "model." + mode), targets.size()));
    }

    private static boolean notReady(CommandContext<CommandSourceStack> ctx) {
        if (EvolveFeature.isReady()) {
            return false;
        }
        CommandHelper.error(ctx.getSource(), Component.translatable(KEY + "disabled"));
        return true;
    }

    private static String nextThresholdLabel(int stageOrdinal) {
        if (stageOrdinal >= Stages.MAX) {
            return "MAX";
        }
        return Integer.toString(Stages.thresholdFor(stageOrdinal + 1));
    }

    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : Integer.toString(amount);
    }

    /**
     * The live amounts of the three stage modifiers a viewer can actually see on camera, read back
     * off the attribute map rather than off the stage table - so a drifted or half-applied body
     * shows up in {@code /evolve info} instead of being invisible until the next take.
     */
    private static String modifiers(ServerPlayer target) {
        return "scale " + signed(StageModifiers.modifierAmount(target, Attributes.SCALE,
                        StageModifiers.SCALE_ID))
                + ", hp " + signed(StageModifiers.modifierAmount(target, Attributes.MAX_HEALTH,
                        StageModifiers.HEALTH_ID))
                + ", atk " + signed(StageModifiers.modifierAmount(target, Attributes.ATTACK_DAMAGE,
                        StageModifiers.ATTACK_ID));
    }

    private static String signed(double amount) {
        return (amount >= 0.0D ? "+" : "") + format(amount);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private EvolveCommands() {
    }
}
