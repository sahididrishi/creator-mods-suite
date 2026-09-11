package dev.riftal.creator.features.evolve.net;

import dev.riftal.creator.core.net.Payloads;

/**
 * Registration and dispatch for Evolve's three server-to-client payloads.
 *
 * <p>{@link #registerAll()} runs in {@code registerContent()} on <em>both</em> physical sides:
 * Fabric needs the type in {@code PayloadTypeRegistry.playS2C()} on the server or
 * {@code ServerPlayNetworking.send} refuses it, and NeoForge flushes payload registrations inside a
 * mod-bus event that fires long before {@code initClient()}. The handlers themselves bounce through
 * a {@link EvolveClientSink} that only the client ever installs, so no client class is loaded on a
 * dedicated server.
 *
 * <p>There are no client-to-server payloads: every input is a command or ordinary gameplay.
 */
public final class EvolvePayloads {

    private static volatile EvolveClientSink sink;

    /** Declares all three payload types. Call from {@code Feature#registerContent()}. */
    public static void registerAll() {
        Payloads.registerS2C(SyncEvolutionPayload.TYPE, SyncEvolutionPayload.CODEC, EvolvePayloads::handleSync);
        Payloads.registerS2C(TransformFxPayload.TYPE, TransformFxPayload.CODEC, EvolvePayloads::handleTransformFx);
        Payloads.registerS2C(XpPopupPayload.TYPE, XpPopupPayload.CODEC, EvolvePayloads::handleXpPopup);
    }

    /** Installs the client handler. Called from {@code Feature#initClient()} only. */
    public static void setSink(EvolveClientSink clientSink) {
        sink = clientSink;
    }

    private static void handleSync(SyncEvolutionPayload payload) {
        EvolveClientSink target = sink;
        if (target != null) {
            target.onSync(payload);
        }
    }

    private static void handleTransformFx(TransformFxPayload payload) {
        EvolveClientSink target = sink;
        if (target != null) {
            target.onTransformFx(payload);
        }
    }

    private static void handleXpPopup(XpPopupPayload payload) {
        EvolveClientSink target = sink;
        if (target != null) {
            target.onXpPopup(payload);
        }
    }

    private EvolvePayloads() {
    }
}
