package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DagorimFlaskServiceTest {
    private Item large;
    private Item medium;
    private Item small;

    @BeforeEach void setUp() {
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
                inventory, requested -> requested);
        assertSame(large, result.getFlask());
        assertEquals(30, result.getRequested());
        assertEquals(1, largeStack.getCount());
        assertEquals(2, mediumStack.getCount());
        assertEquals(2, smallStack.getCount());
    }

    @Test void currentCapacityIsLookedUpAtActivationAndOnlyPositiveRestoreConsumes() {
        AtomicInteger largeCapacity = new AtomicInteger(47);
        DagorimFlaskService service = new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 100.0D),
                large, medium, small, stack -> stack.getItem() == large
                        ? largeCapacity.get() : stack.getItem() == medium ? 20 : 10);
        ItemStack stack = new ItemStack(large, 2);
        AtomicInteger requested = new AtomicInteger();

        assertFalse(service.attempt(100, 0, 0.0D, Arrays.asList(stack), value -> 0)
                .isSuccessful());
        assertEquals(2, stack.getCount());
        largeCapacity.set(61);
        DagorimFlaskService.Activation result = service.attempt(200, 0, 0.0D,
                Arrays.asList(stack), value -> { requested.set(value); return 4; });
        assertEquals(61, requested.get());
        assertEquals(4, result.getActual());
        assertEquals(1, stack.getCount());
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

    @Test void rejectedChecksNeverRequestWearerInventory() {
        DagorimFlaskService service = service(5, 20, 20.0D);
        FakeWearer offCadence = new FakeWearer("off", 99, 0, 0.0D,
                new ItemStack(large, 1));
        FakeWearer aboveThreshold = new FakeWearer("full", 100, 20, 0.0D,
                new ItemStack(large, 1));
        FakeWearer chanceFailed = new FakeWearer("chance", 100, 0, 0.2D,
                new ItemStack(large, 1));
        assertFalse(service.tick(offCadence));
        assertFalse(service.tick(aboveThreshold));
        assertFalse(service.tick(chanceFailed));
        assertEquals(0, offCadence.inventoryReads);
        assertEquals(0, aboveThreshold.inventoryReads);
        assertEquals(0, chanceFailed.inventoryReads);
    }

    private DagorimFlaskService service(int interval, int threshold, double chance) {
        return new DagorimFlaskService(true,
                new IntegrationConfigSnapshot.RingOfDagorimConfig(
                        true, interval, threshold, chance),
                large, medium, small,
                stack -> stack.getItem() == large ? 30 : stack.getItem() == medium ? 20 : 10);
    }

    private static Item item(String name) {
        return new Item().setRegistryName("test", name);
    }

    private static final class FakeWearer implements DagorimFlaskService.WearerAccess {
        private final String id;
        private final int ticks;
        private final int mana;
        private final double random;
        private final ItemStack flask;
        private int inventoryReads;
        private int restoreCalls;
        private int dirtyCalls;
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
        @Override public int restore(int requested) { restoreCalls++; return requested; }
        @Override public void markDirty() { dirtyCalls++; }
        @Override public String playerId() { return id; }
    }
}
