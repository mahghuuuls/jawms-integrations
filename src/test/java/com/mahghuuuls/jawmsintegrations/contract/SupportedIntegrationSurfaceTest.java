package com.mahghuuuls.jawmsintegrations.contract;

import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SupportedIntegrationSurfaceTest {

    @Test
    void exposesOnlyTheThreeSupportedIntegrations() {
        Path project = Paths.get("").toAbsolutePath();

        assertAll(
                () -> assertArrayEquals(new IntegrationId[] {
                                IntegrationId.QUALITY_TOOLS,
                                IntegrationId.ANCIENT_SPELLCRAFT,
                                IntegrationId.CRAFTTWEAKER
                        }, IntegrationId.values()),
                () -> assertFalse(containsRegularFile(project.resolve(
                        "src/main/java/com/mahghuuuls/jawmsintegrations/integration/arsmagica"))),
                () -> assertFalse(containsRegularFile(project.resolve(
                        "src/main/java/com/mahghuuuls/jawmsintegrations/mixin/arsmagica"))),
                () -> assertNull(SupportedIntegrationSurfaceTest.class.getClassLoader()
                        .getResource("mixins.jawmsintegrations.arsmagica.json"))
        );
    }

    @Test
    void buildAndPublicDocumentationDoNotOfferArsIntegration() throws IOException {
        Path project = Paths.get("").toAbsolutePath();
        String dependencies = read(project.resolve("gradle/scripts/dependencies.gradle"));
        String properties = read(project.resolve("gradle.properties"));
        String readme = read(project.resolve("README.md")).toLowerCase();
        String modPage = read(project.resolve("MOD-PAGE.md")).toLowerCase();

        assertAll(
                () -> assertFalse(dependencies.contains("curse.maven:ars-magica")),
                () -> assertFalse(dependencies.contains("arsMagicaRuntimeEnabled")),
                () -> assertFalse(properties.toLowerCase().contains("arsmagica")),
                () -> assertFalse(readme.contains("ars magica")),
                () -> assertFalse(modPage.contains("ars magica"))
        );
    }

    private static boolean containsRegularFile(Path directory) throws IOException {
        if (!Files.exists(directory)) return false;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
