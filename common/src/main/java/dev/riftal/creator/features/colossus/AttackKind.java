package dev.riftal.creator.features.colossus;

/**
 * Every scripted action the Ashen Colossus can be in, with the server-side timeline that drives it.
 *
 * <p>The server never reads GeckoLib's animation clock - {@code AnimationState#getAnimationTick()}
 * only exists on the render thread. Damage, spawns and knockback fire on {@link #hitTicks()},
 * which are hand-kept in sync with the Blockbench timeline documented in
 * {@code plans/02-ashen-colossus.md} section 6.
 *
 * <p>Pure data, unit-testable.
 */
public enum AttackKind {

    /** Idle: the chooser goal is free to pick something. */
    NONE("none", 0, new int[0], 0),
    /** Rise-from-the-ground entrance. Invulnerable for the whole clip. */
    SPAWN("spawn", 60, new int[0], 0),
    /** Phase-change roar. Invulnerable for the whole clip. */
    ROAR("roar", 35, new int[]{8}, 0),
    /** Two-handed ground slam - expanding shockwave ring starts at the hit tick. */
    SLAM("slam", 40, new int[]{20}, 100),
    /** Both arms up, a volley of ash bombs rains around the target. */
    LAVA_RAIN("lava_rain", 30, new int[]{10}, 120),
    /** Three Ashen Minions claw out of the floor. */
    SUMMON("summon", 30, new int[]{15}, 400),
    /** Phase 3 only: three hits in 2.5 seconds. */
    COMBO("combo", 50, new int[]{10, 22, 38}, 140),
    /** Interrupt. The boss takes double damage while it lasts. */
    STAGGER("stagger", 40, new int[0], 0),
    /** The 70-tick collapse. Loot and XP land on the last tick. */
    DEATH("death", 70, new int[0], 0);

    private final String animName;
    private final int durationTicks;
    private final int[] hitTicks;
    private final int cooldownTicks;

    AttackKind(String animName, int durationTicks, int[] hitTicks, int cooldownTicks) {
        this.animName = animName;
        this.durationTicks = durationTicks;
        this.hitTicks = hitTicks;
        this.cooldownTicks = cooldownTicks;
    }

    /** GeckoLib triggerable-animation name registered on the {@code attack} controller. */
    public String animName() {
        return animName;
    }

    /** How long the goal owns the boss, in ticks. */
    public int durationTicks() {
        return durationTicks;
    }

    /** Ticks into the clip at which the goal deals its damage. Ascending, always inside the clip. */
    public int[] hitTicks() {
        return hitTicks.clone();
    }

    /** Number of scripted hits this attack lands. */
    public int hitCount() {
        return hitTicks.length;
    }

    /** The {@code n}-th hit tick, without allocating. */
    public int hitTick(int index) {
        return hitTicks[index];
    }

    /** Per-attack cooldown started when the goal stops. 0 means "no dedicated cooldown". */
    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** True while the boss may not be interrupted and may not be damaged. */
    public boolean isInvulnerable() {
        return this == SPAWN || this == ROAR;
    }

    /** True for the attacks the chooser goal is allowed to pick. */
    public boolean isChoosable() {
        return this == SLAM || this == LAVA_RAIN || this == SUMMON || this == COMBO;
    }

    /** The synched-data value for this kind. */
    public int id() {
        return ordinal();
    }

    /** Inverse of {@link #id()}. Anything out of range reads as {@link #NONE}. */
    public static AttackKind byId(int id) {
        AttackKind[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    /**
     * Looks a kind up by its enum name, for NBT. Anything unknown - a tag written by an older or
     * newer build - reads as {@link #NONE} rather than throwing on a world load.
     */
    public static AttackKind byName(String name) {
        for (AttackKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return NONE;
    }
}
