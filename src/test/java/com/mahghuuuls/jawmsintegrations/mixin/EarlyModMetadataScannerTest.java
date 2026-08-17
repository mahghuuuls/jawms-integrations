package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarlyModMetadataScannerTest {

    @TempDir
    Path temporaryDirectory;

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

    @Test
    void readsPackagedQualityToolsMetadataFromTheForgeModsDirectory() throws Exception {
        File jar = new File(System.getProperty("jawmsintegrations.contract.qualityToolsJar"));
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Files.copy(jar.toPath(), mods.resolve(jar.getName()), StandardCopyOption.REPLACE_EXISTING);

        try (URLClassLoader emptyLoader = new URLClassLoader(new URL[0], null)) {
            OptionalMixinGateRegistry.Evidence evidence = EarlyModMetadataScanner.scan(
                    emptyLoader,
                    IntegrationId.QUALITY_TOOLS,
                    temporaryDirectory.toFile()
            );
            assertEquals(OptionalMixinGateRegistry.Decision.SUPPORTED, evidence.getDecision());
            assertEquals(IntegrationId.QUALITY_TOOLS.getSupportedMetadataVersion(),
                    evidence.getDetectedVersion());
        }
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
