package dev.riftal.creator.features.arsenal.net;

import dev.riftal.creator.features.arsenal.client.ArsenalClientFx;

/**
 * The receiver side of {@link GrappleFxPayload}, as a class that is safe to load on a dedicated
 * server.
 *
 * <p>{@code Feature#registerContent()} runs on both physical sides and must not touch a client-only
 * class. Handing {@code Payloads.registerS2C} a method reference straight to the client handler
 * broke that in a way that is easy to miss: a lambda is <em>linked</em> where its
 * {@code invokedynamic} executes - inside {@code registerContent()} on a dedicated server - and
 * {@code LambdaMetafactory} has to resolve a {@code MethodHandle} to the target method, which loads
 * and verifies the class that declares it. Today
 * {@code dev.riftal.creator.features.arsenal.client.ArsenalClientFx} happens to verify without
 * resolving {@code Minecraft} or {@code ClientLevel}, so it survives; one added line that made the
 * verifier prove a client type against a non-client supertype would turn it into a
 * {@code NoClassDefFoundError} during mod construction, in production only, on the one loader
 * (Fabric) whose server jar really has no {@code net.minecraft.client}.
 *
 * <p>The payload <em>type</em> still has to be registered on both sides or the server cannot send
 * it, so moving the whole call into {@code initClient()} is not an option. Instead the lambda
 * targets this class, which is ordinary common code; the client class is named only inside a method
 * body, where the JVM resolves it lazily - on first execution, which only ever happens on a client
 * (Fabric registers the receiver at all only when {@code isClient()}, and NeoForge's
 * {@code playToClient} handlers run client side).
 */
public final class GrappleFxHandler {

    /** Client thread. Forwards to the client-only effect code. */
    public static void play(GrappleFxPayload payload) {
        // Named only here, in a method body: javac emits an ordinary invokestatic and the JVM
        // resolves ArsenalClientFx on first execution, never at class-load or verification time.
        ArsenalClientFx.play(payload);
    }

    private GrappleFxHandler() {
    }
}
