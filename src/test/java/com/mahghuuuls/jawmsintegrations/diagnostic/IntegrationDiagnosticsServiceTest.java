package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationDiagnosticsServiceTest {

    @Test
    void boundedStartupAndOverallOutputUseAuthoritativeAbsentStates() {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(
                config,
                modId -> null,
                integration -> OptionalMixinGateRegistry.Evidence.absent()
        );
        IntegrationDiagnosticsService diagnostics = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("0.4.0", CompatibleApi.class),
                config,
                coordinator
        );

        String startup = diagnostics.startupSummary();
        List<String> overall = diagnostics.overallStatus();

        assertTrue(startup.contains("JAWMS 0.4.0/API 1.4"));
        assertTrue(startup.contains("Quality Tools=ABSENT"));
        assertTrue(startup.contains("Ancient Spellcraft=ABSENT"));
        assertEquals(4, overall.size());
        assertTrue(overall.get(1).contains("Quality Tools: ABSENT"));
        assertTrue(overall.get(2).contains("Ancient Spellcraft: ABSENT"));
        assertTrue(overall.get(3).contains("startup diagnostics=disabled"));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.4";
    }
}
