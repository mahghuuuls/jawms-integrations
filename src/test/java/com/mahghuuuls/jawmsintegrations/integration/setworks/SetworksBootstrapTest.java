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
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;

import static org.junit.jupiter.api.Assertions.*;

final class SetworksBootstrapTest {
    @Test
    void absentAndOlderSetworksSkipAuthoring() {
        for (String installed : new String[]{null, "1.0.0"}) {
            IntegrationCoordinator coordinator = coordinator(installed, true);
            IntegrationCoordinator result = SetworksBootstrap.initialize(coordinator,
                    JawmsCompatibility.verify("1.4.1", Api19.class),
                    () -> fail("Unavailable Setworks must not attempt authoring")).getCoordinator();
            assertSame(coordinator, result);
            assertEquals(installed == null ? IntegrationState.ABSENT : IntegrationState.UNSUPPORTED,
                    result.getStatus(IntegrationId.SETWORKS).getState());
        }
    }

    @Test
    void authoringSurvivesDisabledAndMissingGameplayApiAndActivatesOnCompatiblePair() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger activations = new AtomicInteger();
        SetworksSourceAccess source = new SetworksSourceAccess() {
            @Override public PlayerStatus inspect(EntityPlayer player) { return null; }
            @Override public void serverStopped() { }
        };
        Supplier<SetworksBootstrap.Authoring> registration = () -> {
            calls.incrementAndGet();
            return () -> {
                activations.incrementAndGet();
                return source;
            };
        };
        JawmsCompatibility.Status missingGameplayApi = JawmsCompatibility.verify("1.4.1", Api18.class);
        JawmsCompatibility.Status supported = JawmsCompatibility.verify("1.4.1", Api19.class);
        IntegrationStatusView disabled = SetworksBootstrap.initialize(coordinator("1.1.0", false), supported,
                registration).getCoordinator().getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.DISABLED, disabled.getState());
        assertTrue(disabled.getDetail().contains("still validate"));
        IntegrationStatusView unsupported = SetworksBootstrap.initialize(coordinator("1.1.0", true), missingGameplayApi,
                registration).getCoordinator().getStatus(IntegrationId.SETWORKS);
        assertEquals(IntegrationState.UNSUPPORTED, unsupported.getState());
        assertTrue(unsupported.getDetail().contains("1.4.1 / API 1.8"));
        assertTrue(unsupported.getDetail().contains("suspension and recovery"));
        assertEquals(0, activations.get());
        SetworksBootstrap.Result active = SetworksBootstrap.initialize(
                coordinator("1.1.0", true), supported, registration);
        assertEquals(IntegrationState.ACTIVE, active.getCoordinator().getStatus(IntegrationId.SETWORKS).getState());
        assertSame(source, active.getSource());
        assertEquals(1, activations.get());
        assertEquals(3, calls.get());
    }

    @Test
    void missingAuthoringContractAndRejectedRegistrationFailClosed() {
        IntegrationCoordinator coordinator = coordinator("1.1.0", true);
        JawmsCompatibility.Status jawms = JawmsCompatibility.verify("1.4.1", Api19.class);
        assertEquals(IntegrationState.UNSUPPORTED, SetworksBootstrap.initialize(coordinator, jawms,
                () -> { throw new NoSuchMethodError("registerEffectKind"); })
                .getCoordinator().getStatus(IntegrationId.SETWORKS).getState());
        SetworksBootstrap.Result incompatible = SetworksBootstrap.initialize(coordinator, jawms,
                () -> () -> { throw new NoSuchMethodError("suspend"); });
        assertEquals(IntegrationState.UNSUPPORTED,
                incompatible.getCoordinator().getStatus(IntegrationId.SETWORKS).getState());
        assertNull(incompatible.getSource());
        // The actual released entry refuses registration before Setworks installs its service.
        IntegrationStatusView unbound = SetworksBootstrap.initialize(coordinator, jawms)
                .getCoordinator().getStatus(IntegrationId.SETWORKS);
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
