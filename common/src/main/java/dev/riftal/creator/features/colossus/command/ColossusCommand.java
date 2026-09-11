package dev.riftal.creator.features.colossus.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.util.Selection;
import dev.riftal.creator.features.colossus.AttackKind;
import dev.riftal.creator.features.colossus.AttackSelector;
import dev.riftal.creator.features.colossus.BossPhase;
import dev.riftal.creator.features.colossus.ColossusFeature;
import dev.riftal.creator.features.colossus.arena.Arena;
import dev.riftal.creator.features.colossus.arena.ArenaSavedData;
import dev.riftal.creator.features.colossus.entity.AshenColossusEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /colossus} - the director's console for the Ashen Colossus fight.
 *
 * <pre>
 * /colossus spawn [arena]
 * /colossus phase &lt;1-3&gt;
 * /colossus hp &lt;0-100&gt;
 * /colossus stagger
 * /colossus kill
 * /colossus arena set [name] [radius]
 * /colossus arena clear [name]
 * /colossus status
 * </pre>
 *
 * <p>Every boss-affecting subcommand acts on the nearest living Colossus within
 * {@value #SEARCH_RADIUS} blocks of the caller. All feedback goes through
 * {@link CommandHelper}, so {@code /creator silent true} moves it off camera.
 */
public final class ColossusCommand {

    /** How far a subcommand looks for a boss to act on. */
    public static final double SEARCH_RADIUS = 128.0D;

    private static final SuggestionProvider<CommandSourceStack> ARENA_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ArenaSavedData.get(ctx.getSource().getLevel()).names(), builder);

    /** Call from {@code ColossusFeature#registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.op("colossus")
                .then(CommandHelper.literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), null))
                        .then(CommandHelper.arg("arena", StringArgumentType.word())
                                .suggests(ARENA_NAMES)
                                .executes(ctx -> spawn(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "arena")))))
                .then(CommandHelper.literal("phase")
                        .then(CommandHelper.arg("n", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> phase(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n")))))
                .then(CommandHelper.literal("hp")
                        .then(CommandHelper.arg("percent", FloatArgumentType.floatArg(0.0F, 100.0F))
                                .executes(ctx -> hp(ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "percent")))))
                .then(CommandHelper.literal("stagger")
                        .executes(ctx -> stagger(ctx.getSource())))
                .then(CommandHelper.literal("kill")
                        .executes(ctx -> kill(ctx.getSource())))
                .then(CommandHelper.literal("arena")
                        .then(CommandHelper.literal("set")
                                .executes(ctx -> arenaSet(ctx, null, Arena.DEFAULT_RADIUS))
                                .then(CommandHelper.arg("name", StringArgumentType.word())
                                        .suggests(ARENA_NAMES)
                                        .executes(ctx -> arenaSet(ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                Arena.DEFAULT_RADIUS))
                                        .then(CommandHelper.arg("radius", IntegerArgumentType.integer(
                                                        Arena.MIN_RADIUS, Arena.MAX_RADIUS))
                                                .executes(ctx -> arenaSet(ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(CommandHelper.literal("clear")
                                .executes(ctx -> arenaClearAll(ctx.getSource()))
                                .then(CommandHelper.arg("name", StringArgumentType.word())
                                        .suggests(ARENA_NAMES)
                                        .executes(ctx -> arenaClear(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(CommandHelper.literal("status")
                        .executes(ctx -> status(ctx.getSource())))));
    }

    // ------------------------------------------------------------------ subcommands

    private static int spawn(CommandSourceStack source, @Nullable String requestedArena) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        String name = requestedArena != null
                ? ArenaSavedData.normalise(requestedArena)
                : ColossusFeature.lastArenaOf(player);

        Optional<Arena> stored = ArenaSavedData.get(level).find(name);
        BlockPos centre = stored.map(Arena::centre).orElseGet(() -> BlockPos.containing(source.getPosition()));
        int radius = stored.map(Arena::radius).orElse(Arena.DEFAULT_RADIUS);

        AshenColossusEntity boss = ColossusFeature.colossus().get().create(level);
        if (boss == null) {
            return CommandHelper.error(source,
                    Component.translatable("commands.creator_colossus.spawn_failed"));
        }
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        boss.moveTo(centre.getX() + 0.5D, centre.getY(), centre.getZ() + 0.5D, yaw, 0.0F);
        boss.bindArena(name, centre, radius);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);
        ColossusFeature.rememberArena(player, name);

        return CommandHelper.success(source, Component.translatable(
                "commands.creator_colossus.spawn",
                centre.getX(), centre.getY(), centre.getZ(), name, radius));
    }

    private static int phase(CommandSourceStack source, int phaseIndex) {
        AshenColossusEntity boss = findBoss(source);
        if (boss == null) {
            return noBoss(source);
        }
        float fraction = switch (phaseIndex) {
            case 2 -> 0.65F;
            case 3 -> 0.32F;
            default -> 1.0F;
        };
        boss.setHealth(boss.getMaxHealth() * fraction);
        boss.forcePhase(BossPhase.byIndex(phaseIndex));
        return CommandHelper.success(source, Component.translatable(
                "commands.creator_colossus.phase", phaseIndex, Math.round(fraction * 100.0F)));
    }

    private static int hp(CommandSourceStack source, float percent) {
        AshenColossusEntity boss = findBoss(source);
        if (boss == null) {
            return noBoss(source);
        }
        if (percent <= 0.0F) {
            boss.startDeath();
            return CommandHelper.success(source,
                    Component.translatable("commands.creator_colossus.kill"));
        }
        boss.setHealth(boss.getMaxHealth() * (percent / 100.0F));
        return CommandHelper.success(source, Component.translatable(
                "commands.creator_colossus.hp", Math.round(percent),
                BossPhase.forHealthFraction(percent / 100.0F).index()));
    }

    private static int stagger(CommandSourceStack source) {
        AshenColossusEntity boss = findBoss(source);
        if (boss == null) {
            return noBoss(source);
        }
        boss.stagger();
        return CommandHelper.success(source,
                Component.translatable("commands.creator_colossus.stagger"));
    }

    private static int kill(CommandSourceStack source) {
        AshenColossusEntity boss = findBoss(source);
        if (boss == null) {
            return noBoss(source);
        }
        boss.startDeath();
        return CommandHelper.success(source,
                Component.translatable("commands.creator_colossus.kill"));
    }

    private static int arenaSet(CommandContext<CommandSourceStack> ctx, @Nullable String requestedName,
                                int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        String name = requestedName != null
                ? ArenaSavedData.normalise(requestedName)
                : ColossusFeature.lastArenaOf(player);

        BlockPos centre = BlockPos.containing(source.getPosition());
        Arena arena = new Arena(name, centre, radius);
        ArenaSavedData.get(level).put(arena);
        ColossusFeature.rememberArena(player, name);

        // A boss already bound to this arena re-binds live, so the ring moves mid-take.
        int rebound = 0;
        for (AshenColossusEntity boss : Selection.around(level, AshenColossusEntity.class,
                Vec3.atCenterOf(centre), SEARCH_RADIUS, b -> b.getArenaName().equals(name))) {
            boss.bindArena(name, arena.centre(), arena.radius());
            rebound++;
        }

        int result = CommandHelper.success(source,
                Component.translatable("commands.creator_colossus.arena.set",
                        name, centre.getX(), centre.getY(), centre.getZ(), arena.radius()));
        if (rebound > 0) {
            CommandHelper.feedback(source,
                    Component.translatable("commands.creator_colossus.arena.rebound", rebound));
        }
        return result;
    }

    private static int arenaClear(CommandSourceStack source, String name) {
        boolean removed = ArenaSavedData.get(source.getLevel()).remove(name);
        if (!removed) {
            return CommandHelper.error(source, Component.translatable(
                    "commands.creator_colossus.arena.unknown", ArenaSavedData.normalise(name)));
        }
        return CommandHelper.success(source,
                Component.translatable("commands.creator_colossus.arena.cleared", 1));
    }

    private static int arenaClearAll(CommandSourceStack source) {
        int removed = ArenaSavedData.get(source.getLevel()).clear();
        return CommandHelper.success(source,
                Component.translatable("commands.creator_colossus.arena.cleared", removed));
    }

    private static int status(CommandSourceStack source) {
        AshenColossusEntity boss = findBoss(source);
        if (boss == null) {
            return noBoss(source);
        }
        ServerLevel level = source.getLevel();
        AttackSelector.Cooldowns cooldowns = boss.cooldowns();
        AttackKind attack = boss.getAttack();

        CommandHelper.feedback(source, Component.translatable(
                "commands.creator_colossus.status.health",
                (int) boss.getHealth(), (int) boss.getMaxHealth(),
                Math.round(boss.getHealth() / boss.getMaxHealth() * 100.0F), boss.getPhase()));
        CommandHelper.feedback(source, Component.translatable(
                "commands.creator_colossus.status.attack",
                attack.name().toLowerCase(Locale.ROOT),
                cooldowns.slam(), cooldowns.lavaRain(), cooldowns.summon(), cooldowns.combo()));
        CommandHelper.feedback(source, Component.translatable(
                "commands.creator_colossus.status.arena",
                boss.getArenaName(), boss.getArenaRadius(),
                String.format(Locale.ROOT, "%.1f", boss.getRingRadius()),
                boss.aliveMinionCount(level), AttackSelector.MINION_CAP));
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    /** Nearest living Colossus within {@value #SEARCH_RADIUS} blocks of the caller, or null. */
    @Nullable
    public static AshenColossusEntity findBoss(CommandSourceStack source) {
        return Selection.nearest(source.getLevel(), AshenColossusEntity.class,
                source.getPosition(), SEARCH_RADIUS, boss -> !boss.isDeadOrDying());
    }

    private static int noBoss(CommandSourceStack source) {
        return CommandHelper.error(source,
                Component.translatable("commands.creator_colossus.none", (int) SEARCH_RADIUS));
    }

    private ColossusCommand() {
    }
}
