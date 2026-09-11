package dev.riftal.creator.features.toolkit;

import dev.riftal.creator.features.toolkit.arena.ArenaSnapshot;
import dev.riftal.creator.features.toolkit.cam.CameraBookmark;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the toolkit keeps across a restart: the next take number, the saved arenas and the
 * camera bookmarks. Lives on the overworld's {@code DimensionDataStorage} as
 * {@code creatormods_toolkit.dat}.
 *
 * <p>Deliberately <em>not</em> persisted: the freeze flags and the live take. A world that reloads
 * comes back unfrozen and not recording, which is the behaviour a director expects after a crash.
 */
public final class ToolkitState extends SavedData {

    /** Data file name under {@code <world>/data/}. */
    public static final String FILE_ID = "creatormods_toolkit";

    private int nextTakeNumber = 1;
    private final Map<String, ArenaSnapshot> arenas = new LinkedHashMap<>();
    private final Map<String, CameraBookmark> cameras = new LinkedHashMap<>();

    private ToolkitState() {
    }

    /** The state for this server, creating it on first use. Server thread only. */
    public static ToolkitState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    private static SavedData.Factory<ToolkitState> factory() {
        return new SavedData.Factory<>(ToolkitState::new, ToolkitState::load,
                DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
    }

    /** Normalises an arena or camera name so {@code Ring} and {@code ring} are the same slot. */
    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ----- take numbering -------------------------------------------------------------------

    /** The number the next {@code /toolkit take start} will use. */
    public int nextTakeNumber() {
        return nextTakeNumber;
    }

    /**
     * Consumes and returns the next take number.
     *
     * <p>Take numbers are three digits because the log file name is
     * {@code <world>_<date>_take-NNN.log}. Past 999 the counter wraps to 1 rather than sticking:
     * clamping meant a long-running world claimed 999 forever and {@code TakeLog.open} truncated
     * the same file on every single start, silently destroying the previous take's marks. A wrap
     * can only collide with a take shot on the same day, and {@code /toolkit take set} is there for
     * a director who wants to choose.
     */
    public int claimTakeNumber() {
        int claimed = nextTakeNumber;
        nextTakeNumber = claimed >= 999 ? 1 : claimed + 1;
        setDirty();
        return claimed;
    }

    /** Overrides the next take number, clamped to 1..999. */
    public void setNextTakeNumber(int number) {
        nextTakeNumber = Math.max(1, Math.min(999, number));
        setDirty();
    }

    // ----- arenas ---------------------------------------------------------------------------

    public ArenaSnapshot arena(String name) {
        return arenas.get(key(name));
    }

    public Collection<ArenaSnapshot> arenas() {
        return java.util.List.copyOf(arenas.values());
    }

    public void putArena(ArenaSnapshot snapshot) {
        arenas.put(key(snapshot.name()), snapshot);
        setDirty();
    }

    public boolean removeArena(String name) {
        boolean removed = arenas.remove(key(name)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ----- cameras --------------------------------------------------------------------------

    public CameraBookmark camera(String name) {
        return cameras.get(key(name));
    }

    public Collection<CameraBookmark> cameras() {
        return java.util.List.copyOf(cameras.values());
    }

    public void putCamera(CameraBookmark bookmark) {
        cameras.put(key(bookmark.name()), bookmark);
        setDirty();
    }

    public boolean removeCamera(String name) {
        boolean removed = cameras.remove(key(name)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ----- persistence ----------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("nextTake", nextTakeNumber);
        ListTag arenaList = new ListTag();
        for (ArenaSnapshot snapshot : arenas.values()) {
            arenaList.add(snapshot.save());
        }
        tag.put("arenas", arenaList);
        ListTag cameraList = new ListTag();
        for (CameraBookmark bookmark : cameras.values()) {
            cameraList.add(bookmark.save());
        }
        tag.put("cameras", cameraList);
        return tag;
    }

    private static ToolkitState load(CompoundTag tag, HolderLookup.Provider registries) {
        ToolkitState state = new ToolkitState();
        state.nextTakeNumber = Math.max(1, tag.getInt("nextTake"));
        ListTag arenaList = tag.getList("arenas", Tag.TAG_COMPOUND);
        for (int i = 0; i < arenaList.size(); i++) {
            ArenaSnapshot snapshot = ArenaSnapshot.load(arenaList.getCompound(i));
            if (snapshot != null) {
                state.arenas.put(key(snapshot.name()), snapshot);
            }
        }
        ListTag cameraList = tag.getList("cameras", Tag.TAG_COMPOUND);
        for (int i = 0; i < cameraList.size(); i++) {
            CameraBookmark bookmark = CameraBookmark.load(cameraList.getCompound(i));
            if (bookmark != null) {
                state.cameras.put(key(bookmark.name()), bookmark);
            }
        }
        return state;
    }
}
