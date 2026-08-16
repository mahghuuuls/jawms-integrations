package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EverfullManaServiceTest {

    private EverfullManaService service;
    private EverfullManaState state;

    @BeforeEach
    void setUp() {
        IntegrationConfigSnapshot.AncientSpellcraftConfig config =
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(true);
        AncientReplacementPolicy.install(new AncientReplacementPolicy(true, config));
        service = new EverfullManaService(true,
                new IntegrationConfigSnapshot.EverfullManaFlaskConfig(true, 3, 12, 10));
        state = new EverfullManaState();
    }

    @AfterEach
    void tearDown() {
        AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
        EverfullManaService.install(EverfullManaService.disabled());
    }

    @Test
    void cadenceImportsImmediatelyAndRegeneratesOnlyAtConfiguredBoundary() {
        ItemStack stack = everfull();

        assertTrue(service.tick(stack, 700, 239));
        assertEquals(50, state.peek(stack));
        assertFalse(service.tick(stack, 700, 239));
        assertTrue(service.tick(stack, 700, 240));
        assertEquals(53, state.peek(stack));
        assertFalse(service.tick(stack, 700, 241));
        assertEquals(53, state.peek(stack));
    }

    @Test
    void regenerationClampsAtFixedCapacity() {
        ItemStack stack = everfull();
        state.getOrImport(stack, 1400);
        state.set(stack, 99);

        assertTrue(service.tick(stack, 1400, 240));
        assertEquals(100, state.peek(stack));
        assertFalse(service.tick(stack, 1400, 480));
        assertEquals(100, state.peek(stack));
    }

    @Test
    void transferUsesMinimumAndConsumesOnlyReportedActualRestoration() {
        ItemStack stack = everfull();
        state.getOrImport(stack, 700);
        AtomicInteger requested = new AtomicInteger();

        int actual = service.transfer(stack, 700, 96, 100, value -> {
            requested.set(value);
            return 3;
        });

        assertEquals(4, requested.get());
        assertEquals(3, actual);
        assertEquals(47, state.peek(stack));
    }

    @Test
    void failedOrNoopRestorationNeverSpendsCharge() {
        ItemStack stack = everfull();
        state.getOrImport(stack, 140);
        AtomicInteger calls = new AtomicInteger();

        assertEquals(0, service.transfer(stack, 140, 50, 100, requested -> {
            calls.incrementAndGet();
            return 0;
        }));
        assertEquals(10, state.peek(stack));
        assertEquals(0, service.transfer(stack, 140, 100, 100, requested -> {
            calls.incrementAndGet();
            return requested;
        }));
        assertEquals(1, calls.get());
        assertEquals(10, state.peek(stack));
    }

    @Test
    void exactRegistryTargetAndIndependentDisableFailClosed() {
        ItemStack wrong = new ItemStack(new Item().setRegistryName("ancientspellcraft", "other"));
        assertFalse(service.handles(wrong));
        assertEquals(0, service.transfer(wrong, 1400, 0, 100, requested -> requested));

        EverfullManaService disabled = new EverfullManaService(true,
                new IntegrationConfigSnapshot.EverfullManaFlaskConfig(false, 1, 12, 10));
        assertFalse(disabled.handles(everfull()));
    }

    private static ItemStack everfull() {
        Item item = new Item();
        item.setRegistryName(AncientReplacement.EVERFULL_MANA_FLASK.getRegistryName());
        return new ItemStack(item);
    }
}
