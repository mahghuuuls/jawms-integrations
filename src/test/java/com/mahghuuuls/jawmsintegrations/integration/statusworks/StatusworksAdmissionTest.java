package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaFirstAcceptanceVerifier;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot.IntegrationToggleConfig;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StatusworksAdmissionTest {
    @Test
    void nativeAuthoringNamesMatchTheSharedStatCatalog() {
        List<String> expected = Arrays.asList(
                "jawmsintegrations:max_mana_flat", "jawmsintegrations:max_mana_percent",
                "jawmsintegrations:mana_regen_flat", "jawmsintegrations:mana_regen_percent",
                "jawmsintegrations:mana_regen_delay_flat", "jawmsintegrations:mana_regen_delay_percent",
                "jawmsintegrations:spell_efficiency", "jawmsintegrations:spell_efficiency_magic",
                "jawmsintegrations:spell_efficiency_fire", "jawmsintegrations:spell_efficiency_ice",
                "jawmsintegrations:spell_efficiency_lightning", "jawmsintegrations:spell_efficiency_necromancy",
                "jawmsintegrations:spell_efficiency_earth", "jawmsintegrations:spell_efficiency_sorcery",
                "jawmsintegrations:spell_efficiency_healing");
        assertEquals(expected, StatusworksAuthoring.typeIds());
    }

    @Test
    void gameplayProbeLinksTheExactDevelopmentDependencies() throws Exception {
        StatusworksGameplayContract.verify();
    }

    @Test
    void gameplayProbeRejectsAnIncompleteRecoveryProvider() {
        NoSuchMethodException missing = assertThrows(NoSuchMethodException.class,
                () -> StatusworksGameplayContract.verifyReadinessProvider(ProviderWithoutUpdate.class));
        assertTrue(missing.getMessage().contains(".update("));
    }

    @Test
    void gameplayProbeRejectsAnInstanceRegistrationMethod() {
        NoSuchMethodException incompatible = assertThrows(NoSuchMethodException.class,
                () -> StatusworksGameplayContract.verifyManaApi(InstanceRegistrationApi.class));
        assertTrue(incompatible.getMessage().contains(".registerReadinessProvider"));
    }

    @Test
    void absentDependencyDoesNotResolveEitherOptionalPath() {
        IntegrationCoordinator absent = coordinator(null, true);
        assertSame(absent, StatusworksBootstrap.initialize(absent, api(Api110.class),
                StatusworksAdmissionTest::unexpectedOptionalPath, StatusworksAdmissionTest::unexpectedOptionalPath,
                StatusworksAdmissionTest::unexpectedActivation).coordinator);
    }

    @Test
    void legacyDisabledAndMissingJawmsApiRunAuthoringOnly() {
        AtomicInteger authoringCalls = new AtomicInteger();
        assertEquals(IntegrationState.UNSUPPORTED,
                status(initializeAuthoringOnly("1.2.0", true, Api110.class, authoringCalls)).getState());
        assertEquals(IntegrationState.DISABLED,
                status(initializeAuthoringOnly("1.3.0", false, Api110.class, authoringCalls)).getState());
        IntegrationStatusView oldApi = status(initializeAuthoringOnly("1.3.0", true, Api19.class, authoringCalls));
        assertEquals(IntegrationState.UNSUPPORTED, oldApi.getState());
        assertTrue(oldApi.getDetail().contains("Requires JAWMS API 1.10"));
        assertEquals(3, authoringCalls.get());
    }

    @Test
    void unavailableGameplayContractPreservesRegisteredAuthoring() {
        AtomicInteger authoringCalls = new AtomicInteger();
        IntegrationStatusView status = status(StatusworksBootstrap.initialize(coordinator("1.3.0", true),
                api(Api110.class), authoringCalls::incrementAndGet, () -> {
                    assertEquals(0, authoringCalls.get());
                    throw new NoSuchMethodException("update");
                }, StatusworksAdmissionTest::unexpectedActivation).coordinator);
        assertEquals(IntegrationState.UNSUPPORTED, status.getState());
        assertTrue(status.getDetail().contains("Native contribution types are registered"));
        assertEquals(1, authoringCalls.get());
    }

    @Test
    void inertAuthoringDoesNotLinkNewGameplayTypesOrClientClasses() throws Exception {
        Path classes = Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"),
                "com", "mahghuuuls", "jawmsintegrations");
        for (String file : Arrays.asList("StatusworksAuthoring", "StatusworksAuthoring$InertType")) {
            String bytes = classBytes(classes, "integration/statusworks/" + file);
            for (String reference : Arrays.asList("ContributionGroup", "GroupAvailability", "ManaReadinessProvider",
                    "ManaFirstAcceptanceVerifier", "net/minecraft/client/")) {
                assertFalse(bytes.contains(reference), file + " linked " + reference);
            }
        }
    }

    private static String classBytes(Path classes, String file) throws Exception {
        return new String(Files.readAllBytes(classes.resolve(file + ".class")), StandardCharsets.ISO_8859_1);
    }

    private static IntegrationCoordinator initializeAuthoringOnly(String version, boolean enabled, Class<?> apiVersion,
                                                                  AtomicInteger authoringCalls) {
        return StatusworksBootstrap.initialize(coordinator(version, enabled), api(apiVersion),
                authoringCalls::incrementAndGet, StatusworksAdmissionTest::unexpectedOptionalPath,
                StatusworksAdmissionTest::unexpectedActivation).coordinator;
    }

    private static IntegrationCoordinator coordinator(String version, boolean enabled) {
        IntegrationConfigSnapshot defaults = IntegrationConfigSnapshot.defaults();
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(defaults.getQualityTools(),
                defaults.getAncientSpellcraft(), defaults.getCraftTweaker(), defaults.getJafs(),
                defaults.getSpellStamina(), defaults.getSetworks(), new IntegrationToggleConfig(enabled),
                defaults.getDiagnostics());
        return IntegrationCoordinator.initialize(config,
                id -> IntegrationId.STATUSWORKS.getModId().equals(id) ? version : null, id -> null);
    }

    private static IntegrationStatusView status(IntegrationCoordinator coordinator) {
        return coordinator.getStatus(IntegrationId.STATUSWORKS);
    }

    private static JawmsCompatibility.Status api(Class<?> version) {
        return JawmsCompatibility.verify("1.5.0", version);
    }

    private static void unexpectedOptionalPath() {
        throw new AssertionError("Ineligible optional path was invoked");
    }

    private static StatusworksSourceAccess unexpectedActivation() {
        throw new AssertionError("Ineligible gameplay was activated");
    }

    public static final class Api19 {
        public static final String CURRENT = "1.9";
    }

    public static final class Api110 {
        public static final String CURRENT = "1.10";
    }

    public static final class ProviderWithoutUpdate {
        public Optional<ManaProfileGeneration> getGeneration(EntityPlayer player) {
            return Optional.empty();
        }

        public ManaContributionPublicationResult completeInitial(EntityPlayer player, ManaProfileGeneration generation,
                                                                  ManaContribution contribution) {
            throw new AssertionError("Signature probe must not invoke the provider");
        }

        public ManaContributionPublicationResult suspend(EntityPlayer player, ManaProfileGeneration generation,
                                                          long revision) {
            throw new AssertionError("Signature probe must not invoke the provider");
        }
    }

    public static final class InstanceRegistrationApi {
        public ManaReadinessProvider registerReadinessProvider(ResourceLocation id,
                                                              ManaFirstAcceptanceVerifier verifier) {
            throw new AssertionError("Signature probe must not register a provider");
        }

        public static ManaProfileExplanation explainManaProfile(EntityPlayer player) {
            throw new AssertionError("Signature probe must not query player state");
        }
    }
}
