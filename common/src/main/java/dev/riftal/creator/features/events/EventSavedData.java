package dev.riftal.creator.features.events;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The director's world-level state: whichever event is running, and what ran last.
 *
 * <p>Lives in the overworld's {@code DimensionDataStorage} under {@code creator_events_director}
 * regardless of which dimension the event is anchored to - the dimension id is part of the record.
 *
 * <p>{@code DataFixTypes} has no "mod data" member and {@code DimensionDataStorage} dereferences
 * the one it is handed, so {@link DataFixTypes#LEVEL} stands in. It is inert here: the file always
 * carries the current data version, and DataFixerUpper skips a fix run when the versions match.
 */
public final class EventSavedData extends SavedData {

    /** File name under {@code <world>/data/}. */
    public static final String FILE_NAME = "creator_events_director";

    private static final String KEY_VERSION = "version";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_LAST = "last_event";
    private static final int VERSION = 1;

    private CompoundTag active;
    private String lastEventId = "";

    /** Factory for {@code DimensionDataStorage#computeIfAbsent}. */
    public static SavedData.Factory<EventSavedData> factory() {
        return new SavedData.Factory<>(EventSavedData::new, EventSavedData::load, DataFixTypes.LEVEL);
    }

    private static EventSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EventSavedData data = new EventSavedData();
        if (tag.contains(KEY_ACTIVE, Tag.TAG_COMPOUND)) {
            data.active = tag.getCompound(KEY_ACTIVE).copy();
        }
        data.lastEventId = tag.getString(KEY_LAST);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_VERSION, VERSION);
        if (active != null) {
            tag.put(KEY_ACTIVE, active.copy());
        }
        tag.putString(KEY_LAST, lastEventId);
        return tag;
    }

    /** The serialised active event, or null when the director is idle. */
    public CompoundTag active() {
        return active == null ? null : active.copy();
    }

    /** Replaces the active record. Pass null when the event stops. */
    public void setActive(CompoundTag tag) {
        this.active = tag == null ? null : tag.copy();
        setDirty();
    }

    /** Id of the last event that ran, for {@code /event status} when nothing is active. */
    public String lastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String id) {
        this.lastEventId = id == null ? "" : id;
        setDirty();
    }
}
