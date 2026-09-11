package dev.riftal.creator.features.arsenal.item;

import dev.riftal.creator.core.CreatorMods;
import dev.riftal.creator.core.util.Fx;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.mechanic.DamageMath;
import dev.riftal.creator.features.arsenal.mechanic.SoulWisp;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scythe that drinks what it cuts.
 *
 * <p>Three mechanics, all driven from vanilla's own melee hooks so no mixin is needed:
 * <ul>
 *   <li><b>Exact lifesteal.</b> {@code Player#attack} calls
 *       {@code Item#getAttackDamageBonus(Entity, float, DamageSource)} immediately <em>before</em>
 *       {@code target.hurt(...)} and {@code Item#postHurtEnemy(...)} immediately after, and those
 *       are the only two call sites of either method in 1.21.1. We record health plus absorption in
 *       the first and subtract in the second, which yields the true damage dealt - including zero
 *       when the target was still in its invulnerability window.</li>
 *   <li><b>Always-on sweep.</b> Vanilla's sweep only fires while grounded, unsprinting and
 *       uncritting, and its damage is computed inline in {@code Player#attack}, so there is no
 *       cross-loader hook to force it. We run vanilla's own selection box and 3-block check
 *       ourselves and call {@code Player#sweepAttack()} for the particle. A re-entrancy guard keeps
 *       the sweep's own {@code hurt} calls from recursing.</li>
 *   <li><b>Souls.</b> Anything that dies to the scythe - the struck target or a swept neighbour -
 *       releases a {@link SoulWisp}.</li>
 * </ul>
 */
public class SoulScytheItem extends SwordItem {

    /** Registry path, also the lang-key suffix. */
    public static final String PATH = "soul_scythe";

    /** Extra sweeping ratio this weapon carries, on top of the base sword modifiers. */
    public static final double SWEEP_MODIFIER = 0.75D;

    /** Extra entity interaction range, in blocks. */
    public static final double REACH_MODIFIER = 0.5D;

    /** Vanilla's sweep selection box, and the 3-block radius check that goes with it. */
    private static final double SWEEP_RANGE_SQ = 9.0D;

    private static final Map<UUID, PendingHit> PENDING = new HashMap<>();

    private static boolean sweeping;

    private record PendingHit(int targetId, float healthBefore) {
    }

    public SoulScytheItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    /**
     * Base sword modifiers plus the scythe's sweeping ratio and reach, built in one builder -
     * {@code ItemAttributeModifiers} has no merge, so the whole set has to be declared at once.
     */
    public static ItemAttributeModifiers attributes(Tier tier, int attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                attackDamage + tier.getAttackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.SWEEPING_DAMAGE_RATIO,
                        new AttributeModifier(ArsenalFeature.res("scythe_sweep"), SWEEP_MODIFIER,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ArsenalFeature.res("scythe_reach"), REACH_MODIFIER,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /**
     * Not actually a damage bonus - this is the only vanilla hook that fires with the target in hand
     * just before it is hurt, so it is where the pre-hit health snapshot is taken. Always returns 0.
     */
    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        if (CreatorMods.isEnabled(ArsenalFeature.ID)
                && !sweeping
                && target instanceof LivingEntity living
                && damageSource.getEntity() instanceof Player attacker
                && !attacker.level().isClientSide) {
            PENDING.put(attacker.getUUID(), new PendingHit(living.getId(), effectiveHealth(living)));
        }
        return 0.0F;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (sweeping || !CreatorMods.isEnabled(ArsenalFeature.ID)) {
            return;
        }
        if (!(attacker instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        PendingHit snapshot = PENDING.remove(player.getUUID());
        float dealt = 0.0F;
        if (snapshot != null && snapshot.targetId() == target.getId()) {
            dealt = Math.max(0.0F, snapshot.healthBefore() - effectiveHealth(target));
        }

        lifesteal(level, player, dealt);
        sweep(level, player, target, dealt);
        if (target.isDeadOrDying()) {
            releaseSoul(level, target, player);
        }
    }

    private static void lifesteal(ServerLevel level, ServerPlayer player, float dealt) {
        float healed = DamageMath.lifesteal(dealt);
        if (healed <= 0.0F) {
            return;
        }
        player.heal(healed);
        if (healed >= 1.0F) {
            Fx.particles(level, ParticleTypes.HEART,
                    player.position().add(0.0D, player.getBbHeight() + 0.2D, 0.0D), 1, 0.2D, 0.0D);
        }
    }

    private static void sweep(ServerLevel level, ServerPlayer player, LivingEntity target, float dealt) {
        sweeping = true;
        try {
            float sweepDamage = DamageMath.sweepDamage(dealt);
            DamageSource source = level.damageSources().playerAttack(player);
            double yaw = player.getYRot() * (Math.PI / 180.0D);
            double knockX = Math.sin(yaw);
            double knockZ = -Math.cos(yaw);

            for (LivingEntity neighbour : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
                if (neighbour == target || neighbour == player || !neighbour.isAlive()) {
                    continue;
                }
                if (player.isAlliedTo(neighbour)) {
                    continue;
                }
                if (neighbour instanceof ArmorStand stand && stand.isMarker()) {
                    continue;
                }
                if (player.distanceToSqr(neighbour) >= SWEEP_RANGE_SQ) {
                    continue;
                }
                neighbour.knockback(0.4D, knockX, knockZ);
                if (sweepDamage > 0.0F) {
                    neighbour.hurt(source, sweepDamage);
                }
                if (neighbour.isDeadOrDying()) {
                    releaseSoul(level, neighbour, player);
                }
            }

            player.sweepAttack();
            Fx.sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
        } finally {
            sweeping = false;
        }
    }

    private static void releaseSoul(ServerLevel level, LivingEntity victim, ServerPlayer killer) {
        Vec3 origin = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        SoulWisp.launch(level, origin, killer);
    }

    private static float effectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!isSelected || !CreatorMods.isEnabled(ArsenalFeature.ID)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)) {
            return;
        }
        // Inventory.tick() restarts its slot index per compartment, so isSelected can be true for an
        // armour or off-hand slot too. Only the real main-hand stack trails.
        if (player.getMainHandItem() != stack) {
            return;
        }
        if (!player.isAlive() || !player.isSprinting() || serverLevel.getGameTime() % 2L != 0L) {
            return;
        }
        Fx.particles(serverLevel, ParticleTypes.SOUL_FIRE_FLAME,
                player.position().add(0.0D, 0.1D, 0.0D), 2, 0.1D, 0.0D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        WeaponTooltips.weapon(tooltipComponents, PATH, ChatFormatting.DARK_AQUA);
    }
}
