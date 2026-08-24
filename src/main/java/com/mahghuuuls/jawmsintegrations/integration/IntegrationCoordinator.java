package com.mahghuuuls.jawmsintegrations.integration;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Sole owner of optional-integration state and compatibility precedence. */
public final class IntegrationCoordinator {

    private final Map<IntegrationId, IntegrationStatusView> statuses;

    private IntegrationCoordinator(Map<IntegrationId, IntegrationStatusView> statuses) {
        this.statuses = Collections.unmodifiableMap(new EnumMap<>(statuses));
    }

    public static IntegrationCoordinator initialize(IntegrationConfigSnapshot config) {
        return initialize(
                config,
                modId -> {
                    ModContainer container = Loader.instance().getIndexedModList().get(modId);
                    return container == null ? null : container.getVersion();
                },
                OptionalIntegrationEvidenceRegistry::get
        );
    }

    public static IntegrationCoordinator initialize(IntegrationConfigSnapshot config,
                                                    ModVersionSource versions,
                                                    GateEvidenceSource gateEvidence) {
        if (config == null || versions == null || gateEvidence == null) {
            throw new IllegalArgumentException("Coordinator inputs must not be null");
        }
        Map<IntegrationId, IntegrationStatusView> resolved = new EnumMap<>(IntegrationId.class);
        for (IntegrationId integration : IntegrationId.values()) {
            String detectedVersion = versions.findVersion(integration.getModId());
            OptionalIntegrationEvidenceRegistry.Evidence gate = gateEvidence.get(integration);
            resolved.put(integration, resolve(integration, detectedVersion, gate, isEnabled(config, integration)));
        }
        return new IntegrationCoordinator(resolved);
    }

    private static IntegrationStatusView resolve(IntegrationId integration,
                                                 String detectedVersion,
                                                 OptionalIntegrationEvidenceRegistry.Evidence gate,
                                                 boolean enabled) {
        if (detectedVersion == null) {
            return new IntegrationStatusView(integration, IntegrationState.ABSENT, null,
                    "Optional dependency is not installed");
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.ERROR) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    gate.getDetail());
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.UNSUPPORTED) {
            return new IntegrationStatusView(integration, IntegrationState.UNSUPPORTED, detectedVersion,
                    "Transformation gate rejected version " + gate.getDetectedVersion()
                            + "; minimum metadata version is " + integration.getMinimumMetadataVersion());
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.ABSENT) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Forge detected the mod, but the transformation gate did not see it");
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.UNKNOWN) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    gate.getDetail());
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED
                && !integration.meetsMinimumMetadataVersion(gate.getDetectedVersion())) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Transformation gate reported metadata version " + gate.getDetectedVersion()
                            + "; minimum metadata version is "
                            + integration.getMinimumMetadataVersion());
        }
        if (gate.getDecision() == OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED
                && !integration.acceptsLoaderVersion(detectedVersion)) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Transformation-time version " + gate.getDetectedVersion()
                            + " differs from Forge version " + detectedVersion);
        }
        String reportedVersion = integration.meetsMinimumMetadataVersion(detectedVersion)
                ? detectedVersion
                : gate.getDetectedVersion();
        if (!enabled) {
            return new IntegrationStatusView(integration, IntegrationState.DISABLED, reportedVersion,
                    "Disabled by configuration");
        }
        return new IntegrationStatusView(integration, IntegrationState.READY, reportedVersion,
                "A release meeting the minimum version is installed and the integration is ready to activate");
    }

    private static boolean isEnabled(IntegrationConfigSnapshot config, IntegrationId integration) {
        switch (integration) {
            case QUALITY_TOOLS:
                return config.getQualityTools().isIntegrationEnabled();
            case ANCIENT_SPELLCRAFT:
                return config.getAncientSpellcraft().isIntegrationEnabled();
            case CRAFTTWEAKER:
                return config.getCraftTweaker().isEnabled();
            default:
                throw new IllegalStateException("Unhandled integration " + integration);
        }
    }

    public IntegrationStatusView getStatus(IntegrationId integration) {
        IntegrationStatusView status = statuses.get(integration);
        if (status == null) {
            throw new IllegalArgumentException("Unknown integration " + integration);
        }
        return status;
    }

    public List<IntegrationStatusView> getStatuses() {
        return Collections.unmodifiableList(new ArrayList<>(statuses.values()));
    }

    public IntegrationCoordinator withFailure(IntegrationId integration, String detail) {
        if (integration == null || detail == null || detail.trim().isEmpty()) {
            throw new IllegalArgumentException("Failure integration and detail must be provided");
        }
        Map<IntegrationId, IntegrationStatusView> failed = new EnumMap<>(statuses);
        IntegrationStatusView current = getStatus(integration);
        failed.put(integration, new IntegrationStatusView(integration, IntegrationState.FAILED,
                current.getDetectedVersion(), detail));
        return new IntegrationCoordinator(failed);
    }

    public IntegrationCoordinator withActive(IntegrationId integration, String detail) {
        if (integration == null || detail == null || detail.trim().isEmpty()) {
            throw new IllegalArgumentException("Active integration and detail must be provided");
        }
        IntegrationStatusView current = getStatus(integration);
        if (current.getState() != IntegrationState.READY) {
            throw new IllegalStateException("Only a READY integration can become ACTIVE: " + integration);
        }
        Map<IntegrationId, IntegrationStatusView> active = new EnumMap<>(statuses);
        active.put(integration, new IntegrationStatusView(integration, IntegrationState.ACTIVE,
                current.getDetectedVersion(), detail));
        return new IntegrationCoordinator(active);
    }

    @FunctionalInterface
    public interface ModVersionSource {
        /** Returns the raw Forge metadata version, or null when the mod is absent. */
        String findVersion(String modId);
    }

    @FunctionalInterface
    public interface GateEvidenceSource {
        OptionalIntegrationEvidenceRegistry.Evidence get(IntegrationId integration);
    }
}
