package dev.riftal.creator.features.colossus.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Per-level store of the named arenas {@code /colossus arena set} writes.
 *
 * <p>Saved to {@code creator_colossus_arenas.dat} next to the level's other saved data, so the
 * recording set-up survives a restart.
 */
public final class ArenaSavedData extends SavedData {

    /** File name inside the dimension's {@code data/} folder, without the extension. */
    public static final String FILE_NAME = "creator_colossus_arenas";

    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaSavedData() {
    }

    /** The store for this level, creating an empty one on first use. */
    public static ArenaSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), FILE_NAME);
    }

    private static SavedData.Factory<ArenaSavedData> factory() {
        return new SavedData.Factory<>(ArenaSavedData::new, ArenaSavedData::load, DataFixTypes.LEVEL);
    }

    private static ArenaSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ArenaSavedData data = new ArenaSavedData();
        ListTag list = tag.getList("Arenas", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Arena arena = Arena.load(list.getCompound(i));
            if (!arena.name().isEmpty()) {
                data.arenas.put(arena.name(), arena);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Arena arena : arenas.values()) {
            list.add(arena.save());
        }
        tag.put("Arenas", list);
        return tag;
    }

    /** Lower-cased, trimmed arena key. Keeps {@code Main} and {@code main} the same arena. */
    public static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<Arena> find(String name) {
        return Optional.ofNullable(arenas.get(normalise(name)));
    }

    public void put(Arena arena) {
        arenas.put(normalise(arena.name()), new Arena(normalise(arena.name()), arena.centre(), arena.radius()));
        setDirty();
    }

    /** Removes one arena. Returns true when something was actually removed. */
    public boolean remove(String name) {
        if (arenas.remove(normalise(name)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Removes every arena and returns how many went. */
    public int clear() {
        int removed = arenas.size();
        if (removed > 0) {
            arenas.clear();
            setDirty();
        }
        return removed;
    }

    public List<String> names() {
        return new ArrayList<>(arenas.keySet());
    }

    public int size() {
        return arenas.size();
    }
}
