package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityValueChangeTrackerTest {

    @Test
    void firstObservationAndEachValueChangeRefreshExactlyOnce() {
        QualityValueChangeTracker tracker = new QualityValueChangeTracker();
        UUID player = UUID.randomUUID();
        AtomicInteger refreshes = new AtomicInteger();

        assertTrue(tracker.observe(player, values(5.0D, 0.0D, 0.0D), refreshes::incrementAndGet));
        assertFalse(tracker.observe(player, values(5.0D, 0.0D, 0.0D), refreshes::incrementAndGet));
        assertTrue(tracker.observe(player, values(10.0D, 0.0D, 0.0D), refreshes::incrementAndGet));
        assertTrue(tracker.observe(player, QualityAttributeProjection.Values.ZERO, refreshes::incrementAndGet));

        assertEquals(3, refreshes.get());
        assertEquals(1, tracker.snapshotCount());
    }

    @Test
    void playersAreIsolatedAndClearForcesNextObservationToRefresh() {
        QualityValueChangeTracker tracker = new QualityValueChangeTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AtomicInteger refreshes = new AtomicInteger();
        QualityAttributeProjection.Values values = values(8.0D, 4.0D, 2.0D);

        tracker.observe(first, values, refreshes::incrementAndGet);
        tracker.observe(second, values, refreshes::incrementAndGet);
        tracker.clear(first);

        assertTrue(tracker.observe(first, values, refreshes::incrementAndGet));
        assertFalse(tracker.observe(second, values, refreshes::incrementAndGet));
        assertEquals(3, refreshes.get());
        assertEquals(2, tracker.snapshotCount());
    }

    private static QualityAttributeProjection.Values values(double maximum,
                                                            double regeneration,
                                                            double efficiency) {
        EnumMap<QualityAttributeProjection.Attribute, Double> values =
                new EnumMap<>(QualityAttributeProjection.Attribute.class);
        values.put(QualityAttributeProjection.Attribute.MAX_MANA_PERCENT, maximum);
        values.put(QualityAttributeProjection.Attribute.MANA_REGEN_PERCENT, regeneration);
        values.put(QualityAttributeProjection.Attribute.SPELL_EFFICIENCY, efficiency);
        return new QualityAttributeProjection.Values(values);
    }
}
