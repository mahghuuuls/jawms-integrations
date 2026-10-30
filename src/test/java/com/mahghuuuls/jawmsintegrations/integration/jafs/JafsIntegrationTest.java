package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jafs.api.ActiveFoodView;
import com.mahghuuuls.jafs.api.IFoodService;
import com.mahghuuuls.jafs.api.IPropertyApplier;
import com.mahghuuuls.jafs.api.PaymentResult;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.api.PropertyValue;
import com.mahghuuuls.jawms.api.IContextualManaModifierProvider;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.OptionalIntegrationEvidenceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class JafsIntegrationTest {
    @Test
    void disabledCommonStartupRetainsMetadataWithoutRequestingAnyEffectBoundary() {
        IntegrationConfigSnapshot defaults = IntegrationConfigSnapshot.defaults();
        IntegrationConfigSnapshot config = new IntegrationConfigSnapshot(defaults.getQualityTools(),
                defaults.getAncientSpellcraft(), defaults.getCraftTweaker(),
                new IntegrationConfigSnapshot.IntegrationToggleConfig(false), defaults.getDiagnostics());
        IntegrationCoordinator coordinator = IntegrationCoordinator.initialize(config,
                id -> IntegrationId.JAFS.getModId().equals(id) ? "1.0.0" : null,
                id -> OptionalIntegrationEvidenceRegistry.Evidence.supported("1.0.0"));
        RecordingFoods service = new RecordingFoods();
        RecordingRegistration registration = new RecordingRegistration(service);
        registration.rejectService = true;
        AtomicInteger entryCalls = new AtomicInteger();
        EntityPlayerMP player = player();
        JafsRegistrationBootstrap.Result result = JafsRegistrationBootstrap.initialize(coordinator, effects -> {
            entryCalls.incrementAndGet();
            assertFalse(effects);
            JafsIntegration module = JafsIntegration.install(effects, registration,
                    ignored -> fail("Disabled callback dirtied mana"),
                    ignored -> fail("Disabled callback warned"), ignored -> fail("Disabled callback failed"));
            assertSame(ManaContribution.EMPTY, module.getContribution(player));
            assertThrows(IllegalStateException.class, () -> module.inspect(player));
            for (IPropertyApplier applier : registration.appliers.values()) {
                applier.apply(player, 20, 10);
                applier.apply(null, 0, 0);
            }
            return null;
        });
        assertEquals(1, entryCalls.get(), "Compatible disabled startup must not skip metadata");
        assertEquals(12, registration.appliers.size());
        assertEquals(15, registration.specs.stream().mapToInt(spec -> spec.operations().size()).sum());
        assertEquals(1, registration.metadataChecks);
        assertEquals(0, registration.serviceCalls);
        assertNull(registration.provider);
        assertEquals(0, registration.hookCalls);
        assertEquals(0, service.reads);
        assertNull(result.getFoodSource());
        assertEquals(IntegrationState.DISABLED, result.getCoordinator().getStatus(IntegrationId.JAFS).getState());
        assertTrue(result.getCoordinator().getStatus(IntegrationId.JAFS).getDetail().contains("metadata available"));
        assertEquals("Disabled by configuration", coordinator.getStatus(IntegrationId.JAFS).getDetail(),
                "Coordinator transition must not mutate an earlier snapshot");
    }

    @Test
    void enabledAndDisabledUseTheSameNativeSpecsAndEnabledEffectsOpenOnlyAfterHooks() {
        RecordingFoods service = new RecordingFoods();
        service.maximum = new PropertyValue(20, 0, 1);
        RecordingRegistration enabled = new RecordingRegistration(service);
        AtomicInteger dirty = new AtomicInteger();
        EntityPlayerMP player = player();
        enabled.duringPublish = () -> {
            for (IPropertyApplier applier : enabled.appliers.values()) applier.apply(player, 99, 99);
            if (enabled.provider != null) assertSame(ManaContribution.EMPTY, enabled.provider.getContribution(player));
            assertEquals(0, dirty.get());
            assertEquals(0, service.reads);
        };
        JafsIntegration active = JafsIntegration.install(true, enabled, ignored -> dirty.incrementAndGet(),
                ignored -> {}, ignored -> {});
        assertEquals("metadata", enabled.order.get(0));
        assertEquals("service", enabled.order.get(1));
        assertEquals("provider", enabled.order.get(14));
        assertEquals("hooks", enabled.order.get(15));
        assertEquals(1, enabled.hookCalls);
        enabled.appliers.values().iterator().next().apply(player, 99, 99);
        assertEquals(1, dirty.get());
        assertEquals(20, active.getContribution(player).getFlatMaximumMana());
        RecordingRegistration disabled = new RecordingRegistration(null);
        disabled.rejectService = true;
        JafsIntegration.install(false, disabled, ignored -> fail("dirty"), ignored -> {}, ignored -> {});
        for (int i = 0; i < enabled.specs.size(); i++) {
            PropertySpec expected = enabled.specs.get(i);
            PropertySpec actual = disabled.specs.get(i);
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.displayNameKey(), actual.displayNameKey());
            assertEquals(expected.unit(), actual.unit());
            assertEquals(expected.operations(), actual.operations());
            for (PropertyOperation operation : expected.operations()) {
                assertEquals(expected.range(operation).min(), actual.range(operation).min());
                assertEquals(expected.range(operation).max(), actual.range(operation).max());
            }
        }
    }

    @Test
    void modePrerequisitesFailBeforeAnyIrreversibleWrite() {
        for (boolean effects : new boolean[]{false, true}) {
            RecordingRegistration invalid = new RecordingRegistration(new RecordingFoods());
            invalid.apiVersion = "0.9";
            assertThrows(IllegalStateException.class, () -> JafsIntegration.install(effects, invalid,
                    ignored -> fail("dirty"), ignored -> {}, ignored -> {}));
            assertTrue(invalid.appliers.isEmpty());
            assertNull(invalid.provider);
            assertEquals(0, invalid.serviceCalls);
            assertEquals(0, invalid.hookCalls);
        }
        for (boolean missingService : new boolean[]{false, true}) {
            RecordingRegistration invalid = new RecordingRegistration(missingService ? null : new RecordingFoods());
            invalid.serviceFailure = missingService ? null : new IllegalStateException("mana registry frozen");
            assertThrows(IllegalStateException.class, () -> JafsIntegration.install(true, invalid,
                    ignored -> fail("dirty"), ignored -> {}, ignored -> {}));
            assertTrue(invalid.appliers.isEmpty());
            assertNull(invalid.provider);
            assertEquals(0, invalid.hookCalls);
        }
    }

    @Test
    void everyPartialMetadataFailureAndBothEffectPublicationFailuresStayInert() {
        EntityPlayerMP player = player();
        for (boolean effects : new boolean[]{false, true}) {
            for (int position = 0; position < 12; position++) {
                RecordingFoods service = new RecordingFoods();
                RecordingRegistration registration = new RecordingRegistration(service);
                registration.failAt = position;
                assertThrows(IllegalStateException.class, () -> JafsIntegration.install(effects, registration,
                        ignored -> fail("Partial metadata callback dirtied mana"), ignored -> {}, ignored -> {}));
                assertEquals(position, registration.appliers.size());
                assertNull(registration.provider);
                for (IPropertyApplier applier : registration.appliers.values()) applier.apply(player, 20, 0);
                assertEquals(0, registration.hookCalls);
                assertEquals(0, service.reads);
            }
        }
        for (boolean failAtProvider : new boolean[]{true, false}) {
            RecordingFoods service = new RecordingFoods();
            RecordingRegistration registration = new RecordingRegistration(service);
            registration.providerFailure = failAtProvider;
            registration.hookFailure = !failAtProvider;
            assertThrows(IllegalStateException.class, () -> JafsIntegration.install(true, registration,
                    ignored -> fail("Failed activation callback dirtied mana"), ignored -> {}, ignored -> {}));
            assertEquals(12, registration.appliers.size());
            assertNotNull(registration.provider);
            assertSame(ManaContribution.EMPTY, registration.provider.getContribution(player));
            for (IPropertyApplier applier : registration.appliers.values()) applier.apply(player, 20, 0);
            assertEquals(failAtProvider ? 0 : 1, registration.hookCalls);
            assertEquals(0, service.reads);
        }
    }

    @Test
    void registersTheExactTwelveNativePropertiesAndFifteenOperations() {
        List<PropertySpec> specs = JafsIntegration.propertySpecs();
        String[] names = {"max_mana", "mana_regen", "mana_regen_delay", "spell_efficiency",
                "spell_efficiency_magic", "spell_efficiency_fire", "spell_efficiency_ice",
                "spell_efficiency_lightning", "spell_efficiency_necromancy", "spell_efficiency_earth",
                "spell_efficiency_sorcery", "spell_efficiency_healing"};
        assertEquals(names.length, specs.size());
        int operations = 0;
        for (int i = 0; i < names.length; i++) {
            PropertySpec spec = specs.get(i);
            assertEquals("jawmsintegrations:" + names[i], spec.id());
            assertEquals("property.jawmsintegrations." + names[i], spec.displayNameKey());
            assertEquals(i < 3, spec.supports(PropertyOperation.PERCENT));
            assertEquals(i < 3 ? -Double.MAX_VALUE : 0, spec.range(PropertyOperation.FLAT).min());
            operations += spec.operations().size();
        }
        assertEquals(15, operations);
        assertTrue(specs.get(0).range(PropertyOperation.FLAT).contains(0.5));
        assertFalse(specs.get(0).range(PropertyOperation.PERCENT).contains(-101));
        assertFalse(specs.get(1).range(PropertyOperation.FLAT).contains(Double.POSITIVE_INFINITY));
        assertEquals("s", specs.get(2).unit());
    }

    @Test
    void nativeFirstReadPrecedesCallbacksAndRefreshNeverStoresCallbackTotals() {
        RecordingFoods service = new RecordingFoods();
        service.maximum = new PropertyValue(20, 10, 1);
        EntityPlayerMP player = player();
        RecordingRegistration registration = new RecordingRegistration(service);
        AtomicInteger dirty = new AtomicInteger();
        JafsIntegration module = JafsIntegration.install(true, registration, ignored -> dirty.incrementAndGet(),
                ignored -> {}, ignored -> {});
        assertEquals(20, module.getContribution(player).getFlatMaximumMana());
        assertEquals(12, service.reads);
        service.maximum = new PropertyValue(0.5, 10, 1);
        registration.appliers.get("jawmsintegrations:max_mana").apply(player, 999, 999);
        assertEquals(1, dirty.get());
        ManaContribution after = module.getContribution(player);
        assertEquals(0, after.getFlatMaximumMana());
        assertEquals(10, after.getMaximumManaIncrease());
        service.maximum = PropertyValue.ZERO;
        assertTrue(module.getContribution(player).isEmpty());
    }

    @Test
    void partialMetadataRegistrationLeavesNoProviderAndInertCallbacks() {
        RecordingFoods service = new RecordingFoods();
        RecordingRegistration registration = new RecordingRegistration(service);
        registration.failAt = 4;
        AtomicInteger dirty = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> JafsIntegration.install(true, registration,
                ignored -> dirty.incrementAndGet(), ignored -> {}, ignored -> {}));
        assertNull(registration.provider, "Metadata failure must precede provider publication");
        EntityPlayerMP player = player();
        assertEquals(0, registration.hookCalls);
        for (IPropertyApplier applier : registration.appliers.values()) applier.apply(player, 20, 0);
        assertEquals(0, dirty.get());
        assertEquals(0, service.reads);
        assertEquals(4, registration.appliers.size());
    }

    @Test
    void sourceFaultsContributeZeroWithoutKeepingThePreviousValueAndCanRecover() {
        RecordingFoods service = new RecordingFoods();
        service.maximum = new PropertyValue(20, 0, 1);
        EntityPlayerMP player = player();
        List<String> warnings = new ArrayList<>();
        JafsIntegration module = JafsIntegration.install(true, new RecordingRegistration(service),
                ignored -> {}, warnings::add, ignored -> {});
        assertEquals(20, module.getContribution(player).getFlatMaximumMana());
        service.failure = new IllegalStateException("not available");
        assertTrue(module.getContribution(player).isEmpty());
        assertTrue(module.getContribution(player).isEmpty());
        assertEquals(1, warnings.size());
        service.failure = null;
        service.maximum = new PropertyValue(8, 0, 1);
        assertEquals(8, module.getContribution(player).getFlatMaximumMana());
    }

    @Test
    void fatalLinkageDisablesTheGuardAndNotifiesTheCoordinatorBoundaryOnce() {
        RecordingFoods service = new RecordingFoods();
        service.linkageFailure = new AbstractMethodError();
        List<String> failures = new ArrayList<>();
        AtomicInteger dirty = new AtomicInteger();
        RecordingRegistration registration = new RecordingRegistration(service);
        JafsIntegration module = JafsIntegration.install(true, registration, ignored -> dirty.incrementAndGet(),
                ignored -> {}, failures::add);
        EntityPlayerMP player = player();
        assertTrue(module.getContribution(player).isEmpty());
        assertTrue(module.getContribution(player).isEmpty());
        assertEquals(1, failures.size());
        registration.appliers.values().iterator().next().apply(player, 1, 1);
        assertEquals(0, dirty.get());
        assertEquals(1, service.reads);
    }

    @Test
    void guardWarningsRemainBoundedEvenWhenTheInvalidAmountChanges() {
        RecordingFoods service = new RecordingFoods();
        service.maximum = new PropertyValue(0.5, 10, 1);
        List<String> warnings = new ArrayList<>();
        JafsIntegration module = JafsIntegration.install(true, new RecordingRegistration(service),
                ignored -> {}, warnings::add, ignored -> {});
        EntityPlayerMP player = player();
        assertEquals(10, module.getContribution(player).getMaximumManaIncrease());
        service.maximum = new PropertyValue(0.25, 10, 1);
        assertEquals(10, module.getContribution(player).getMaximumManaIncrease());
        assertEquals(1, warnings.size());
    }

    @Test
    void inspectionDoesNotDirtyLogAcceptOrDisableAndWarningsExpireWithPlayerSessions() {
        RecordingFoods service = new RecordingFoods();
        service.maximum = new PropertyValue(0.5, 10, 1);
        List<String> warnings = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        AtomicInteger dirty = new AtomicInteger();
        JafsIntegration module = JafsIntegration.install(true, new RecordingRegistration(service),
                ignored -> dirty.incrementAndGet(), warnings::add, failures::add);
        EntityPlayerMP first = player();
        EntityPlayerMP second = player();
        assertEquals(1, module.inspect(first).getProblems().size());
        assertEquals(0, dirty.get());
        assertTrue(warnings.isEmpty());
        module.getContribution(first);
        module.getContribution(first);
        module.getContribution(second);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains(first.getUniqueID().toString()));
        assertTrue(warnings.get(1).contains(second.getUniqueID().toString()));
        module.onLogout(new PlayerEvent.PlayerLoggedOutEvent(first));
        module.getContribution(first);
        module.getContribution(second);
        assertEquals(3, warnings.size());
        module.clearWarnings();
        module.getContribution(second);
        assertEquals(4, warnings.size());
        service.linkageFailure = new AbstractMethodError();
        assertThrows(AbstractMethodError.class, () -> module.inspect(first));
        assertTrue(failures.isEmpty(), "Read-only inspection cannot change activation state");
        service.linkageFailure = null;
        assertEquals(10, module.getContribution(first).getMaximumManaIncrease());
        assertEquals(0, dirty.get());
    }

    @Test
    void nativeTotalsRemainPlayerSpecific() {
        RecordingFoods service = new RecordingFoods();
        EntityPlayerMP first = player();
        EntityPlayerMP second = player();
        service.players.put(first, new PropertyValue(8, 0, 1));
        service.players.put(second, new PropertyValue(12, 0, 1));
        JafsIntegration module = JafsIntegration.install(true, new RecordingRegistration(service),
                ignored -> {}, ignored -> {}, ignored -> {});
        assertEquals(8, module.getContribution(first).getFlatMaximumMana());
        assertEquals(12, module.getContribution(second).getFlatMaximumMana());
        assertEquals(8, module.getContribution(first).getFlatMaximumMana());
    }

    @Test
    void metadataRequiresItsOpenRegistryAndTheMinimumApiWithoutAnUpperBound() {
        assertDoesNotThrow(() -> JafsIntegration.requireMetadataRegistration("1.0", true, false));
        assertDoesNotThrow(() -> JafsIntegration.requireMetadataRegistration("2.3", true, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireMetadataRegistration("0.9", true, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireMetadataRegistration("unknown", true, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireMetadataRegistration("1.0", false, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireMetadataRegistration("1.0", true, true));

    }

    private static EntityPlayerMP player() {
        try {
            // Same constructor-free identity fixture as the existing scripting tests.
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field field = unsafeType.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            TestPlayer player = (TestPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                    .invoke(field.get(null), TestPlayer.class);
            player.id = UUID.randomUUID();
            return player;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class TestPlayer extends EntityPlayerMP {
        private UUID id;
        private TestPlayer() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return id; }
    }

    private static final class RecordingFoods implements IFoodService {
        private PropertyValue maximum = PropertyValue.ZERO;
        private final Map<EntityPlayerMP, PropertyValue> players = new IdentityHashMap<>();
        private RuntimeException failure;
        private LinkageError linkageFailure;
        private int reads;

        @Override
        public PropertyValue getPropertyValue(EntityPlayerMP player, String property) {
            reads++;
            if (failure != null) throw failure;
            if (linkageFailure != null) throw linkageFailure;
            return "jawmsintegrations:max_mana".equals(property)
                    ? players.getOrDefault(player, maximum) : PropertyValue.ZERO;
        }

        @Override public List<ActiveFoodView> getActiveFoods(EntityPlayerMP player) {
            throw new AssertionError("Unexpected food-state mirror");
        }
        @Override public boolean canPay(EntityPlayerMP player, int amount) {
            throw new AssertionError("Unexpected hunger payment");
        }
        @Override public PaymentResult pay(EntityPlayerMP player, int amount, String reason) {
            throw new AssertionError("Unexpected hunger payment");
        }
        @Override public int clearAll(EntityPlayerMP player, String reason) {
            throw new AssertionError("Unexpected food mutation");
        }
    }

    private static final class RecordingRegistration implements JafsIntegration.Registration {
        private final IFoodService service;
        private IContextualManaModifierProvider provider;
        private final Map<String, IPropertyApplier> appliers = new LinkedHashMap<>();
        private int failAt = -1;
        private final List<PropertySpec> specs = new ArrayList<>();
        private final List<String> order = new ArrayList<>();
        private int metadataChecks;
        private int serviceCalls;
        private int hookCalls;
        private boolean rejectService;
        private String apiVersion = "1.0";
        private RuntimeException serviceFailure;
        private boolean providerFailure;
        private boolean hookFailure;
        private Runnable duringPublish;

        private RecordingRegistration(IFoodService service) {
            this.service = service;
        }

        @Override public void validateMetadata() {
            metadataChecks++;
            order.add("metadata");
            JafsIntegration.requireMetadataRegistration(apiVersion, true, false);
        }
        @Override public IFoodService validatedService() {
            serviceCalls++;
            order.add("service");
            if (rejectService) throw new AssertionError("Disabled initialization requested effect prerequisites");
            if (serviceFailure != null) throw serviceFailure;
            return service;
        }
        @Override public void contextual(IContextualManaModifierProvider provider) {
            order.add("provider");
            this.provider = provider;
            if (duringPublish != null) duringPublish.run();
            if (providerFailure) throw new IllegalStateException("injected provider fault");
        }
        @Override public void property(PropertySpec spec, IPropertyApplier applier) {
            if (appliers.size() == failAt) throw new IllegalStateException("injected registry fault");
            order.add("property:" + spec.id());
            specs.add(spec);
            appliers.put(spec.id(), applier);
            if (duringPublish != null) duringPublish.run();
        }
        @Override public void hooks(JafsIntegration module) {
            hookCalls++;
            order.add("hooks");
            if (duringPublish != null) duringPublish.run();
            if (hookFailure) throw new IllegalStateException("injected hook fault");
        }
    }
}

