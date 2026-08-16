package com.mahghuuuls.jawmsintegrations.integration;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegrationCoordinatorTest {

    @Test
    void absentOptionalModsRemainIndependentInactiveStates() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(),
                new HashMap<>(),
                gates(OptionalMixinGateRegistry.Evidence.absent(), OptionalMixinGateRegistry.Evidence.absent())
        );

        assertEquals(IntegrationState.ABSENT,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.ABSENT,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void exactVersionsActivateUnlessTheirMasterControlIsDisabled() {
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(
                new IntegrationConfigSnapshot.QualityToolsConfig(false, true),
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(true),
                new IntegrationConfigSnapshot.DiagnosticsConfig(false)
        );
        Map<String, String> versions = supportedVersions();
        IntegrationCoordinator coordinator = initialize(
                config,
                versions,
                gates(
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.QUALITY_TOOLS.getSupportedMetadataVersion()),
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );

        assertEquals(IntegrationState.DISABLED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.ACTIVE,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void unsupportedVersionDisablesOnlyAffectedIntegration() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.QUALITY_TOOLS.getModId(), "1.0.8");
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(),
                versions,
                gates(
                        OptionalMixinGateRegistry.Evidence.unsupported("1.0.8"),
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );

        assertEquals(IntegrationState.UNSUPPORTED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.ACTIVE,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void earlyGateFailureIsIsolatedAndExplained() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(),
                supportedVersions(),
                gates(
                        OptionalMixinGateRegistry.Evidence.error("metadata unreadable"),
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );

        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals("metadata unreadable",
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getDetail());
        assertEquals(IntegrationState.ACTIVE,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void presentExactReleaseFailsClosedWhenEarlyGatePublishesNoEvidence() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(),
                supportedVersions(),
                gates(
                        OptionalMixinGateRegistry.Evidence.unknown(),
                        OptionalMixinGateRegistry.Evidence.supported(
                                IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );

        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals("Mixin gate did not publish evidence",
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getDetail());
        assertEquals(IntegrationState.ACTIVE,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void supportedDecisionFailsClosedWhenGateReportsWrongMetadataVersion() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(),
                supportedVersions(),
                gates(
                        OptionalMixinGateRegistry.Evidence.supported("1.0.8"),
                        OptionalMixinGateRegistry.Evidence.supported(
                                IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );

        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.ACTIVE,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void exactAncientReleaseAcceptsItsKnownForgeIndevAliasOnlyWithGateEvidence() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.ANCIENT_SPELLCRAFT.getModId(), "1.12.2-INDEV");

        IntegrationCoordinator confirmed = initialize(
                IntegrationConfigSnapshot.defaults(),
                versions,
                gates(
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.QUALITY_TOOLS.getSupportedMetadataVersion()),
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.ANCIENT_SPELLCRAFT.getSupportedMetadataVersion())
                )
        );
        IntegrationCoordinator unconfirmed = initialize(
                IntegrationConfigSnapshot.defaults(),
                versions,
                gates(
                        OptionalMixinGateRegistry.Evidence.supported(IntegrationId.QUALITY_TOOLS.getSupportedMetadataVersion()),
                        OptionalMixinGateRegistry.Evidence.unknown()
                )
        );

        assertEquals(IntegrationState.ACTIVE,
                confirmed.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
        assertEquals(IntegrationState.FAILED,
                unconfirmed.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
        assertEquals("Mixin gate did not publish evidence",
                unconfirmed.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getDetail());
    }

    private static IntegrationCoordinator initialize(IntegrationConfigSnapshot config,
                                                     Map<String, String> versions,
                                                     Map<IntegrationId, OptionalMixinGateRegistry.Evidence> gates) {
        return IntegrationCoordinator.initialize(config, versions::get, gates::get);
    }

    private static Map<String, String> supportedVersions() {
        Map<String, String> versions = new HashMap<>();
        for (IntegrationId integration : IntegrationId.values()) {
            versions.put(integration.getModId(), integration.getSupportedMetadataVersion());
        }
        return versions;
    }

    private static Map<IntegrationId, OptionalMixinGateRegistry.Evidence> gates(
            OptionalMixinGateRegistry.Evidence qualityTools,
            OptionalMixinGateRegistry.Evidence ancientSpellcraft) {
        Map<IntegrationId, OptionalMixinGateRegistry.Evidence> gates = new EnumMap<>(IntegrationId.class);
        gates.put(IntegrationId.QUALITY_TOOLS, qualityTools);
        gates.put(IntegrationId.ANCIENT_SPELLCRAFT, ancientSpellcraft);
        return gates;
    }
}
