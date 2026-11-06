package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class SetworksBootstrapTest {
    @Test
    void absentAndOlderSetworksSkipAuthoring() {
        for (String installed : new String[]{null, "1.0.0"}) {
            IntegrationCoordinator coordinator = coordinator(installed, true);
            IntegrationCoordinator result = SetworksBootstrap.initialize(coordinator,
                    JawmsCompatibility.verify("1.3.0", Api18.class),
                    () -> fail("Unavailable Setworks must not attempt authoring"));
            assertSame(coordinator, result);
            assertEquals(installed == null ? IntegrationState.ABSENT : IntegrationState.UNSUPPORTED,
                    result.getStatus(IntegrationId.SETWORKS).getState());
        }
    }

    @Test
    void authoringSurvivesDisabledAndLegacyJawmsAndStaysInactiveOnCompatiblePair() {
        AtomicInteger calls = new AtomicInteger();
        JawmsCompatibility.Status legacy = JawmsCompatibility.verify("1.3.0", Api18.class);
        JawmsCompatibility.Status supported = JawmsCompatibility.verify("1.4.0", Api19.class);
        IntegrationStatusView disabled = SetworksBootstrap.initialize(coordinator("1.1.0", false), legacy,
                calls::incrementAndGet).getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.DISABLED, disabled.getState());
        assertTrue(disabled.getDetail().contains("still validate"));
        IntegrationStatusView unsupported = SetworksBootstrap.initialize(coordinator("1.1.0", true), legacy,
                calls::incrementAndGet).getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.UNSUPPORTED, unsupported.getState());
        assertTrue(unsupported.getDetail().contains("1.3.0 / API 1.8"));
        assertTrue(unsupported.getDetail().contains("suspension and recovery"));
        IntegrationStatusView ready = SetworksBootstrap.initialize(coordinator("1.1.0", true), supported,
                calls::incrementAndGet).getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.READY, ready.getState());
        assertTrue(ready.getDetail().contains("gameplay is inactive"));
        assertEquals(3, calls.get());
    }

    @Test
    void missingAuthoringContractAndRejectedRegistrationFailClosed() {
        IntegrationCoordinator coordinator = coordinator("1.1.0", true);
        JawmsCompatibility.Status jawms = JawmsCompatibility.verify("1.4.0", Api19.class);
        assertEquals(IntegrationState.UNSUPPORTED, SetworksBootstrap.initialize(coordinator, jawms,
                () -> { throw new NoSuchMethodError("registerEffectKind"); })
                .getStatus(IntegrationId.SETWORKS).getState());
        // The actual released entry refuses registration before Setworks installs its service.
        IntegrationStatusView unbound = SetworksBootstrap.initialize(coordinator, jawms)
                .getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.FAILED, unbound.getState());
        assertTrue(unbound.getDetail().contains("not constructed yet"));
    }

    private static IntegrationCoordinator coordinator(String installed, boolean enabled) {
        IntegrationConfigSnapshot defaults = IntegrationConfigSnapshot.defaults();
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(defaults.getQualityTools(),
                defaults.getAncientSpellcraft(), defaults.getCraftTweaker(), defaults.getJafs(),
                defaults.getSpellStamina(), new IntegrationConfigSnapshot.IntegrationToggleConfig(enabled),
                defaults.getDiagnostics());
        return IntegrationCoordinator.initialize(config, id -> "setworks".equals(id) ? installed : null,
                ignored -> OptionalIntegrationEvidenceRegistry.Evidence.absent());
    }

    public static final class Api18 {
        public static final String CURRENT = "1.8";
    }

    public static final class Api19 {
        public static final String CURRENT = "1.9";
    }
}
