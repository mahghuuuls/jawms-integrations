package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import com.mahghuuuls.jawmsintegrations.integration.jafs.FoodSourceAccess;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.concurrent.atomic.AtomicInteger;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationDiagnosticsServiceTest {

    @Test
    void disabledMetadataStatusNeverQueriesFoodAndPreservesReadOnlyAttribution() {
        IntegrationConfigSnapshot defaults = IntegrationConfigSnapshot.defaults();
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(defaults.getQualityTools(),
                defaults.getAncientSpellcraft(), defaults.getCraftTweaker(),
                new IntegrationConfigSnapshot.IntegrationToggleConfig(false), defaults.getDiagnostics());
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(config,
                id -> IntegrationId.JAFS.getModId().equals(id) ? "1.0.0" : null,
                id -> OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        coordinator = coordinator.withDisabledDetail(IntegrationId.JAFS,
                "Property metadata available; mana effects disabled; no food contribution applied");
        FoodSourceAccess forbidden = new FoodSourceAccess() {
            @Override public ManaStatProjection.Result inspect(EntityPlayer player) {
                throw new AssertionError("Disabled diagnostics queried native player foods");
            }
            @Override public void clearWarnings() { throw new AssertionError("Read-only diagnostics cleared warnings"); }
        };
        IntegrationDiagnosticsService view = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.4.1", CompatibleApi.class), config, coordinator, forbidden);
        for (int i = 0; i < 3; i++) {
            assertTrue(view.foodStatus(null).get(0).contains("DISABLED"));
            assertTrue(view.foodStatus(null).get(0).contains("metadata available"));
            assertTrue(view.foodStatus(null).get(0).contains("no food contribution applied"));
            assertTrue(view.overallStatus().stream().anyMatch(line -> line.contains("metadata available")));
        }
        assertEquals(IntegrationState.DISABLED, coordinator.getStatus(IntegrationId.JAFS).getState());
    }

    @Test
    void boundedStartupAndOverallOutputUseAuthoritativeAbsentStates() {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(
                config,
                modId -> null,
                integration -> OptionalIntegrationEvidenceRegistry.Evidence.absent()
        );
        IntegrationDiagnosticsService diagnostics = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.4.1", CompatibleApi.class),
                config,
                coordinator
        );

        String startup = diagnostics.startupSummary();
        List<String> overall = diagnostics.overallStatus();

        assertTrue(startup.contains("JAWMS 1.4.1/API 1.6"));
        assertTrue(startup.contains("Quality Tools=ABSENT"));
        assertTrue(startup.contains("Ancient Spellcraft=ABSENT"));
        assertTrue(startup.contains("CraftTweaker=ABSENT"));
        assertTrue(startup.contains("Spell stamina=ABSENT"));
        assertTrue(startup.contains("Setworks=ABSENT"));
        assertTrue(startup.contains("built-in qualities=enabled(12/12)"));
        assertTrue(startup.contains("Ancient replacements=inactive(state=ABSENT)"));
        assertEquals(9, overall.size());
        assertTrue(overall.get(1).contains("Quality Tools: ABSENT"));
        assertTrue(overall.get(2).contains("Ancient Spellcraft: ABSENT"));
        assertTrue(overall.get(3).contains("CraftTweaker: ABSENT"));
        assertTrue(overall.get(4).contains("Just Another Food System: ABSENT"));
        assertTrue(overall.get(5).contains("Spell stamina: ABSENT"));
        assertTrue(overall.get(6).contains("Setworks: ABSENT"));
        String settings = overall.get(7);
        assertTrue(settings.contains("startup diagnostics=disabled"));
        assertTrue(settings.contains("built-in qualities=enabled(12/12)"));
        assertTrue(settings.contains("Ancient replacements=inactive(state=ABSENT)"));
        assertTrue(settings.contains("CraftTweaker=enabled"));
        assertTrue(settings.contains("JAFS=enabled"));
        assertTrue(settings.contains("Setworks=enabled"));
        assertTrue(overall.get(8).contains("configured, inactive"));
        assertEquals("Quality Tools reload summary: built-in qualities=enabled(12/12)",
                diagnostics.qualityToolsReloadSummary());
    }

    @Test
    void acceptedDependenciesAreReportedReadyUntilTheirFeatureOwnersActivate() {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(
                config,
                modId -> {
                    for (IntegrationId integration : IntegrationId.values()) {
                        if (integration.getModId().equals(modId)) {
                            return integration.getMinimumMetadataVersion();
                        }
                    }
                    return null;
                },
                integration -> OptionalIntegrationEvidenceRegistry.Evidence.supported(
                        integration.getMinimumMetadataVersion())
        );
        IntegrationDiagnosticsService diagnostics = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.4.1", CompatibleApi.class), config, coordinator);

        String startup = diagnostics.startupSummary();
        List<String> overall = diagnostics.overallStatus();
        assertTrue(startup.contains("Quality Tools=READY(1.0.7_for_1.12.2)"));
        assertTrue(startup.contains("CraftTweaker=READY(1.12-4.1.20.715)"));
        assertTrue(overall.get(1).contains("minimum=1.0.7"));
        assertTrue(overall.get(3).contains("CraftTweaker: READY"));
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

    @Test
    void sourceStatusDistinguishesInactiveZeroInvalidValidAndUnavailableWithoutAcceptingMana() throws Exception {
        IntegrationConfigSnapshot config = IntegrationConfigSnapshot.defaults();
        IntegrationCoordinator ready = IntegrationCoordinator.initialize(config, ignored -> "1.0.0",
                ignored -> OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        IntegrationCoordinator active = ready.withActive(IntegrationId.JAFS, "registered");
        AtomicInteger calls = new AtomicInteger();
        ManaStatProjection.Totals[] maximum = {ManaStatProjection.Totals.ZERO};
        FoodSourceAccess source = new FoodSourceAccess() {
            @Override public ManaStatProjection.Result inspect(EntityPlayer player) {
                calls.incrementAndGet();
                if (maximum[0] == null) throw new IllegalStateException("not available");
                return ManaStatProjection.project(stat -> stat == ManaStatProjection.Stat.MAX_MANA
                        ? maximum[0] : ManaStatProjection.Totals.ZERO);
            }
            @Override public void clearWarnings() { throw new AssertionError("Inspection cleared warnings"); }
        };
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        EntityPlayer player = (EntityPlayer) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), NamedPlayer.class);
        IntegrationDiagnosticsService view = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.4.1", CompatibleApi.class), config, active, source);
        assertTrue(view.foodStatus(player).get(0).contains("ZERO"));
        maximum[0] = new ManaStatProjection.Totals(0.5, 10);
        List<String> invalid = view.foodStatus(player);
        assertTrue(invalid.get(0).contains("INVALID FIELDS OMITTED"));
        assertTrue(invalid.get(0).contains("maximumManaIncrease=10.0"));
        assertTrue(invalid.stream().anyMatch(line -> line.contains("FRACTIONAL_MAXIMUM")));
        assertTrue(invalid.stream().anyMatch(line -> line.contains("flat=0.5, percent=10.0")));
        maximum[0] = new ManaStatProjection.Totals(8, 0);
        assertTrue(view.foodStatus(player).get(0).contains("VALID"));
        maximum[0] = null;
        assertTrue(view.foodStatus(player).get(0).contains("unavailable (IllegalStateException)"));
        assertEquals(4, calls.get());
        IntegrationDiagnosticsService inactive = new IntegrationDiagnosticsService(
                JawmsCompatibility.verify("1.4.1", CompatibleApi.class), config, ready, source);
        assertTrue(inactive.foodStatus(player).get(0).contains("inactive"));
        assertEquals(4, calls.get(), "Inactive modules must not query optional API state");
    }

    private static final class NamedPlayer extends EntityPlayerMP {
        private NamedPlayer() { super(null, null, null, null); }
        @Override public String getName() { return "FoodPlayer"; }
    }

    public static final class CompatibleApi {
        public static final String CURRENT = "1.6";
    }
}
