package dev.riftal.creator.features.arsenal.command;

import dev.riftal.creator.core.registry.RegistryEntry;
import dev.riftal.creator.features.arsenal.ArsenalFeature;
import dev.riftal.creator.features.arsenal.item.GrappleBladeItem;
import dev.riftal.creator.features.arsenal.item.GravityHammerItem;
import dev.riftal.creator.features.arsenal.item.SoulScytheItem;
import dev.riftal.creator.features.arsenal.item.StormBowItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The four signature weapons, as a command argument and a give-list. */
public enum Weapon {

    GRAPPLE_BLADE(GrappleBladeItem.PATH, () -> ArsenalFeature.GRAPPLE_BLADE),
    STORM_BOW(StormBowItem.PATH, () -> ArsenalFeature.STORM_BOW),
    GRAVITY_HAMMER(GravityHammerItem.PATH, () -> ArsenalFeature.GRAVITY_HAMMER),
    SOUL_SCYTHE(SoulScytheItem.PATH, () -> ArsenalFeature.SOUL_SCYTHE);

    private final String id;
    private final Supplier<RegistryEntry<Item>> entry;

    Weapon(String id, Supplier<RegistryEntry<Item>> entry) {
        this.id = id;
        this.entry = entry;
    }

    /** The command-argument spelling, which is also the registry path. */
    public String id() {
        return this.id;
    }

    /** The registered item. Only safe once the loader has flushed the registries. */
    public Item item() {
        return this.entry.get().get();
    }

    /** Lookup by command argument. Empty for anything unknown. */
    public static Optional<Weapon> byId(String id) {
        for (Weapon weapon : values()) {
            if (weapon.id.equals(id)) {
                return Optional.of(weapon);
            }
        }
        return Optional.empty();
    }

    /** Every id, for suggestions and for the "unknown weapon" error. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(values().length);
        for (Weapon weapon : values()) {
            out.add(weapon.id);
        }
        return out;
    }
}
