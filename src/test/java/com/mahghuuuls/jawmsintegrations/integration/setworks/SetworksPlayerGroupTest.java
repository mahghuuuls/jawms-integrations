package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

final class SetworksPlayerGroupTest {
    @Test
    void invalidationSuspendsRecordedEmptyImmediatelyAndRecoveryUsesSuspensionReceipt() throws Exception {
        FakeSlot slot = new FakeSlot();
        SetworksPlayerGroup group = new SetworksPlayerGroup(slot, ignored -> { });
        ManaProfileGeneration token = token();
        group.bind(token);
        group.finish(true);
        group.publish(token);
        assertSame(ManaContribution.EMPTY, slot.group);

        group.apply("active", maximum("invalid"));
        assertEquals(Arrays.asList("initial:0", "suspend:1"), slot.calls);
        assertNull(slot.group);
        group.finish(false);
        group.publish(token);
        group.finish(true);
        group.publish(token);
        assertEquals(2, slot.calls.size());
        assertTrue(group.inspect(ManaProfileReadiness.UNAVAILABLE, null).getDetail().contains("suspended"));

        group.apply("active", maximum("20"));
        group.finish(false);
        group.publish(token);
        assertEquals(Arrays.asList("initial:0", "suspend:1", "update:2"), slot.calls);
        assertEquals(20, slot.group.getFlatMaximumMana());
        assertSame(slot.group, group.inspect(ManaProfileReadiness.READY, slot.group).getApplied());
        ManaContribution equivalent = ManaContribution.builder().flatMaximumMana(20).build();
        assertEquals("applied contribution", group.inspect(ManaProfileReadiness.READY, equivalent).getDetail());
    }

    @Test
    void callbackFreeLifecycleCarriesOnlyVerifiedState() throws Exception {
        FakeSlot slot = new FakeSlot();
        List<String> warnings = new ArrayList<>();
        SetworksPlayerGroup group = new SetworksPlayerGroup(slot, warnings::add);
        ManaProfileGeneration token = token();
        group.bind(token);
        group.apply("broken", maximum("no number"));
        group.finish(false);
        group.finish(true);
        group.publish(token);
        assertTrue(slot.calls.isEmpty());
        assertEquals(1, warnings.size());
        group.apply("broken", maximum("another invalid value"));
        group.finish(false);
        group.publish(token);
        assertTrue(slot.calls.isEmpty());
        assertEquals(1, warnings.size());
        group.withdraw("broken");
        group.apply("healthy", maximum("20"));
        group.finish(false);
        group.publish(token);
        assertEquals(20, slot.group.getFlatMaximumMana());

        ManaProfileGeneration transferred = token();
        group.bind(transferred);
        group.finish(true);
        group.publish(transferred);
        assertEquals(Arrays.asList("initial:0", "initial:0"), slot.calls);
        assertEquals(20, slot.group.getFlatMaximumMana());
        group.publish(token);
        assertEquals(2, slot.calls.size());
    }

    @Test
    void completedAggregateOmitsOnlyOverflowAndRetainsIndependentStats() throws Exception {
        FakeSlot slot = new FakeSlot();
        SetworksPlayerGroup group = new SetworksPlayerGroup(slot, ignored -> { });
        ManaProfileGeneration token = token();
        group.bind(token);
        group.apply("first", maximum("20"));
        group.apply("third", params("mana_regen", "flat", "2.25"));
        group.finish(false);
        group.publish(token);
        assertEquals(20, slot.group.getFlatMaximumMana());
        slot.phase = ManaProfileReadiness.READY;
        group.apply("first", maximum("2147483647"));
        group.apply("second", maximum("20"));
        group.finish(false);
        group.publish(token);
        assertEquals(0, slot.group.getFlatMaximumMana());
        assertEquals(2.25, slot.group.getFlatRegeneration());
        assertEquals(Arrays.asList("initial:0", "update:1"), slot.calls);
        assertTrue(group.inspect(ManaProfileReadiness.READY, slot.group).getDetail()
                .contains("max_mana flat MAXIMUM_OUT_OF_RANGE"));
    }

    @Test
    void allAuthoredEntriesRouteToDistinctFieldsAndPercentInstancesWithdrawIndependently() throws Exception {
        FakeSlot slot = new FakeSlot();
        SetworksPlayerGroup group = new SetworksPlayerGroup(slot, ignored -> { });
        ManaProfileGeneration token = token();
        group.bind(token);
        group.apply("maximum", maximum("20"));
        group.apply("maximum-pct-a", params("max_mana", "percent", "10"));
        group.apply("maximum-pct-b", params("max_mana", "percent", "10"));
        group.apply("regeneration", params("mana_regen", "flat", "3.25"));
        group.apply("regeneration-pct", params("mana_regen", "percent", "-12"));
        group.apply("delay", params("mana_regen_delay", "flat", "-4.25"));
        group.apply("delay-pct", params("mana_regen_delay", "percent", "13"));
        group.apply("efficiency", params("spell_efficiency", "flat", "14"));
        Element[] schools = {Element.MAGIC, Element.FIRE, Element.ICE, Element.LIGHTNING,
                Element.NECROMANCY, Element.EARTH, Element.SORCERY, Element.HEALING};
        String[] names = {"magic", "fire", "ice", "lightning", "necromancy", "earth", "sorcery", "healing"};
        for (int i = 0; i < schools.length; i++) {
            group.apply(names[i], params("spell_efficiency_" + names[i], "flat", Integer.toString(15 + i)));
        }
        group.finish(false);
        group.publish(token);
        ManaContribution all = slot.group;
        assertEquals(20, all.getFlatMaximumMana());
        assertEquals(20.0, all.getMaximumManaIncrease());
        assertEquals(3.25, all.getFlatRegeneration());
        assertEquals(12.0, all.getRegenerationReduction());
        assertEquals(-4.25, all.getFlatLockoutSeconds());
        assertEquals(13.0, all.getLockoutIncrease());
        assertEquals(14.0, all.getGlobalSpellEfficiency());
        for (int i = 0; i < schools.length; i++) {
            assertEquals(15.0 + i, all.getElementSpellEfficiency(schools[i]), names[i]);
        }
        slot.phase = ManaProfileReadiness.READY;
        group.withdraw("maximum-pct-a");
        group.finish(false);
        group.publish(token);
        assertEquals(10.0, slot.group.getMaximumManaIncrease());
        assertEquals(20, slot.group.getFlatMaximumMana());
        assertEquals(22.0, slot.group.getElementSpellEfficiency(Element.HEALING));
    }

