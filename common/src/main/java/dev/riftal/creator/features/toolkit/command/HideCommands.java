package dev.riftal.creator.features.toolkit.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.riftal.creator.core.command.CommandHelper;
import dev.riftal.creator.core.command.SilentMode;
import dev.riftal.creator.features.toolkit.ToolkitRuntime;
import dev.riftal.creator.features.toolkit.net.HideStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * {@code /toolkit hide hud|chat|nametags|commands <on|off>} - the clean-frame switches.
 *
 * <p>The three visual switches are per player and only ever affect the player who typed the command,
 * so they carry <b>no permission gate</b>: a non-op crew member has to be able to clean their own
 * frame, which is what the plan means by "client commands run without server permission". An op can
 * still never blank somebody else's screen - there is no target argument for hud or chat.
 *
 * <p>{@code nametags} is the exception, and it has two halves:
 * <ul>
 *   <li>no target - hides name tags on the caller's own client, through the S2C payload and
 *       {@code ToolkitEntityRendererMixin}. Needs the mod on that client.</li>
 *   <li>{@code <targets>} - op only, and the <b>vanilla</b> path: the named players go into a
 *       scoreboard team whose name-tag visibility is {@code NEVER}, so a camera operator running a
 *       stock client still films a crew with no floating names. Exactly
 *       {@code /team modify creator_toolkit_crew nametagVisibility never}, without the chat line.</li>
 * </ul>
 *
 * <p>{@code commands} is the server-wide silent mode that moves all feedback to the action bar, and
 * that one is op only.
 */
public final class HideCommands {

    /** Scoreboard team used for the vanilla name-tag fallback. */
    public static final String CREW_TEAM = "creator_toolkit_crew";

    private static final HideStatePayload NOTHING_HIDDEN = new HideStatePayload(false, false, false);

    /** What each player has hidden. Purely a mirror so a second command can flip one switch. */
    private static final Map<UUID, HideStatePayload> STATE = new HashMap<>();

    /**
     * The two game rules as they were before <em>this feature</em> turned silent mode on, or null
     * when we did not turn it on.
     *
     * <p>Core's {@code SilentMode} keeps its own copy, but it hands it back only through
     * {@code set(server, false)}, and the loader calls {@code SilentMode.reset()} - which zeroes
     * those statics without touching the world - from a listener injected at the HEAD of the very
     * same {@code stopServer} our shutdown mixin uses. Mixin order between two configs at one
     * injection point is undefined, so on a bad roll core's reset wins, {@code set(server, false)}
     * becomes a no-op, and {@code sendCommandFeedback=false} is written into level.dat for good.
     * Our own snapshot does not depend on that race.
     */
    private static Boolean savedCommandFeedback;
    private static Boolean savedAdminLogging;

    /** The {@code hide} subtree. The visual switches are open; {@code commands} needs op. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return CommandHelper.literal("hide")
                .then(CommandHelper.literal("hud")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.HUD,
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("chat")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.CHAT,
                                        BoolArgumentType.getBool(ctx, "on")))))
                .then(CommandHelper.literal("nametags")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx.getSource(), Part.NAMETAGS,
                                        BoolArgumentType.getBool(ctx, "on")))
                                // Hiding somebody *else's* name tag is a server-wide change, so it
                                // is the one branch of /hide that is still op only.
                                .then(CommandHelper.arg("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(CommandHelper.OP_LEVEL))
                                        .executes(HideCommands::crew))))
                .then(CommandHelper.op("commands")
                        .then(CommandHelper.arg("on", BoolArgumentType.bool())
                                .executes(ctx -> silent(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "on")))));
    }

    /** The state the server believes {@code player} is in. Never null. */
    public static HideStatePayload stateFor(ServerPlayer player) {
        return STATE.getOrDefault(player.getUUID(), NOTHING_HIDDEN);
    }

    /** Forgets one player's flags, on disconnect. */
    public static void forget(UUID player) {
        if (player != null) {
            STATE.remove(player);
        }
    }

    /** Drops the per-player mirror; the client resets its own flags on disconnect. */
    public static void reset() {
        STATE.clear();
        savedCommandFeedback = null;
        savedAdminLogging = null;
    }

    /**
     * Puts {@code sendCommandFeedback} and {@code logAdminCommands} back if this feature switched
     * them off and never switched them on again.
     *
     * <p>Called from {@code ToolkitRuntime#onServerStopping}, i.e. at the HEAD of
     * {@code MinecraftServer#stopServer}, which is before the worlds are saved - so the restored
     * values are the ones that reach level.dat. Closing a world mid-shoot must not leave the
     * director with a permanently silent save.
     *
     * @return true if a restore was needed and performed
     */
    public static boolean restoreGameRules(MinecraftServer server) {
        if (server == null || savedCommandFeedback == null) {
            return false;
        }
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(savedCommandFeedback, server);
        rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(savedAdminLogging, server);
        savedCommandFeedback = null;
        savedAdminLogging = null;
        return true;
    }

    private enum Part {
        HUD, CHAT, NAMETAGS
    }

