package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;

/** Keeps native definition registration independent of mana gameplay eligibility. */
public final class StatusworksBootstrap {
    private static final IntegrationId INTEGRATION = IntegrationId.STATUSWORKS;
    private static final String MINIMUM_JAWMS_API = "1.10";

    private StatusworksBootstrap() {
    }

    public static IntegrationCoordinator initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms) {
        return initialize(coordinator, jawms, () -> StatusworksAuthoring.register(),
                () -> StatusworksGameplayContract.verify());
    }

    static IntegrationCoordinator initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms,
                                             Runnable registerAuthoring, GameplayProbe verifyGameplay) {
        IntegrationStatusView status = coordinator.getStatus(INTEGRATION);
        if (status.getState() == IntegrationState.ABSENT) return coordinator;
        try {
            registerAuthoring.run();
        } catch (LinkageError error) {
            String detail = "Native contribution registration contract unavailable: " + error;
            JawmsIntegrationsMod.LOGGER.warn("Statusworks integration UNSUPPORTED: {}", detail);
            return coordinator.withUnsupported(INTEGRATION, detail);
        } catch (RuntimeException exception) {
            String detail = "Native contribution registration failed: " + exception.getMessage();
            JawmsIntegrationsMod.LOGGER.error("Statusworks integration FAILED: " + detail, exception);
            return coordinator.withFailure(INTEGRATION, detail);
        }
        if (status.getState() != IntegrationState.READY) {
            return coordinator.withDetail(INTEGRATION, status.getDetail() + ". Native contribution types are registered.");
        }
        if (!JawmsCompatibility.meetsMinimum(jawms.getApiVersion(), MINIMUM_JAWMS_API)) {
            return unsupportedGameplay(coordinator, "Requires JAWMS API " + MINIMUM_JAWMS_API
                    + " or newer. Detected " + jawms.getApiVersion() + ".");
        }
        try {
            verifyGameplay.verify();
        } catch (ReflectiveOperationException | LinkageError missing) {
            return unsupportedGameplay(coordinator, "Gameplay contract unavailable: " + missing + ".");
        }
        return coordinator.withDetail(INTEGRATION, "Native contribution types are registered. Gameplay activation pending.");
    }

    private static IntegrationCoordinator unsupportedGameplay(IntegrationCoordinator coordinator, String reason) {
        String detail = reason + " Native contribution types are registered.";
        JawmsIntegrationsMod.LOGGER.warn("Statusworks integration UNSUPPORTED: {}", detail);
        return coordinator.withUnsupported(INTEGRATION, detail);
    }

    @FunctionalInterface
    interface GameplayProbe {
        void verify() throws ReflectiveOperationException;
    }
}
