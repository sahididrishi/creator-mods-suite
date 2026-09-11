package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.riftal.creator.features.toolkit.ToolkitState;
import dev.riftal.creator.features.toolkit.arena.ArenaSnapshot;
import dev.riftal.creator.features.toolkit.cam.CameraBookmark;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Tab completion for the toolkit's name arguments. Nothing is suggested that does not exist. */
public final class ToolkitSuggestions {

    /** Every registered entity type id, for {@code /toolkit wave spawn}. */
    public static final SuggestionProvider<CommandSourceStack> ENTITY_TYPES =
            (context, builder) -> SharedSuggestionProvider.suggestResource(
                    BuiltInRegistries.ENTITY_TYPE.keySet(), builder);

    /** Saved arena names. */
    public static final SuggestionProvider<CommandSourceStack> ARENAS =
            (context, builder) -> SharedSuggestionProvider.suggest(arenaNames(context), builder);

    /** Saved camera bookmark names. */
    public static final SuggestionProvider<CommandSourceStack> CAMERAS =
            (context, builder) -> SharedSuggestionProvider.suggest(cameraNames(context), builder);

    private static List<String> arenaNames(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<String> names = new ArrayList<>();
        for (ArenaSnapshot snapshot : ToolkitState.get(server).arenas()) {
            names.add(snapshot.name());
        }
        return names;
    }

    private static List<String> cameraNames(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<String> names = new ArrayList<>();
        for (CameraBookmark bookmark : ToolkitState.get(server).cameras()) {
            names.add(bookmark.name());
        }
        return names;
    }

    private ToolkitSuggestions() {
    }
}
