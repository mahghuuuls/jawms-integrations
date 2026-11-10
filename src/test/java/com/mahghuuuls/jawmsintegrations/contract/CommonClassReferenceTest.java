package com.mahghuuuls.jawmsintegrations.contract;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonClassReferenceTest {

    private static final String[] FORBIDDEN = {
            "com/tmtravlr/qualitytools/",
            "com/windanesz/ancientspellcraft/",
            "com/tmtravlr/potioncore/",
            "crafttweaker/CraftTweakerAPI",
            "crafttweaker/api/",
            "crafttweaker/annotations/",
            "crafttweaker/runtime/",
            "crafttweaker/mc1120/",
            "stanhebben/zenscript/",
            "com/mahghuuuls/jafs/",
            "am2/"
    };

    @Test
    void sharedProductionClassesDoNotReferenceOptionalModPackages() throws Exception {
        Path classes = Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"));
        Path root = classes.resolve("com/mahghuuuls/jawmsintegrations");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> inspect(path, violations));
        }

        assertTrue(violations.isEmpty(), "Optional class references in shared code: " + violations);
    }

    private static boolean isSharedClass(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/integration/qualitytools/")
                && !normalized.contains("/integration/ancientspellcraft/")
                && !normalized.contains("/integration/crafttweaker/")
                && !normalized.contains("/integration/jafs/")
                && !normalized.contains("/mixin/qualitytools/")
                && !normalized.contains("/mixin/ancientspellcraft/");
    }

    private static void inspect(Path path, List<String> violations) {
        try {
            String constants = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
            if (isSharedClass(path)) {
                for (String forbidden : FORBIDDEN) {
                    if (constants.contains(forbidden)) {
                        violations.add(path.getFileName() + " -> " + forbidden);
                    }
                }
            }
            String normalized = path.toString().replace('\\', '/');
            boolean gameplayContract = normalized.endsWith("/integration/statusworks/StatusworksGameplayContract.class");
            boolean gameplayAdapter = normalized.endsWith("/integration/statusworks/StatusworksGameplay.class")
                    || normalized.contains("/integration/statusworks/StatusworksGameplay$");
            boolean nativeProjection = normalized.endsWith("/integration/statusworks/StatusworksProjection.class");
            boolean nativeAuthoring = normalized.endsWith("/integration/statusworks/StatusworksAuthoring.class")
                    || normalized.endsWith("/integration/statusworks/StatusworksAuthoring$InertType.class");
            if (!gameplayContract && !gameplayAdapter && !nativeProjection && !nativeAuthoring
                    && constants.contains("com/mahghuuuls/statusworks/")) {
                violations.add(path.getFileName() + " -> Statusworks API");
            }
            if (!gameplayContract && !gameplayAdapter
                    && constants.contains("com/mahghuuuls/jawms/api/ManaFirstAcceptanceVerifier")) {
                violations.add(path.getFileName() + " -> JAWMS first acceptance API");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inspect " + path, exception);
        }
    }
}
