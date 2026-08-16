package com.mahghuuuls.jawmsintegrations.integration;

/** Immutable status exposed to diagnostics and later presentation code. */
public final class IntegrationStatusView {

    private final IntegrationId integration;
    private final IntegrationState state;
    private final String detectedVersion;
    private final String detail;

    IntegrationStatusView(IntegrationId integration,
                          IntegrationState state,
                          String detectedVersion,
                          String detail) {
        this.integration = integration;
        this.state = state;
        this.detectedVersion = detectedVersion;
        this.detail = detail;
    }

    public IntegrationId getIntegration() {
        return integration;
    }

    public IntegrationState getState() {
        return state;
    }

    public boolean isPresent() {
        return detectedVersion != null;
    }

    public String getDetectedVersion() {
        return detectedVersion;
    }

    public String getSupportedVersion() {
        return integration.getSupportedVersion();
    }

    public String getDetail() {
        return detail;
    }
}
