package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarlyModMetadataScannerTest {

    @Test
    void readsExactQualityToolsReleaseMetadataWithoutLoadingItsClasses() throws Exception {
        assertRelease(
                "jawmsintegrations.contract.qualityToolsJar",
                IntegrationId.QUALITY_TOOLS
        );
    }

    @Test
    void readsExactAncientSpellcraftReleaseMetadataWithoutLoadingItsClasses() throws Exception {
        assertRelease(
                "jawmsintegrations.contract.ancientSpellcraftJar",
                IntegrationId.ANCIENT_SPELLCRAFT
        );
    }

    private static void assertRelease(String property, IntegrationId integration) throws Exception {
        File jar = new File(System.getProperty(property));
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, null)) {
            OptionalMixinGateRegistry.Evidence evidence = EarlyModMetadataScanner.scan(loader, integration);
            assertEquals(OptionalMixinGateRegistry.Decision.SUPPORTED, evidence.getDecision());
            assertEquals(integration.getSupportedMetadataVersion(), evidence.getDetectedVersion());
        }
    }
}
