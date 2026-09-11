package dev.riftal.creator.features.evolve.net;

/**
 * What the client half of Evolve does with an incoming payload.
 *
 * <p>This interface exists so that {@link EvolvePayloads} - which must be reachable from
 * {@code registerContent()} on a dedicated server - never names a client-only class. The client
 * implementation is installed from {@code EvolveFeature#initClient()} through
 * {@link EvolvePayloads#setSink}; on a dedicated server nothing ever installs one and every packet
 * handler is a no-op.
 */
public interface EvolveClientSink {

    void onSync(SyncEvolutionPayload payload);

    void onTransformFx(TransformFxPayload payload);

    void onXpPopup(XpPopupPayload payload);
}
