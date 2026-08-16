package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalMixinGateRegistry;
import electroblob.wizardry.constants.Element;
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
        assertTrue(startup.contains("built-in qualities=enabled(12/12)"));
        assertTrue(startup.contains("Ancient replacements=inactive(state=ABSENT)"));
        assertEquals(4, overall.size());
        assertTrue(overall.get(1).contains("Quality Tools: ABSENT"));
        assertTrue(overall.get(2).contains("Ancient Spellcraft: ABSENT"));
        assertTrue(overall.get(3).contains("startup diagnostics=disabled"));
        assertTrue(overall.get(3).contains("built-in qualities=enabled(12/12)"));
        assertTrue(overall.get(3).contains("Ancient replacements=inactive(state=ABSENT)"));
        assertEquals("Quality Tools reload summary: built-in qualities=enabled(12/12)",
                diagnostics.qualityToolsReloadSummary());
    }

    @Test
    void contributionFormattingReportsNumericFieldsInsteadOfObjectIdentity() {
        ManaContribution contribution = ManaContribution.builder()
                .flatMaximumMana(8)
                .maximumManaIncrease(10.0D)
                .flatRegeneration(1.5D)
                .regenerationIncrease(20.0D)
                .flatLockoutSeconds(-1.25D)
                .lockoutReduction(30.0D)
                .globalSpellEfficiency(25.0D)
                .elementSpellEfficiency(Element.FIRE, 5.0D)
                .build();

        String formatted = IntegrationDiagnosticsService.formatContribution(contribution);

        assertEquals("{flatMaximumMana=8, maximumManaIncrease=10.0, flatRegeneration=1.5, "
                        + "regenerationIncrease=20.0, flatLockoutSeconds=-1.25, lockoutReduction=30.0, "
                        + "spellEfficiency=25.0, elementSpellEfficiency={FIRE=5.0}}",
                formatted);
        assertTrue(!formatted.contains("@"));
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.4";
    }
}