    private static int set(CommandSourceStack source, Part part, boolean value) {
        ToolkitRuntime.bind(source.getServer());
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return CommandHelper.error(source, ToolkitText.of("player_only"));
        }
        HideStatePayload current = stateFor(player);
        HideStatePayload updated = switch (part) {
            case HUD -> new HideStatePayload(value, current.chat(), current.nametags());
            case CHAT -> new HideStatePayload(current.hud(), value, current.nametags());
            case NAMETAGS -> new HideStatePayload(current.hud(), current.chat(), value);
        };
        STATE.put(player.getUUID(), updated);
        ToolkitNet.send(player, updated);
        return CommandHelper.success(source, ToolkitText.of(
                switch (part) {
                    case HUD -> "hide.hud";
                    case CHAT -> "hide.chat";
                    case NAMETAGS -> "hide.nametags";
                },
                ToolkitText.onOff(value)));
    }

    /**
     * The vanilla-client fallback: put (or take) the named players in a scoreboard team whose name
     * tags are never drawn. The mod-side payload goes out too, so a crew member who <em>does</em>
     * have the mod is covered by both paths and needs no second command.
     */
    private static int crew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        boolean hidden = BoolArgumentType.getBool(ctx, "on");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        if (targets.isEmpty()) {
            return CommandHelper.error(source, ToolkitText.of("tphere.none"));
        }

        StringJoiner names = new StringJoiner(", ");
        for (ServerPlayer target : targets) {
            String name = target.getGameProfile().getName();
            setCrewNameTagsHidden(server, name, hidden);
            HideStatePayload current = stateFor(target);
            HideStatePayload updated =
                    new HideStatePayload(current.hud(), current.chat(), hidden);
            STATE.put(target.getUUID(), updated);
            ToolkitNet.send(target, updated);
            names.add(name);
        }
        return CommandHelper.success(source, ToolkitText.of("hide.nametags.crew",
                names.toString(), ToolkitText.onOff(hidden)));
    }

    /**
     * Adds or removes one player name from the {@value #CREW_TEAM} scoreboard team, whose name-tag
     * visibility is {@code NEVER}.
     *
     * <p>This is the half of {@code /toolkit hide nametags} that works on a crew member running a
     * stock client: no mod, no payload, no renderer mixin - just the vanilla team flag the server
     * sends to everyone. Takes a name rather than a player so it can be exercised from a GameTest,
     * which has no connected players.
     *
     * <p>Same caveat as vanilla {@code /team join}: a player already on another team is moved out
     * of it, and switching back off leaves them on no team rather than restoring the old one.
     *
     * @return true if the team exists and the name is in it afterwards
     */
    public static boolean setCrewNameTagsHidden(MinecraftServer server, String playerName,
                                                boolean hidden) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(CREW_TEAM);
        if (hidden) {
            if (team == null) {
                team = scoreboard.addPlayerTeam(CREW_TEAM);
                team.setDisplayName(ToolkitText.of("hide.nametags.crew.team"));
            }
            team.setNameTagVisibility(Team.Visibility.NEVER);
            scoreboard.addPlayerToTeam(playerName, team);
            return true;
        }
        if (team == null) {
            return false;
        }
        // removePlayerFromTeam(name, team) throws when the name is on a different team, so this
        // has to be identity against the scoreboard's own index, not just the member list.
        if (scoreboard.getPlayersTeam(playerName) == team) {
            scoreboard.removePlayerFromTeam(playerName, team);
        }
        // An empty team left lying around would show up in /team list on camera.
        if (team.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(team);
        }
        return false;
    }

    private static int silent(CommandSourceStack source, boolean value) {
        MinecraftServer server = ToolkitRuntime.bind(source.getServer());
        // Announce the change while output is still visible, then flip.
        if (value) {
            Component notice = ToolkitText.of("hide.commands.on");
            source.sendSuccess(() -> notice, false);
            setSilent(server, true);
            return 1;
        }
        setSilent(server, false);
        return CommandHelper.success(source, ToolkitText.of("hide.commands.off"));
    }

    /**
     * Flips server-wide silent mode and remembers what the two game rules were, so
     * {@link #restoreGameRules} can put them back even if core's copy has already been zeroed.
     *
     * <p>Public so the GameTest can drive the same path the command does.
     */
    public static void setSilent(MinecraftServer server, boolean value) {
        if (value) {
            // Snapshot first, and only when we are the ones flipping it: if silent mode was
            // already on (say from /creator silent) the rules are already false and remembering
            // those would restore the wrong thing at shutdown.
            if (server != null && !SilentMode.isSilent()) {
                GameRules rules = server.getGameRules();
                savedCommandFeedback = rules.getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
                savedAdminLogging = rules.getBoolean(GameRules.RULE_LOGADMINCOMMANDS);
            }
            SilentMode.set(server, true);
            return;
        }
        SilentMode.set(server, false);
        savedCommandFeedback = null;
        savedAdminLogging = null;
    }

    private HideCommands() {
    }
}
