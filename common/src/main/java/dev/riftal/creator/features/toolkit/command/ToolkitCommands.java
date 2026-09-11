package dev.riftal.creator.features.toolkit.command;

import dev.riftal.creator.core.command.CommandHelper;

/**
 * The one root the Director's Toolkit owns: {@code /toolkit}.
 *
 * <pre>
 * /toolkit take   start | stop | mark [label] | status | set &lt;n&gt;
 * /toolkit freeze mobs &lt;bool&gt; | players &lt;bool&gt; | all &lt;bool&gt; | status
 * /toolkit wave   spawn &lt;entity&gt; &lt;count&gt; &lt;radius&gt; [ring|random [centre]] | clear
 * /toolkit arena  save &lt;name&gt; [from to] | reset &lt;name&gt; | delete &lt;name&gt; | list
 * /toolkit cam    save &lt;name&gt; | go &lt;name&gt; [glideTicks] | del &lt;name&gt; | list
 * /toolkit cheat  god [bool] | fly [bool] | heal | clear
 * /toolkit hide   hud &lt;bool&gt; | chat &lt;bool&gt; | nametags &lt;bool&gt; [targets] | commands &lt;bool&gt;
 * /toolkit tphere &lt;players&gt;
 * </pre>
 *
 * <p>The root itself is open, and so are the branches that only ever change the caller's own screen:
 * {@code take status} for a crew member reading the clapperboard, and {@code hide hud|chat|nametags}
 * so a non-op camera operator can clean their own frame - the plan's "client commands run without
 * server permission". Everything that changes the world, another player, or the server
 * ({@code hide nametags &lt;bool&gt; &lt;targets&gt;}, {@code hide commands}, and every other subtree) requires
 * permission level 2.
 *
 * <p>There is deliberately no {@code /take}, {@code /freeze} or {@code /camgo} alias.
 * {@code CONTRACT.md} §5.2 gives this feature exactly one command root, its own word, and eight
 * features each claiming four or five short English verbs at the top level is how command trees
 * collide - with each other and with the map, chat and admin mods a crew already runs.
 */
public final class ToolkitCommands {

    /** Queues the whole tree with the core command helper. Call from {@code registerContent()}. */
    public static void register() {
        CommandHelper.register(dispatcher -> dispatcher.register(CommandHelper.literal("toolkit")
                .then(TakeCommands.build())
                .then(FreezeCommands.build())
                .then(WaveCommands.build())
                .then(ArenaCommands.build())
                .then(CamCommands.build())
                .then(CheatCommands.build())
                .then(HideCommands.build())
                .then(TpHereCommands.build())));
    }

    private ToolkitCommands() {
    }
}
