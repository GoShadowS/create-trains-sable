package dev.goshadows.createtrainssable.derailment;

import dev.goshadows.createtrainssable.integration.ReleaseReason;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded registry of active physics sessions, keyed by train UUID. */
public final class SessionRegistry {

    private final Map<UUID, PhysicsSession> sessions = new LinkedHashMap<>();

    public boolean contains(UUID trainId) {
        return sessions.containsKey(trainId);
    }

    public int size() {
        return sessions.size();
    }

    public void add(PhysicsSession session) {
        sessions.put(session.trainId(), session);
    }

    public Iterator<Map.Entry<UUID, PhysicsSession>> iterator() {
        return sessions.entrySet().iterator();
    }

    public Iterable<PhysicsSession> all() {
        return sessions.values();
    }

    public void clear(ReleaseReason reason) {
        for (PhysicsSession session : sessions.values()) {
            session.release(reason);
        }
        sessions.clear();
    }
}
