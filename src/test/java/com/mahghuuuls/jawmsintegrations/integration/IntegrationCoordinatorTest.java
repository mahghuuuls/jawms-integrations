package com.mahghuuuls.jawmsintegrations.integration;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationCoordinatorTest {

    @Test
    void allFourAbsentOptionalModsRemainIndependentInactiveStates() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), new HashMap<>(), allAbsent());
        assertEquals(4, coordinator.getStatuses().size());
        for (IntegrationId integration : IntegrationId.values()) {
            assertEquals(IntegrationState.ABSENT, coordinator.getStatus(integration).getState());
        }
    }

    @Test
    void minimumVersionsRespectEveryIndependentMasterControlAndRemainReadyUntilActivated() {
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(
                new IntegrationConfigSnapshot.QualityToolsConfig(false, true),
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(true),
                new IntegrationConfigSnapshot.IntegrationToggleConfig(false),
                new IntegrationConfigSnapshot.IntegrationToggleConfig(true),
                new IntegrationConfigSnapshot.DiagnosticsConfig(false));
        IntegrationCoordinator coordinator = initialize(config, supportedVersions(), allSupported());
        assertEquals(IntegrationState.DISABLED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.READY,
                coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
        assertEquals(IntegrationState.DISABLED,
                coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getState());
        assertEquals(IntegrationState.READY,
                coordinator.getStatus(IntegrationId.ARS_MAGICA).getState());
    }

    @Test
    void unsupportedVersionDisablesOnlyAffectedIntegrationAndNamesVersions() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.CRAFTTWEAKER.getModId(), "1.12-4.1.20.714");
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence = allSupported();
        evidence.put(IntegrationId.CRAFTTWEAKER,
                OptionalIntegrationEvidenceRegistry.Evidence.unsupported("1.12-4.1.20.714"));
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), versions, evidence);
        IntegrationStatusView craftTweaker = coordinator.getStatus(IntegrationId.CRAFTTWEAKER);
        assertEquals(IntegrationState.UNSUPPORTED, craftTweaker.getState());
        assertTrue(craftTweaker.getDetail().contains("1.12-4.1.20.714"));
        assertTrue(craftTweaker.getDetail().contains("1.12-4.1.20.715"));
        assertEquals(IntegrationState.READY,
                coordinator.getStatus(IntegrationId.ARS_MAGICA).getState());
    }

    @Test
    void bootstrapFailureAndMissingEvidenceFailClosedOnlyForAffectedIntegration() {
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence = allSupported();
        evidence.put(IntegrationId.ARS_MAGICA,
                OptionalIntegrationEvidenceRegistry.Evidence.error("metadata unreadable"));
        evidence.put(IntegrationId.CRAFTTWEAKER,
                OptionalIntegrationEvidenceRegistry.Evidence.unknown());
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), supportedVersions(), evidence);
        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.ARS_MAGICA).getState());
        assertEquals("metadata unreadable",
                coordinator.getStatus(IntegrationId.ARS_MAGICA).getDetail());
        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getState());
        assertEquals("Bootstrap gate did not publish evidence",
                coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getDetail());
        assertEquals(IntegrationState.READY,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
    }

    @Test
    void supportedDecisionWithWrongMetadataFailsClosed() {
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence = allSupported();
        evidence.put(IntegrationId.ARS_MAGICA,
                OptionalIntegrationEvidenceRegistry.Evidence.supported("GRADLE:VERSIONGRADLE:BUILD"));
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), supportedVersions(), evidence);
        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.ARS_MAGICA).getState());
        assertTrue(coordinator.getStatus(IntegrationId.ARS_MAGICA).getDetail().contains("1.6.2"));
    }

    @Test
    void ancientForgeAliasRequiresExactArchiveEvidence() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.ANCIENT_SPELLCRAFT.getModId(), "1.12.2-INDEV");
        IntegrationCoordinator confirmed = initialize(
                IntegrationConfigSnapshot.defaults(), versions, allSupported());
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> missing = allSupported();
        missing.put(IntegrationId.ANCIENT_SPELLCRAFT,
                OptionalIntegrationEvidenceRegistry.Evidence.unknown());
        IntegrationCoordinator unconfirmed = initialize(
                IntegrationConfigSnapshot.defaults(), versions, missing);
        assertEquals(IntegrationState.READY,
                confirmed.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
        assertEquals(IntegrationState.FAILED,
                unconfirmed.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState());
    }

    @Test
    void releasedArsForgePlaceholderUsesExactSupportedArchiveEvidence() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.ARS_MAGICA.getModId(), "GRADLE:VERSIONGRADLE:BUILD");

        IntegrationCoordinator confirmed = initialize(
                IntegrationConfigSnapshot.defaults(), versions, allSupported());
        IntegrationStatusView ars = confirmed.getStatus(IntegrationId.ARS_MAGICA);
        assertEquals(IntegrationState.READY, ars.getState());
        assertEquals("1.6.2", ars.getDetectedVersion());

        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> rejected = allSupported();
        rejected.put(IntegrationId.ARS_MAGICA,
                OptionalIntegrationEvidenceRegistry.Evidence.unsupported("1.6.1"));
        IntegrationCoordinator unsupported = initialize(
                IntegrationConfigSnapshot.defaults(), versions, rejected);
        assertEquals(IntegrationState.UNSUPPORTED,
                unsupported.getStatus(IntegrationId.ARS_MAGICA).getState());
    }

    @Test
    void activationFailureReclassifiesOnlyTheAffectedIntegration() {
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), supportedVersions(), allSupported())
                .withFailure(IntegrationId.QUALITY_TOOLS, "provider registration failed");
        assertEquals(IntegrationState.FAILED,
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals("provider registration failed",
                coordinator.getStatus(IntegrationId.QUALITY_TOOLS).getDetail());
        assertEquals(IntegrationState.READY,
                coordinator.getStatus(IntegrationId.CRAFTTWEAKER).getState());
    }

    @Test
    void newerVersionsHaveNoUpperBoundAndOlderVersionsRemainUnsupported() {
        for (IntegrationId integration : IntegrationId.values()) {
            assertTrue(integration.meetsMinimumMetadataVersion(newerVersion(integration)));
        }
        assertFalse(IntegrationId.QUALITY_TOOLS.meetsMinimumMetadataVersion("1.0.6_for_1.12.2"));
        assertFalse(IntegrationId.ANCIENT_SPELLCRAFT.meetsMinimumMetadataVersion("1.12.2-1.8.2"));
        assertFalse(IntegrationId.CRAFTTWEAKER.meetsMinimumMetadataVersion("1.12-4.1.20.714"));
        assertFalse(IntegrationId.ARS_MAGICA.meetsMinimumMetadataVersion("1.6.1"));

        Map<String, String> versions = supportedVersions();
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence = allSupported();
        for (IntegrationId integration : IntegrationId.values()) {
            String newer = newerVersion(integration);
            versions.put(integration.getModId(), newer);
            evidence.put(integration, OptionalIntegrationEvidenceRegistry.Evidence.supported(newer));
        }
        IntegrationCoordinator coordinator = initialize(
                IntegrationConfigSnapshot.defaults(), versions, evidence);
        for (IntegrationId integration : IntegrationId.values()) {
            assertEquals(IntegrationState.READY, coordinator.getStatus(integration).getState());
        }
    }

    @Test
    void craftTweakerForgeAliasIsAcceptedOnlyAfterFullMetadataGatePasses() {
        Map<String, String> versions = supportedVersions();
        versions.put(IntegrationId.CRAFTTWEAKER.getModId(), "4.1.20");

        IntegrationCoordinator accepted = initialize(
                IntegrationConfigSnapshot.defaults(), versions, allSupported());
        assertEquals(IntegrationState.READY,
                accepted.getStatus(IntegrationId.CRAFTTWEAKER).getState());

        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> rejected = allSupported();
        rejected.put(IntegrationId.CRAFTTWEAKER,
                OptionalIntegrationEvidenceRegistry.Evidence.unsupported(
                        "1.12-4.1.20.714"));
        IntegrationCoordinator unsupported = initialize(
                IntegrationConfigSnapshot.defaults(), versions, rejected);
        assertEquals(IntegrationState.UNSUPPORTED,
                unsupported.getStatus(IntegrationId.CRAFTTWEAKER).getState());
    }

    @Test
    void onlyReadyIntegrationsCanBePromotedToActive() {
        IntegrationCoordinator ready = initialize(
                IntegrationConfigSnapshot.defaults(), supportedVersions(), allSupported());
        IntegrationCoordinator active = ready.withActive(
                IntegrationId.QUALITY_TOOLS, "provider registration completed");
        assertEquals(IntegrationState.ACTIVE,
                active.getStatus(IntegrationId.QUALITY_TOOLS).getState());
        assertEquals(IntegrationState.READY,
                active.getStatus(IntegrationId.CRAFTTWEAKER).getState());

        IntegrationConfigSnapshot disabledQualityTools = new IntegrationConfigSnapshot(
                new IntegrationConfigSnapshot.QualityToolsConfig(false, true),
                IntegrationConfigSnapshot.defaults().getAncientSpellcraft(),
                IntegrationConfigSnapshot.defaults().getCraftTweaker(),
                IntegrationConfigSnapshot.defaults().getArsMagica(),
                IntegrationConfigSnapshot.defaults().getDiagnostics());
        IntegrationCoordinator disabled = initialize(
                disabledQualityTools, supportedVersions(), allSupported());
        assertThrows(IllegalStateException.class, () -> disabled.withActive(
                IntegrationId.QUALITY_TOOLS, "must not bypass disabled state"));

        IntegrationCoordinator failed = ready.withFailure(
                IntegrationId.QUALITY_TOOLS, "activation failed");
        assertThrows(IllegalStateException.class, () -> failed.withActive(
                IntegrationId.QUALITY_TOOLS, "must not bypass failure state"));
    }

    private static IntegrationCoordinator initialize(
            IntegrationConfigSnapshot config,
            Map<String, String> versions,
            Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence) {
        return IntegrationCoordinator.initialize(config, versions::get, evidence::get);
    }

    private static Map<String, String> supportedVersions() {
        Map<String, String> versions = new HashMap<>();
        for (IntegrationId integration : IntegrationId.values()) {
            versions.put(integration.getModId(), integration.getMinimumMetadataVersion());
        }
        return versions;
    }

    private static Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> allSupported() {
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence =
                new EnumMap<>(IntegrationId.class);
        for (IntegrationId integration : IntegrationId.values()) {
            evidence.put(integration, OptionalIntegrationEvidenceRegistry.Evidence.supported(
                    integration.getMinimumMetadataVersion()));
        }
        return evidence;
    }

    private static Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> allAbsent() {
        Map<IntegrationId, OptionalIntegrationEvidenceRegistry.Evidence> evidence =
                new EnumMap<>(IntegrationId.class);
        for (IntegrationId integration : IntegrationId.values()) {
            evidence.put(integration, OptionalIntegrationEvidenceRegistry.Evidence.absent());
        }
        return evidence;
    }

    private static String newerVersion(IntegrationId integration) {
        switch (integration) {
            case QUALITY_TOOLS: return "2.0.0-beta_for_1.12.2";
            case ANCIENT_SPELLCRAFT: return "1.12.2-2.0.0-beta";
            case CRAFTTWEAKER: return "1.12-4.2.0.0";
            case ARS_MAGICA: return "2.0.0";
            default: throw new IllegalStateException("Unhandled integration " + integration);
        }
    }
}
