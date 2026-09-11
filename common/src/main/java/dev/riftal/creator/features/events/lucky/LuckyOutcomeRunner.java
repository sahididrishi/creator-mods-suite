package dev.riftal.creator.features.events.lucky;

import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.core.util.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Turns a {@link LuckyOutcome} into something that happens in the world. Server side only.
 *
 * <p>Every outcome is best-effort: an unknown entity id, a missing loot table or a command the
 * server refuses is logged once by the caller and skipped, never thrown, because this runs from a
 * falling block landing during a live recording.
 */
public final class LuckyOutcomeRunner {

    /** Runs one outcome at {@code pos}. Returns false when the outcome could not be applied. */
    public static boolean run(ServerLevel level, BlockPos pos, LuckyOutcome outcome, RandomSource random) {
        Vec3 centre = Vec3.atCenterOf(pos);
        boolean applied = switch (outcome.type()) {
            case LuckyOutcome.TYPE_ITEMS -> items(level, centre, outcome);
            case LuckyOutcome.TYPE_ENTITY -> entity(level, pos, outcome, random);
            case LuckyOutcome.TYPE_EXPLOSION -> explosion(level, centre, outcome);
            case LuckyOutcome.TYPE_COMMAND -> command(level, centre, outcome);
            case LuckyOutcome.TYPE_EFFECT -> effect(level, centre, outcome);
            default -> false;
        };
        if (applied) {
            Fx.particles(level, ParticleTypes.TOTEM_OF_UNDYING, centre.add(0.0D, 0.4D, 0.0D), 24, 0.4D, 0.2D);
            Fx.sound(level, centre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F,
                    0.8F + random.nextFloat() * 0.6F);
        }
        return applied;
    }

    private static boolean items(ServerLevel level, Vec3 centre, LuckyOutcome outcome) {
        ResourceLocation id = ResourceLocation.tryParse(outcome.id());
        if (id == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        LootTable table = server.reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            return false;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, centre)
                .create(LootContextParamSets.CHEST);
        int rolls = Math.max(1, Math.min(outcome.count(), 8));
        boolean dropped = false;
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params, level.getRandom())) {
                if (stack.isEmpty()) {
                    continue;
                }
                ItemEntity item = new ItemEntity(level, centre.x, centre.y + 0.5D, centre.z, stack);
                item.setDeltaMovement(
                        (level.getRandom().nextDouble() - 0.5D) * 0.2D,
                        0.25D,
                        (level.getRandom().nextDouble() - 0.5D) * 0.2D);
                level.addFreshEntity(item);
                dropped = true;
            }
        }
        return dropped;
    }

    private static boolean entity(ServerLevel level, BlockPos pos, LuckyOutcome outcome, RandomSource random) {
        ResourceLocation id = ResourceLocation.tryParse(outcome.id());
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return false;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        int count = Math.max(1, Math.min(outcome.count(), 8));
        boolean spawned = false;
        for (int i = 0; i < count; i++) {
            BlockPos at = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
            Entity created = type.spawn(level, at, MobSpawnType.EVENT);
            spawned |= created != null;
        }
        return spawned;
    }

    private static boolean explosion(ServerLevel level, Vec3 centre, LuckyOutcome outcome) {
        float radius = (float) Math.max(1.0D, Math.min(outcome.radius(), 6.0D));
        level.explode(null, centre.x, centre.y, centre.z, radius, outcome.fire(),
                Level.ExplosionInteraction.MOB);
        return true;
    }

    private static boolean command(ServerLevel level, Vec3 centre, LuckyOutcome outcome) {
        String command = outcome.command();
        if (command == null || command.isBlank()) {
            return false;
        }
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack()
                        .withLevel(level)
                        .withPosition(centre)
                        .withPermission(2)
                        .withSuppressedOutput(),
                command);
        return true;
    }

    private static boolean effect(ServerLevel level, Vec3 centre, LuckyOutcome outcome) {
        ResourceLocation id = ResourceLocation.tryParse(outcome.id());
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) {
            return false;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        if (effect == null) {
            return false;
        }
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
        double radius = Math.max(1.0D, Math.min(outcome.radius() <= 0.0D ? 6.0D : outcome.radius(), 16.0D));
        int duration = Math.max(20, Math.min(outcome.duration() <= 0 ? 100 : outcome.duration(), 2400));
        List<LivingEntity> targets = Selection.livingAround(level, centre, radius, null);
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(holder, duration, Math.max(0, Math.min(outcome.amplifier(), 4))));
        }
        return !targets.isEmpty();
    }

    private LuckyOutcomeRunner() {
    }
}