    @Test
    void busyAndReentrantUpdatesKeepLatestGroupAndUseAcknowledgedRevision() throws Exception {
        FakeSlot slot = new FakeSlot();
        SetworksPlayerGroup group = new SetworksPlayerGroup(slot, ignored -> { });
        ManaProfileGeneration token = token();
        group.bind(token);
        group.finish(true);
        group.publish(token);
        slot.phase = ManaProfileReadiness.READY;
        group.apply("active", maximum("20"));
        group.finish(false);
        slot.busy = true;
        group.publish(token);
        slot.busy = false;
        slot.onUpdate = () -> {
            group.apply("active", maximum("30"));
            group.finish(false);
            group.publish(token);
        };
        group.publish(token);
        slot.onUpdate = null;
        group.publish(token);
        assertEquals(Arrays.asList("initial:0", "update:1", "update:1", "update:2"), slot.calls);
        assertEquals(30, slot.group.getFlatMaximumMana());

        group.apply("active", maximum("malformed"));
        group.finish(false);
        assertTrue(group.inspect(ManaProfileReadiness.READY, slot.group).getDetail().contains("stale"));
        assertEquals(30, group.inspect(ManaProfileReadiness.READY, slot.group).getApplied().getFlatMaximumMana());
        group.withdraw("active");
        group.finish(false);
        group.publish(token);
        assertSame(ManaContribution.EMPTY, slot.group);
    }

    @Test
    void missingTransferTokenDoesNotDiscardSourceReceiptOrTouchOtherPlayer() throws Exception {
        FakeSlot first = new FakeSlot();
        FakeSlot second = new FakeSlot();
        SetworksPlayerGroup group = new SetworksPlayerGroup(first, ignored -> { });
        SetworksPlayerGroup peer = new SetworksPlayerGroup(second, ignored -> { });
        ManaProfileGeneration source = token();
        ManaProfileGeneration independent = token();
        group.bind(source);
        peer.bind(independent);
        group.finish(true);
        peer.apply("shared-instance-id", maximum("30"));
        peer.finish(false);
        group.publish(source);
        peer.publish(independent);

        group.bind(null);
        group.apply("shared-instance-id", maximum("20"));
        group.finish(false);
        group.publish(null);
        assertEquals(Arrays.asList("initial:0", "suspend:1"), first.calls);
        assertEquals(Arrays.asList("initial:0"), second.calls);
        assertEquals(30, second.group.getFlatMaximumMana());
        group.bind(source);
        group.publish(source);
        assertEquals(Arrays.asList("initial:0", "suspend:1", "update:2"), first.calls);
        assertEquals(20, first.group.getFlatMaximumMana());
    }

    private static ManaProfileGeneration token() throws Exception {
        Constructor<ManaProfileGeneration> ctor = ManaProfileGeneration.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Map<String, String> maximum(String amount) {
        return params("max_mana", "flat", amount);
    }

    private static Map<String, String> params(String stat, String operation, String amount) {
        Map<String, String> result = new HashMap<>();
        result.put("stat", stat);
        result.put("operation", operation);
        result.put("amount", amount);
        return result;
    }

    private static final class FakeSlot implements SetworksPlayerGroup.Slot {
        final List<String> calls = new ArrayList<>();
        ManaProfileReadiness phase = ManaProfileReadiness.PENDING;
        ManaContribution group;
        boolean busy;
        Runnable onUpdate;

        @Override public ManaProfileReadiness readiness() { return phase; }

        @Override public ManaContributionPublicationResult complete(ManaProfileGeneration token, ManaContribution group) {
            calls.add("initial:0");
            this.group = group;
            return receipt(INITIAL_RECORDED, 1);
        }

        @Override public ManaContributionPublicationResult suspend(ManaProfileGeneration token, long revision) {
            calls.add("suspend:" + revision);
            this.group = null;
            return receipt(SUSPENDED, revision + 1);
        }

        @Override public ManaContributionPublicationResult update(ManaProfileGeneration token, long revision,
                                                                  ManaContribution group) {
            calls.add("update:" + revision);
            if (busy) return new ManaContributionPublicationResult(BUSY, OptionalLong.empty());
            this.group = group;
            if (onUpdate != null) onUpdate.run();
            return receipt(UPDATE_RECORDED, revision + 1);
        }

        private ManaContributionPublicationResult receipt(ManaContributionPublicationStatus status, long revision) {
            return new ManaContributionPublicationResult(status, OptionalLong.of(revision));
        }
    }
}
