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
 * /toolkit cam    save &lt;name&gt; | go &lt;name&gt; | del &lt;name&gt; | list
 * /toolkit cheat  god [bool] | fly [bool] | heal | clear
 * /toolkit hide   hud &lt;bool&gt; | chat &lt;bool&gt; | nametags &lt;bool&gt; | commands &lt;bool&gt;
 * /toolkit tphere &lt;players&gt;
 * </pre>
 *
 * <p>The root itself is open so {@code /toolkit take status} works for a non-op crew member reading
 * the clapperboard; every branch that changes anything requires permission level 2.
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
