package dev.riftal.creator.core.net;

import net.minecraft.server.level.ServerPlayer;

/**
 * Handles a client-to-server payload. Runs on the server thread.
 *
 * <p>Validate everything: distance, cooldown, permission, ownership. The payload is attacker
 * controlled.
 */
@FunctionalInterface
public interface ServerHandler<T> {
    void handle(T payload, ServerPlayer sender);
}
