package dev.riftal.creator.features.powers.ability;

import dev.riftal.creator.features.powers.ability.impl.DashAbility;
import dev.riftal.creator.features.powers.ability.impl.EnderPullAbility;
import dev.riftal.creator.features.powers.ability.impl.FireBurstAbility;
import dev.riftal.creator.features.powers.ability.impl.GroundPoundAbility;
import dev.riftal.creator.features.powers.ability.impl.MobFreezeAbility;
import dev.riftal.creator.features.powers.ability.impl.ShieldDomeAbility;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The six abilities, in canonical slot order. Not a vanilla registry: the set is fixed, it is not
 * data-driven, and nothing needs a network id for it.
 *
 * <p>{@link #bootstrap()} is called from {@code PowersFeature#registerContent()} and is idempotent,
 * so the client, the integrated server and a dedicated server all end up with the same ordered
 * list.
 */
public final class AbilityRegistry {

    private static final List<Ability> ORDERED = new ArrayList<>();
    private static final Map<ResourceLocation, Ability> BY_ID = new LinkedHashMap<>();

    /** Fills the registry with the six MVP abilities. Safe to call more than once. */
    public static synchronized void bootstrap() {
        if (!ORDERED.isEmpty()) {
            return;
        }
        register(new DashAbility());
        register(new FireBurstAbility());
        register(new GroundPoundAbility());
        register(new EnderPullAbility());
        register(new ShieldDomeAbility());
        register(new MobFreezeAbility());
    }

    /** Appends an ability to the slot order. Duplicate ids are rejected. */
    public static synchronized void register(Ability ability) {
        if (BY_ID.containsKey(ability.id())) {
            throw new IllegalStateException("Duplicate ability id " + ability.id());
        }
        BY_ID.put(ability.id(), ability);
        ORDERED.add(ability);
    }

    /** Every ability in slot order. */
    public static List<Ability> ordered() {
        return List.copyOf(ORDERED);
    }

    /** Every ability id in slot order - the suggestion list for the commands. */
    public static List<ResourceLocation> ids() {
        return ORDERED.stream().map(Ability::id).toList();
    }

    /** Look one up, or empty for an id that is not one of the six. */
    public static Optional<Ability> get(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** Canonical slot index of {@code id}, or {@code -1}. */
    public static int canonicalSlot(ResourceLocation id) {
        for (int i = 0; i < ORDERED.size(); i++) {
            if (ORDERED.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** How many abilities exist. */
    public static int size() {
        return ORDERED.size();
    }

    private AbilityRegistry() {
    }
}
