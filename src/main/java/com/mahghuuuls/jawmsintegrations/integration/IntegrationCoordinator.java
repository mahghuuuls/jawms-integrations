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
                OptionalMixinGateRegistry::get
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
            OptionalMixinGateRegistry.Evidence gate = gateEvidence.get(integration);
            resolved.put(integration, resolve(integration, detectedVersion, gate, isEnabled(config, integration)));
        }
        return new IntegrationCoordinator(resolved);
    }

    private static IntegrationStatusView resolve(IntegrationId integration,
                                                 String detectedVersion,
                                                 OptionalMixinGateRegistry.Evidence gate,
                                                 boolean enabled) {
        if (detectedVersion == null) {
            return new IntegrationStatusView(integration, IntegrationState.ABSENT, null,
                    "Optional dependency is not installed");
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.ERROR) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    gate.getDetail());
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.UNSUPPORTED) {
            return new IntegrationStatusView(integration, IntegrationState.UNSUPPORTED, detectedVersion,
                    "Transformation gate rejected version " + gate.getDetectedVersion()
                            + "; supported metadata version is " + integration.getSupportedMetadataVersion());
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.ABSENT) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Forge detected the mod, but the transformation gate did not see it");
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.UNKNOWN) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    gate.getDetail());
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.SUPPORTED
                && !integration.getSupportedMetadataVersion().equals(gate.getDetectedVersion())) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Transformation gate reported metadata version " + gate.getDetectedVersion()
                            + "; required metadata version is "
                            + integration.getSupportedMetadataVersion());
        }
        if (gate.getDecision() == OptionalMixinGateRegistry.Decision.SUPPORTED
                && !integration.acceptsLoaderVersion(detectedVersion)) {
            return new IntegrationStatusView(integration, IntegrationState.FAILED, detectedVersion,
                    "Transformation-time version " + gate.getDetectedVersion()
                            + " differs from Forge version " + detectedVersion);
        }
        if (!enabled) {
            return new IntegrationStatusView(integration, IntegrationState.DISABLED, detectedVersion,
                    "Disabled by configuration");
        }
        return new IntegrationStatusView(integration, IntegrationState.ACTIVE, detectedVersion,
                "Exact supported version is installed and the integration is enabled");
    }

    private static IntegrationStatusView unsupported(IntegrationId integration, String detectedVersion) {
        return new IntegrationStatusView(integration, IntegrationState.UNSUPPORTED, detectedVersion,
                "Detected " + detectedVersion + "; supported release is " + integration.getSupportedVersion()
                        + " (metadata " + integration.getSupportedMetadataVersion() + ")");
    }

    private static boolean isEnabled(IntegrationConfigSnapshot config, IntegrationId integration) {
        switch (integration) {
            case QUALITY_TOOLS:
                return config.getQualityTools().isIntegrationEnabled();
            case ANCIENT_SPELLCRAFT:
                return config.getAncientSpellcraft().isIntegrationEnabled();
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

    @FunctionalInterface
    public interface ModVersionSource {
        /** Returns the raw Forge metadata version, or null when the mod is absent. */
        String findVersion(String modId);
    }

    @FunctionalInterface
    public interface GateEvidenceSource {
        OptionalMixinGateRegistry.Evidence get(IntegrationId integration);
    }
}
