package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalIntegrationContractValidatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void pinnedQualityToolsArtifactSatisfiesRuntimeMixinContract() throws Exception {
        assertCurrentArtifactPasses("jawmsintegrations.contract.qualityToolsJar",
                IntegrationId.QUALITY_TOOLS);
    }

    @Test
    void pinnedAncientArtifactSatisfiesRuntimeMixinContract() throws Exception {
        assertCurrentArtifactPasses("jawmsintegrations.contract.ancientSpellcraftJar",
                IntegrationId.ANCIENT_SPELLCRAFT);
    }

    @Test
    void pinnedArsArtifactSatisfiesRuntimeMixinContract() throws Exception {
        assertCurrentArtifactPasses("jawmsintegrations.contract.arsMagicaJar",
                IntegrationId.ARS_MAGICA);
    }

    @Test
    void newerMetadataWithoutRequiredSeamsFailsOnlyThatMixinGate() throws Exception {
        String newerVersion = "9.0.0_for_1.12.2";
        Files.write(temporaryDirectory.resolve("mcmod.info"), (
                "[{\"modid\":\"qualitytools\",\"version\":\"" + newerVersion + "\"}]"
        ).getBytes(StandardCharsets.UTF_8));

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{temporaryDirectory.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(loader);
            OptionalIntegrationMixinPlugin plugin = new OptionalIntegrationMixinPlugin();
            plugin.onLoad("com.mahghuuuls.jawmsintegrations.mixin.qualitytools");

            OptionalIntegrationEvidenceRegistry.Evidence evidence =
                    OptionalIntegrationEvidenceRegistry.get(IntegrationId.QUALITY_TOOLS);
            assertEquals(OptionalIntegrationEvidenceRegistry.Decision.ERROR,
                    evidence.getDecision());
            assertEquals(newerVersion, evidence.getDetectedVersion());
            assertTrue(evidence.getDetail().contains("Adapter contract failed"));
            assertTrue(evidence.getDetail().contains("QualityType"));
            assertFalse(plugin.shouldApplyMixin("any.Target", "any.Mixin"),
                    "Contract drift must veto the optional Mixins instead of reaching transformation");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static void assertCurrentArtifactPasses(String property, IntegrationId integration)
            throws Exception {
        File jar = new File(System.getProperty(property));
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jar.toURI().toURL()}, null)) {
            OptionalIntegrationEvidenceRegistry.Evidence evidence =
                    OptionalIntegrationContractValidator.validate(
                            loader,
                            integration,
                            OptionalIntegrationEvidenceRegistry.Evidence.supported(
                                    integration.getMinimumMetadataVersion()));
            assertEquals(OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED,
                    evidence.getDecision(), evidence.getDetail());
        }
    }
}
