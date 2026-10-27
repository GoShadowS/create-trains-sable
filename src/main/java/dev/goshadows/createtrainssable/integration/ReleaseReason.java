package dev.goshadows.createtrainssable.integration;

public enum ReleaseReason {
    /** The train was reattached to its TrackGraph. */
    RESYNC,
    /** The wreck could not be recovered; Create keeps the train in its derailed state. */
    ABANDON,
    /** The server is shutting down. */
    SERVER_STOP
}
