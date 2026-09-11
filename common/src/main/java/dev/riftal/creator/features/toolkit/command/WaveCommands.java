package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.wave.WaveMath;
import dev.riftal.creator.features.toolkit.wave.WaveSpawner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** {@code /toolkit wave spawn <entity> <count> <radius> [ring|random] [centre]} and {@code wave clear}. */
public final class WaveCommands {

    /** The {@code wave} subtree, op only. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.op("wave")
                .then(CommandHelper.literal("spawn")
                        .then(CommandHelper.arg("entity", ResourceLocationArgument.id())
                                .suggests(ToolkitSuggestions.ENTITY_TYPES)
                                .then(CommandHelper.arg("count",
                                                IntegerArgumentType.integer(1, WaveSpawner.MAX_COUNT))
                                        .then(withModes(CommandHelper.arg("radius",
                                                DoubleArgumentType.doubleArg(0.0D, 64.0D)))))))
                .then(CommandHelper.literal("clear").executes(ctx -> clear(ctx.getSource())));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> withModes(
            RequiredArgumentBuilder<CommandSourceStack, Double> radius) {
        return radius
                .executes(ctx -> spawn(ctx, WaveMath.Mode.RING, null))
                .then(CommandHelper.literal("ring")
                        .executes(ctx -> spawn(ctx, WaveMath.Mode.RING, null))
                        .then(CommandHelper.arg("centre", Vec3Argument.vec3())
                                .executes(ctx -> spawn(ctx, WaveMath.Mode.RING,
                                        Vec3Argument.getVec3(ctx, "centre")))))
                .then(CommandHelper.literal("random")
                        .executes(ctx -> spawn(ctx, WaveMath.Mode.RANDOM, null))
                        .then(CommandHelper.arg("centre", Vec3Argument.vec3())
                                .executes(ctx -> spawn(ctx, WaveMath.Mode.RANDOM,
                                        Vec3Argument.getVec3(ctx, "centre")))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, WaveMath.Mode mode, Vec3 centre) {
        CommandSourceStack source = ctx.getSource();
        ToolkitRuntime.bind(source.getServer());
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "entity");
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) {
            return CommandHelper.error(source, ToolkitText.of("wave.unknown", id.toString()));
        }
        int count = IntegerArgumentType.getInteger(ctx, "count");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        ServerLevel level = source.getLevel();
        Vec3 where = centre == null ? source.getPosition() : centre;
        int spawned = WaveSpawner.spawn(level, where, type.get(), count, radius, mode);
        return CommandHelper.success(source, ToolkitText.of("wave.spawned",
                spawned, id.toString(), mode.key(), String.format(java.util.Locale.ROOT, "%.1f", radius)));
    }

    private static int clear(CommandSourceStack source) {
        ToolkitRuntime.bind(source.getServer());
        int cleared = WaveSpawner.clear(source.getLevel());
        return CommandHelper.success(source, ToolkitText.of("wave.cleared", cleared));
    }

    private WaveCommands() {
    }
}
