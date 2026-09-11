package dev.riftal.creator.core.net;

/** Handles a server-to-client payload. Runs on the client thread. Client only. */
@FunctionalInterface
public interface ClientHandler<T> {
    void handle(T payload);
}
