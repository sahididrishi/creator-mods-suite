package dev.riftal.creator.features.toolkit;

import static dev.riftal.creator.Constants.LOG;

import dev.riftal.creator.core.Feature;
import dev.riftal.creator.core.data.PlayerData;
import dev.riftal.creator.core.net.Payloads;
import dev.riftal.creator.features.toolkit.cheat.CheatFlags;
import dev.riftal.creator.features.toolkit.cheat.CheatManager;
import dev.riftal.creator.features.toolkit.client.ClientToolkitState;
import dev.riftal.creator.features.toolkit.client.ToolkitClient;
import dev.riftal.creator.features.toolkit.command.ToolkitCommands;
import dev.riftal.creator.features.toolkit.net.FreezeStatePayload;
import dev.riftal.creator.features.toolkit.net.HideStatePayload;
import dev.riftal.creator.features.toolkit.net.MarkPressedPayload;
import dev.riftal.creator.features.toolkit.net.RequestSyncPayload;
import dev.riftal.creator.features.toolkit.net.TakeStatePayload;
import dev.riftal.creator.features.toolkit.net.ToolkitPayloadHandlers;

/**
 * Director's Toolkit - see {@code plans/01-directors-toolkit.md}.
 *
 * <p>Recording controls: take timer, freeze, wave spawning, arena snapshot and restore, camera
 * bookmarks, HUD hiding, silent commands and creator cheats. Everything hangs off one command root,
 * {@code /toolkit}, and one HUD layer.
 *
 * <p>This feature owns, and nothing else:
 * <ul>
 *   <li>{@code common/src/main/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code common/src/main/resources/assets/creator_toolkit/**} and {@code data/creator_toolkit/**}</li>
 *   <li>{@code common/src/main/resources/creatormods-toolkit.mixins.json}</li>
 *   <li>{@code common/src/test/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code fabric|neoforge/src/main/java/dev/riftal/creator/features/toolkit/**}</li>
 *   <li>{@code fabric/src/gametest/java/dev/riftal/creator/features/toolkit/**}</li>
 * </ul>
 * See {@code CONTRACT.md} in the repo root.
 */
public final class ToolkitFeature implements Feature {

    /** Feature id, resource namespace suffix and mixin config suffix. */
    public static final String ID = "toolkit";

    /** Resource namespace owned by this feature. */
    public static final String NAMESPACE = "creator_toolkit";

    /**
     * Key code the mark key listens on: {@code M}, i.e. {@code GLFW_KEY_M} / {@code InputConstants.KEY_M}.
     *
     * <p>Spelled as a literal because {@code InputConstants} is {@code @OnlyIn(Dist.CLIENT)} and this
     * class is loaded on dedicated servers. A compile-time constant costs no class loading.
     */
    public static final int MARK_KEY = 77;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        CheatManager.flags = PlayerData.register(rl("cheats"), CheatFlags.CODEC,
                () -> CheatFlags.NONE, true);

        // C2S. Never trusted: both handlers re-check permission level and rate limit the sender.
        Payloads.registerC2S(MarkPressedPayload.TYPE, MarkPressedPayload.CODEC,
                ToolkitPayloadHandlers::onMarkPressed);
        Payloads.registerC2S(RequestSyncPayload.TYPE, RequestSyncPayload.CODEC,
                ToolkitPayloadHandlers::onRequestSync);

        // S2C, registered on BOTH sides on purpose. The loader helpers register the payload *type*
        // in the same call as the receiver, so a dedicated server that never runs initClient() may
        // not send them at all - and on NeoForge the registrar is flushed right after construction,
        // long before initClient() would fire. The lambda bodies only ever run on a client, so
        // ClientToolkitState (which touches net.minecraft.client) is never loaded on a server.
        Payloads.registerS2C(TakeStatePayload.TYPE, TakeStatePayload.CODEC,
                payload -> ClientToolkitState.onTakeState(payload));
        Payloads.registerS2C(FreezeStatePayload.TYPE, FreezeStatePayload.CODEC,
                payload -> ClientToolkitState.onFreezeState(payload));
        Payloads.registerS2C(HideStatePayload.TYPE, HideStatePayload.CODEC,
                payload -> ClientToolkitState.onHideState(payload));

        ToolkitCommands.register();
    }

    @Override
    public void initCommon() {
        LOG.info("[toolkit] ready - /toolkit take|freeze|wave|arena|cam|cheat|hide|tphere");
    }

    @Override
    public void initClient() {
        ToolkitClient.init();
    }

    @Override
    public void initServer() {
        // Nothing dedicated-server specific: every command and payload above works on both sides.
    }
}
