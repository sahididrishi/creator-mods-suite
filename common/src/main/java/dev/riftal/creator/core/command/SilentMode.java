package dev.riftal.creator.core.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/**
 * Per-server "don't pollute my recording" toggle.
 *
 * <p>When silent mode is on, {@link CommandHelper#feedback} routes replies to the action bar
 * instead of chat, and the two chat-noisy game rules ({@code sendCommandFeedback},
 * {@code logAdminCommands}) are forced off so vanilla commands stop echoing too. Turning it back
 * off restores whatever the rules were before.
 *
 * <p>The rules are set directly on {@link GameRules} rather than through {@code /gamerule}, because
 * {@code /gamerule} itself prints a line.
 */
public final class SilentMode {

    private static boolean silent;
    private static boolean previousCommandFeedback = true;
    private static boolean previousAdminLogging = true;

    /** True when replies should go to the action bar instead of chat. */
    public static boolean isSilent(MinecraftServer server) {
        if (silent) {
            return true;
        }
        return server != null && !server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    /** Global flag only, for code that has no server handy. */
    public static boolean isSilent() {
        return silent;
    }

    /** Turns silent mode on or off, saving and restoring the affected game rules. */
    public static void set(MinecraftServer server, boolean newValue) {
        if (newValue == silent) {
            return;
        }
        silent = newValue;
        if (server == null) {
            return;
        }
        GameRules rules = server.getGameRules();
        if (newValue) {
            previousCommandFeedback = rules.getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
            previousAdminLogging = rules.getBoolean(GameRules.RULE_LOGADMINCOMMANDS);
            rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, server);
            rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(false, server);
        } else {
            rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(previousCommandFeedback, server);
            rules.getRule(GameRules.RULE_LOGADMINCOMMANDS).set(previousAdminLogging, server);
        }
    }

    /** Called by the loader on server stop so a fresh world starts loud. */
    public static void reset() {
        silent = false;
        previousCommandFeedback = true;
        previousAdminLogging = true;
    }

    private SilentMode() {
    }
}
