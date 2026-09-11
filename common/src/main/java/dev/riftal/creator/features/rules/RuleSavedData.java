package dev.riftal.creator.features.rules;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Per-world persistence for the rule engine, stored as {@code data/creator_rules.dat} in the
 * overworld's {@code DimensionDataStorage}.
 *
 * <p>Holds three things: which rules are on (ordered), whether the HUD list is shown, and one
 * private {@link CompoundTag} per rule for timers like {@code item_roulette}'s next roll.
 */
public final class RuleSavedData extends SavedData {

    /** File name under the world's {@code data/} folder. */
    public static final String FILE_NAME = "creator_rules";

    private static final int VERSION = 1;

    private final List<String> active = new ArrayList<>();
    private CompoundTag ruleState = new CompoundTag();
    private boolean hud = true;

    /** Factory for {@code DimensionDataStorage#computeIfAbsent}. */
    public static SavedData.Factory<RuleSavedData> factory() {
        return new SavedData.Factory<>(RuleSavedData::new, RuleSavedData::load, DataFixTypes.LEVEL);
    }

    /**
     * Rebuilds the state from the tag {@link #save} wrote. Public and static so the round trip can
     * be unit-tested without a {@code DimensionDataStorage}; {@code registries} is unused.
     */
    public static RuleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RuleSavedData loaded = new RuleSavedData();
        ListTag list = tag.getList("active", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (!id.isEmpty() && !loaded.active.contains(id)) {
                loaded.active.add(id);
            }
        }
        loaded.ruleState = tag.getCompound("ruleState").copy();
        loaded.hud = !tag.contains("hud") || tag.getBoolean("hud");
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        ListTag list = new ListTag();
        for (String id : active) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("active", list);
        tag.put("ruleState", ruleState.copy());
        tag.putBoolean("hud", hud);
        return tag;
    }

    /** Ordered ids of the rules that were on when the world was last saved. */
    public List<String> active() {
        return List.copyOf(active);
    }

    /** Replaces the stored active set. */
    public void setActive(Collection<String> ids) {
        active.clear();
        for (String id : ids) {
            if (!active.contains(id)) {
                active.add(id);
            }
        }
        setDirty();
    }

    public boolean hud() {
        return hud;
    }

    public void setHud(boolean value) {
        if (hud != value) {
            hud = value;
            setDirty();
        }
    }

    /** The private tag of one rule. Never null; empty on a fresh world. */
    public CompoundTag ruleState(String ruleId) {
        return ruleState.getCompound(ruleId);
    }

    /** Stores one rule's private tag. */
    public void putRuleState(String ruleId, CompoundTag tag) {
        ruleState.put(ruleId, tag);
        setDirty();
    }
}
