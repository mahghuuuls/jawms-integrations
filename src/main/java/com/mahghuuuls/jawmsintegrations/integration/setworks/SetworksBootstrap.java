package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;

import java.util.function.Supplier;

/** Keeps native definition registration independent of mana gameplay eligibility. */
public final class SetworksBootstrap {
    private static final IntegrationId INTEGRATION = IntegrationId.SETWORKS;
    private static final String MINIMUM_JAWMS = "1.4.0";
    private static final String MINIMUM_API = "1.9";

    private SetworksBootstrap() {
    }

    public static Result initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms) {
        // Resolve Setworks types only after the coordinator accepts the installed release.
        return initialize(coordinator, jawms, () -> SetworksManaEffect.register());
    }

    static Result initialize(IntegrationCoordinator coordinator, JawmsCompatibility.Status jawms,
                             Supplier<Authoring> registerAuthoring) {
        IntegrationState state = coordinator.getStatus(INTEGRATION).getState();
        if (state != IntegrationState.READY && state != IntegrationState.DISABLED) {
            return new Result(coordinator, null);
        }
        Authoring authoring;
        try {
            authoring = registerAuthoring.get();
        } catch (LinkageError error) {
            String detail = "Compatible Setworks effect registration contract unavailable: " + error;
            JawmsIntegrationsMod.LOGGER.warn(detail);
            return new Result(coordinator.withUnsupported(INTEGRATION, detail), null);
        } catch (RuntimeException exception) {
            String detail = "Setworks effect registration failed: " + exception.getMessage();
            JawmsIntegrationsMod.LOGGER.error(detail, exception);
            return new Result(coordinator.withFailure(INTEGRATION, detail), null);
        }
        if (state == IntegrationState.DISABLED) {
            return new Result(coordinator.withDisabledDetail(INTEGRATION,
                    "Disabled by configuration. Native effect definitions still validate."), null);
        }
        if (!JawmsCompatibility.meetsMinimum(jawms.getModVersion(), MINIMUM_JAWMS)
                || !JawmsCompatibility.meetsMinimum(jawms.getApiVersion(), MINIMUM_API)) {
            String detail = "Mana effects require JAWMS " + MINIMUM_JAWMS + " or newer and API "
                    + MINIMUM_API + " readiness with suspension and recovery. Detected JAWMS "
                    + jawms.getModVersion() + " / API " + jawms.getApiVersion()
                    + ". Native effect definitions still validate.";
            JawmsIntegrationsMod.LOGGER.warn("Setworks integration UNSUPPORTED: {}", detail);
            return new Result(coordinator.withUnsupported(INTEGRATION, detail), null);
        }
        IntegrationCoordinator active = coordinator.withActive(INTEGRATION,
                "Native effect and server mana readiness provider registered");
        try {
            return new Result(active, authoring.activate());
        } catch (LinkageError missing) {
            String detail = "Setworks grouping or JAWMS API 1.9 readiness contract unavailable: " + missing;
            JawmsIntegrationsMod.LOGGER.warn(detail);
            return new Result(coordinator.withUnsupported(INTEGRATION, detail), null);
        } catch (RuntimeException failure) {
            String detail = "Setworks mana activation failed: " + failure.getMessage();
            JawmsIntegrationsMod.LOGGER.error(detail, failure);
            return new Result(coordinator.withFailure(INTEGRATION, detail), null);
        }
    }

    interface Authoring {
        SetworksSourceAccess activate();
    }

    public static final class Result {
        private final IntegrationCoordinator coordinator;
        private final SetworksSourceAccess source;

        private Result(IntegrationCoordinator coordinator, SetworksSourceAccess source) {
            this.coordinator = coordinator;
            this.source = source;
        }

        public IntegrationCoordinator getCoordinator() {
            return coordinator;
        }

        public SetworksSourceAccess getSource() {
            return source;
        }
    }
}
