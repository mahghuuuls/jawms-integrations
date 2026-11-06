package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;

/** Keeps native definition registration independent of mana gameplay eligibility. */
public final class SetworksBootstrap {
    private static final IntegrationId INTEGRATION = IntegrationId.SETWORKS;
    private static final String MINIMUM_JAWMS = "1.4.0";
    private static final String MINIMUM_API = "1.9";

    private SetworksBootstrap() {
    }

    public static IntegrationCoordinator initialize(IntegrationCoordinator coordinator,
                                                    JawmsCompatibility.Status jawms) {
        // Resolve Setworks types only after the coordinator accepts the installed release.
        return initialize(coordinator, jawms, () -> SetworksManaEffect.register());
    }

    static IntegrationCoordinator initialize(IntegrationCoordinator coordinator,
                                             JawmsCompatibility.Status jawms,
                                             Runnable registerAuthoring) {
        IntegrationState state = coordinator.getStatus(INTEGRATION).getState();
        if (state != IntegrationState.READY && state != IntegrationState.DISABLED) {
            return coordinator;
        }
        try {
            registerAuthoring.run();
        } catch (LinkageError error) {
            String detail = "Compatible Setworks effect registration contract unavailable: " + error;
            JawmsIntegrationsMod.LOGGER.warn(detail);
            return coordinator.withUnsupported(INTEGRATION, detail);
        } catch (RuntimeException exception) {
            String detail = "Setworks effect registration failed: " + exception.getMessage();
            JawmsIntegrationsMod.LOGGER.error(detail, exception);
            return coordinator.withFailure(INTEGRATION, detail);
        }
        if (state == IntegrationState.DISABLED) {
            return coordinator.withDisabledDetail(INTEGRATION,
                    "Disabled by configuration. Native effect definitions still validate.");
        }
        if (!JawmsCompatibility.meetsMinimum(jawms.getModVersion(), MINIMUM_JAWMS)
                || !JawmsCompatibility.meetsMinimum(jawms.getApiVersion(), MINIMUM_API)) {
            String detail = "Mana effects require JAWMS " + MINIMUM_JAWMS + " or newer and API "
                    + MINIMUM_API + " readiness with suspension and recovery. Detected JAWMS "
                    + jawms.getModVersion() + " / API " + jawms.getApiVersion()
                    + ". Native effect definitions still validate.";
            JawmsIntegrationsMod.LOGGER.warn("Setworks integration UNSUPPORTED: {}", detail);
            return coordinator.withUnsupported(INTEGRATION, detail);
        }
        return coordinator.withReadyDetail(INTEGRATION,
                "Native mana effect registered. Mana gameplay is inactive.");
    }
}
