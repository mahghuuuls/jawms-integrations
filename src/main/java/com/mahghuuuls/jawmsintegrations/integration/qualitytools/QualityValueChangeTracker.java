package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Retains scalar snapshots only; player and world objects never cross this boundary. */
public final class QualityValueChangeTracker {

    private final Map<UUID, QualityAttributeProjection.Values> snapshots = new HashMap<>();

    public synchronized boolean observe(UUID playerId,
                                        QualityAttributeProjection.Values values,
                                        Runnable refresh) {
        if (playerId == null || values == null || refresh == null) {
            throw new IllegalArgumentException("Tracker inputs must not be null");
        }
        QualityAttributeProjection.Values previous = snapshots.put(playerId, values);
        if (values.equals(previous)) {
            return false;
        }
        refresh.run();
        return true;
    }

    public synchronized void clear(UUID playerId) {
        if (playerId != null) {
            snapshots.remove(playerId);
        }
    }

    synchronized int snapshotCount() {
        return snapshots.size();
    }
}
