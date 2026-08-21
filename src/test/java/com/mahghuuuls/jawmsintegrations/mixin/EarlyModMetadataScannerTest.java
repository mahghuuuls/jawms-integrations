package com.mahghuuuls.jawmsintegrations.mixin;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.EarlyModMetadataScanner;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
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
    void readsExactCraftTweakerReleaseMetadataWithoutLoadingItsClasses() throws Exception {
        assertRelease("jawmsintegrations.contract.craftTweakerJar", IntegrationId.CRAFTTWEAKER);
    }

    @Test
    void readsExactArsReleaseMetadataWithoutLoadingItsClasses() throws Exception {
        assertRelease("jawmsintegrations.contract.arsMagicaJar", IntegrationId.ARS_MAGICA);
    }

    @Test
    void readsPackagedQualityToolsMetadataFromTheForgeModsDirectory() throws Exception {
        File jar = new File(System.getProperty("jawmsintegrations.contract.qualityToolsJar"));
        Path mods = Files.createDirectories(temporaryDirectory.resolve("mods"));
        Files.copy(jar.toPath(), mods.resolve(jar.getName()), StandardCopyOption.REPLACE_EXISTING);

        try (URLClassLoader emptyLoader = new URLClassLoader(new URL[0], null)) {
            OptionalIntegrationEvidenceRegistry.Evidence evidence = EarlyModMetadataScanner.scan(
                    emptyLoader,
                    IntegrationId.QUALITY_TOOLS,
                    temporaryDirectory.toFile()
            );
            assertEquals(OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED, evidence.getDecision());
            assertEquals(IntegrationId.QUALITY_TOOLS.getMinimumMetadataVersion(),
                    evidence.getDetectedVersion());
        }
    }

    private static void assertRelease(String property, IntegrationId integration) throws Exception {
        File jar = new File(System.getProperty(property));
        try (RejectingOptionalClassLoader loader = new RejectingOptionalClassLoader(
                new URL[]{jar.toURI().toURL()})) {
            OptionalIntegrationEvidenceRegistry.Evidence evidence =
                    EarlyModMetadataScanner.scan(loader, integration);
            assertEquals(OptionalIntegrationEvidenceRegistry.Decision.SUPPORTED,
                    evidence.getDecision());
            assertEquals(integration.getMinimumMetadataVersion(), evidence.getDetectedVersion());
            assertEquals(0, loader.getOptionalClassLoadAttempts(),
                    "Metadata inspection must not resolve optional implementation classes");
        }
    }

    private static final class RejectingOptionalClassLoader extends URLClassLoader {
        private int optionalClassLoadAttempts;

        private RejectingOptionalClassLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if ("net.minecraft.launchwrapper.Launch".equals(name)) {
                throw new ClassNotFoundException(name);
            }
            optionalClassLoadAttempts++;
            throw new ClassNotFoundException("Class loading is forbidden during metadata scan: " + name);
        }

        private int getOptionalClassLoadAttempts() {
            return optionalClassLoadAttempts;
        }
    }
}
