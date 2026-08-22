package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DagorimFlaskServiceTest {
    private Item large;
    private Item medium;
    private Item small;

    @BeforeEach void setUp() {
        Bootstrap.register();
        large = item("large"); medium = item("medium"); small = item("small");
    }

    @Test void defaultsCheckOnlyAtFiveSecondBoundaryBelowTwentyAndAtTwentyPercent() {
        DagorimFlaskService service = service(5, 20, 20.0D);
        assertFalse(service.eligible(99, 0, 0.0D));
        assertTrue(service.eligible(100, 19, 0.199999D));
        assertFalse(service.eligible(100, 20, 0.0D));
        assertFalse(service.eligible(100, 19, 0.2D));
    }

    @Test void zeroAndHundredPercentChanceHaveExactBoundaries() {
        assertFalse(service(5, 20, 0.0D).eligible(100, 0, 0.0D));
        assertTrue(service(5, 20, 100.0D).eligible(100, 0, 0.999999D));
        assertFalse(service(5, 20, 100.0D).eligible(100, 0, 1.0D));
    }

    @Test void multipleEquippedRingsClaimOnlyOneCheckForTheWearerAtEachBoundary() {
        DagorimFlaskService service = service(5, 20, 100.0D);
        Object wearer = new Object();
        assertFalse(service.claimInterval(wearer, 99));
        assertTrue(service.claimInterval(wearer, 100));
        assertFalse(service.claimInterval(wearer, 100));
        assertTrue(service.claimInterval(wearer, 200));
        assertTrue(service.claimInterval(new Object(), 200));
    }

    @Test void priorityIsLargeThenMediumThenSmallRegardlessOfInventoryOrder() {
        DagorimFlaskService service = service(5, 20, 100.0D);
        ItemStack smallStack = new ItemStack(small, 2);
        ItemStack mediumStack = new ItemStack(medium, 2);
        ItemStack largeStack = new ItemStack(large, 2);
        List<ItemStack> inventory = Arrays.asList(smallStack, mediumStack, largeStack);

        DagorimFlaskService.Activation result = service.attempt(100, 0, 0.0D,
                inventory, requested -> requested, snapshot(10, 20, 30, 1));
        assertSame(large, result.getFlask());
        assertEquals(30, result.getRequested());
        assertEquals(1, largeStack.getCount());
        assertEquals(2, mediumStack.getCount());
        assertEquals(2, smallStack.getCount());
    }

    @Test void eachOrdinaryFlaskMapsToItsValueFromTheSameSnapshotShape() {
        DagorimFlaskService service = service(5, 20, 100.0D);
        ConfiguredFlaskSnapshot snapshot = snapshot(11, 22, 33, 4);

        assertEquals(33, service.attempt(100, 0, 0.0D,
                Arrays.asList(new ItemStack(large, 1)), value -> value, snapshot)
                .getRequested());
        assertEquals(22, service.attempt(100, 0, 0.0D,
                Arrays.asList(new ItemStack(medium, 1)), value -> value, snapshot)
                .getRequested());
        assertEquals(11, service.attempt(100, 0, 0.0D,
                Arrays.asList(new ItemStack(small, 1)), value -> value, snapshot)
                .getRequested());
    }

    @Test void currentSnapshotIsUsedAtActivationAndOnlyPositiveRestoreConsumes() {
        DagorimFlaskService service = new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 100.0D),
                large, medium, small, () -> snapshot(10, 20, 47, 1));
        ItemStack stack = new ItemStack(large, 2);
        AtomicInteger requested = new AtomicInteger();

        assertFalse(service.attempt(100, 0, 0.0D, Arrays.asList(stack), value -> 0,
                        snapshot(10, 20, 47, 1))
                .isSuccessful());
        assertEquals(2, stack.getCount());
        DagorimFlaskService.Activation result = service.attempt(200, 0, 0.0D,
                Arrays.asList(stack), value -> { requested.set(value); return 4; },
                snapshot(12, 24, 61, 2));
        assertEquals(61, requested.get());
        assertEquals(4, result.getActual());
        assertEquals(2, result.getSnapshotRevision());
        assertEquals(1, stack.getCount());
    }

    @Test void unavailableOrMissingSnapshotStopsBeforeInventoryAndConsumption() {
        AtomicReference<ConfiguredFlaskSnapshot> current =
                new AtomicReference<>(ConfiguredFlaskSnapshot.unavailable());
        DagorimFlaskService service = new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 100.0D),
                large, medium, small, current::get);
        FakeWearer wearer = new FakeWearer("unavailable", 100, 0, 0.0D,
                new ItemStack(large, 1));

        assertFalse(service.tick(wearer));
        assertEquals(0, wearer.inventoryReads);
        assertEquals(0, wearer.restoreCalls);
        assertEquals(1, wearer.flask.getCount());

        current.set(null);
        wearer.ticks = 200;
        assertFalse(service.tick(wearer));
        assertEquals(0, wearer.inventoryReads);
        assertEquals(1, wearer.flask.getCount());
    }

    @Test void eachEligibleBoundaryReadsOneCurrentSnapshotAfterCoarseGates() {
        AtomicReference<ConfiguredFlaskSnapshot> current =
                new AtomicReference<>(snapshot(10, 20, 30, 7));
        AtomicInteger snapshotReads = new AtomicInteger();
        DagorimFlaskService service = new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 100.0D),
                large, medium, small, () -> {
                    snapshotReads.incrementAndGet();
                    return current.get();
                });
        FakeWearer wearer = new FakeWearer("revision", 99, 0, 0.0D,
                new ItemStack(large, 2));

        assertFalse(service.tick(wearer));
        assertEquals(0, snapshotReads.get());
        assertEquals(0, wearer.inventoryReads);

        wearer.ticks = 100;
        assertTrue(service.tick(wearer));
        assertEquals(1, snapshotReads.get());
        assertEquals(Arrays.asList(30), wearer.requestedRestorations);

        current.set(snapshot(12, 24, 61, 8));
        wearer.ticks = 200;
        assertTrue(service.tick(wearer));
        assertEquals(2, snapshotReads.get());
        assertEquals(Arrays.asList(30, 61), wearer.requestedRestorations);
        assertTrue(service.latestActivationSummary().contains("flaskRevision=8"));
    }

    @Test void transactionReadsAndMutatesOnlyTheSuppliedWearer() {
        DagorimFlaskService service = service(5, 20, 100.0D);
        FakeWearer selected = new FakeWearer("selected", 100, 0, 0.0D,
                new ItemStack(large, 1));
        FakeWearer other = new FakeWearer("other", 100, 0, 0.0D,
                new ItemStack(large, 1));

        assertTrue(service.tick(selected));
        assertEquals(0, selected.flask.getCount());
        assertEquals(1, selected.restoreCalls);
        assertEquals(1, selected.dirtyCalls);
        assertEquals(1, other.flask.getCount());
        assertEquals(0, other.restoreCalls);
        assertEquals(0, other.dirtyCalls);
    }

    @Test void rejectedChecksNeverRequestSnapshotOrWearerInventory() {
        AtomicInteger snapshotReads = new AtomicInteger();
        DagorimFlaskService service = new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 20.0D),
                large, medium, small, () -> {
                    snapshotReads.incrementAndGet();
                    return snapshot(10, 20, 30, 1);
                });
        FakeWearer offCadence = new FakeWearer("off", 99, 0, 0.0D,
                new ItemStack(large, 1));
        FakeWearer aboveThreshold = new FakeWearer("full", 100, 20, 0.0D,
                new ItemStack(large, 1));
        FakeWearer chanceFailed = new FakeWearer("chance", 100, 0, 0.2D,
                new ItemStack(large, 1));
        assertFalse(service.tick(offCadence));
        assertFalse(service.tick(aboveThreshold));
        assertFalse(service.tick(chanceFailed));
        assertEquals(0, snapshotReads.get());
        assertEquals(0, offCadence.inventoryReads);
        assertEquals(0, aboveThreshold.inventoryReads);
        assertEquals(0, chanceFailed.inventoryReads);
    }

    private DagorimFlaskService service(int interval, int threshold, double chance) {
        return new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(
                        true, interval, threshold, chance),
                large, medium, small, () -> snapshot(10, 20, 30, 1));
    }

    private static ConfiguredFlaskSnapshot snapshot(int small, int medium, int large,
                                                     long revision) {
        return ConfiguredFlaskSnapshot.available(small, medium, large, revision);
    }

    private static Item item(String name) {
        return new Item().setRegistryName("test", name);
    }

    private static final class FakeWearer implements DagorimFlaskService.WearerAccess {
        private final String id;
        private int ticks;
        private final int mana;
        private final double random;
        private final ItemStack flask;
        private int inventoryReads;
        private int restoreCalls;
        private int dirtyCalls;
        private final List<Integer> requestedRestorations = new java.util.ArrayList<>();
        private FakeWearer(String id, int ticks, int mana, double random, ItemStack flask) {
            this.id = id; this.ticks = ticks; this.mana = mana;
            this.random = random; this.flask = flask;
        }
        @Override public Object key() { return this; }
        @Override public int ticksExisted() { return ticks; }
        @Override public int currentMana() { return mana; }
        @Override public double randomValue() { return random; }
        @Override public List<ItemStack> inventory() {
            inventoryReads++;
            return Arrays.asList(flask);
        }
        @Override public int restore(int requested) {
            restoreCalls++;
            requestedRestorations.add(requested);
            return requested;
        }
        @Override public void markDirty() { dirtyCalls++; }
        @Override public String playerId() { return id; }
    }
}
