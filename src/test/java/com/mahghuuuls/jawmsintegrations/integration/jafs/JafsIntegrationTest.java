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
        JafsIntegration module = JafsIntegration.install(registration, ignored -> dirty.incrementAndGet(),
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
    void partialRegistrationLeavesAnInertProviderAndCallbacks() {
        RecordingFoods service = new RecordingFoods();
        RecordingRegistration registration = new RecordingRegistration(service);
        registration.failAt = 4;
        AtomicInteger dirty = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> JafsIntegration.install(registration,
                ignored -> dirty.incrementAndGet(), ignored -> {}, ignored -> {}));
        assertNotNull(registration.provider);
        EntityPlayerMP player = player();
        assertSame(ManaContribution.EMPTY, registration.provider.getContribution(player));
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
        JafsIntegration module = JafsIntegration.install(new RecordingRegistration(service),
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
        JafsIntegration module = JafsIntegration.install(registration, ignored -> dirty.incrementAndGet(),
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
        JafsIntegration module = JafsIntegration.install(new RecordingRegistration(service),
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
        JafsIntegration module = JafsIntegration.install(new RecordingRegistration(service),
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
        JafsIntegration module = JafsIntegration.install(new RecordingRegistration(service),
                ignored -> {}, ignored -> {}, ignored -> {});
        assertEquals(8, module.getContribution(first).getFlatMaximumMana());
        assertEquals(12, module.getContribution(second).getFlatMaximumMana());
        assertEquals(8, module.getContribution(first).getFlatMaximumMana());
    }

    @Test
    void readinessRequiresBothOpenRegistriesAndTheMinimumApiWithoutAnUpperBound() {
        assertDoesNotThrow(() -> JafsIntegration.requireRegistration("1.0", true, false, false));
        assertDoesNotThrow(() -> JafsIntegration.requireRegistration("2.3", true, false, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireRegistration("0.9", true, false, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireRegistration("unknown", true, false, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireRegistration("1.0", false, false, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireRegistration("1.0", true, true, false));
        assertThrows(IllegalStateException.class,
                () -> JafsIntegration.requireRegistration("1.0", true, false, true));
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

        private RecordingRegistration(IFoodService service) {
            this.service = service;
        }

        @Override public IFoodService validatedService() { return service; }
        @Override public void contextual(IContextualManaModifierProvider provider) { this.provider = provider; }
        @Override public void property(PropertySpec spec, IPropertyApplier applier) {
            if (appliers.size() == failAt) throw new IllegalStateException("injected registry fault");
            appliers.put(spec.id(), applier);
        }
    }
}

