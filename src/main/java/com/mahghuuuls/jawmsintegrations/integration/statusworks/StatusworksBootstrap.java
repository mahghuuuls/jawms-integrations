package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;

import java.util.function.Supplier;

/** Keeps native definition registration independent of mana gameplay eligibility. */
public final class StatusworksBootstrap {
    private static final IntegrationId INTEGRATION = IntegrationId.STATUSWORKS;
    private static final String MINIMUM_JAWMS_API = "1.10";

    private StatusworksBootstrap() {
    }

    public static Result initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms,
                                    boolean diagnostics) {
        return initialize(coordinator, jawms, () -> StatusworksAuthoring.register(),
                () -> StatusworksGameplayContract.verifyBound(), () -> StatusworksGameplay.activate(diagnostics));
    }

    static Result initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms,
                             Runnable registerAuthoring, GameplayProbe verifyGameplay,
                             Supplier<StatusworksSourceAccess> activate) {
        IntegrationStatusView status = coordinator.getStatus(INTEGRATION);
        if (status.getState() == IntegrationState.ABSENT) return new Result(coordinator, null);
        if (status.getState() == IntegrationState.READY) {
            if (!JawmsCompatibility.meetsMinimum(jawms.getApiVersion(), MINIMUM_JAWMS_API)) {
                coordinator = unsupportedGameplay(coordinator, "Requires JAWMS API " + MINIMUM_JAWMS_API
                        + " or newer. Detected " + jawms.getApiVersion() + ".");
            } else {
                try {
                    verifyGameplay.verify();
                } catch (ReflectiveOperationException | LinkageError | IllegalStateException missing) {
                    coordinator = unsupportedGameplay(coordinator, "Gameplay contract unavailable: " + missing + ".");
                }
            }
            if (coordinator.getStatus(INTEGRATION).getState() == IntegrationState.READY) {
                IntegrationCoordinator active = coordinator.withActive(INTEGRATION,
                        "Native types and server mana readiness provider registered");
                try {
                    return new Result(active, activate.get());
                } catch (RuntimeException | LinkageError failure) {
                    String detail = "Native mana activation failed: " + failure;
                    JawmsIntegrationsMod.LOGGER.error("Statusworks integration FAILED: {}", detail, failure);
                    return new Result(coordinator.withFailure(INTEGRATION, detail), null);
                }
            }
        }
        try {
            registerAuthoring.run();
        } catch (LinkageError error) {
            String detail = "Native contribution registration contract unavailable: " + error;
            JawmsIntegrationsMod.LOGGER.warn("Statusworks integration UNSUPPORTED: {}", detail);
            return new Result(coordinator.withUnsupported(INTEGRATION, detail), null);
        } catch (RuntimeException exception) {
            String detail = "Native contribution registration failed: " + exception.getMessage();
            JawmsIntegrationsMod.LOGGER.error("Statusworks integration FAILED: " + detail, exception);
            return new Result(coordinator.withFailure(INTEGRATION, detail), null);
        }
        String detail = coordinator.getStatus(INTEGRATION).getDetail();
        return new Result(coordinator.withDetail(INTEGRATION, detail
                + (detail.endsWith(".") ? " " : ". ") + "Native contribution types are registered."), null);
    }

    private static IntegrationCoordinator unsupportedGameplay(IntegrationCoordinator coordinator, String reason) {
        JawmsIntegrationsMod.LOGGER.warn("Statusworks integration UNSUPPORTED: {}", reason);
        return coordinator.withUnsupported(INTEGRATION, reason);
    }

    @FunctionalInterface
    interface GameplayProbe {
        void verify() throws ReflectiveOperationException;
    }

    public static final class Result {
        public final IntegrationCoordinator coordinator;
        public final StatusworksSourceAccess source;

        private Result(IntegrationCoordinator coordinator, StatusworksSourceAccess source) {
            this.coordinator = coordinator;
            this.source = source;
        }
    }
}
