package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusworksProjectionTest {
    private static final String MAX_FLAT = "jawmsintegrations:max_mana_flat";
    private static final String MAX_PERCENT = "jawmsintegrations:max_mana_percent";

    @Test
    void queriesAndProjectsEveryNativeTypeInCatalogOrder() {
        Map<String, Double> amounts = new HashMap<>();
        amounts.put(MAX_FLAT, 20.0D);
        amounts.put(MAX_PERCENT, -10.0D);
        amounts.put("jawmsintegrations:mana_regen_flat", 2.5D);
        amounts.put("jawmsintegrations:mana_regen_percent", 25.0D);
        amounts.put("jawmsintegrations:mana_regen_delay_flat", -0.75D);
        amounts.put("jawmsintegrations:mana_regen_delay_percent", -5.0D);
        amounts.put("jawmsintegrations:spell_efficiency", 3.0D);
        Element[] schools = {Element.MAGIC, Element.FIRE, Element.ICE, Element.LIGHTNING, Element.NECROMANCY,
                Element.EARTH, Element.SORCERY, Element.HEALING};
        String[] names = {"magic", "fire", "ice", "lightning", "necromancy", "earth", "sorcery", "healing"};
        for (int index = 0; index < names.length; index++) {
            amounts.put("jawmsintegrations:spell_efficiency_" + names[index], (double) index + 1);
        }
        List<String> visited = new ArrayList<>();
        StatusworksProjection.Read read = StatusworksProjection.read(id -> {
            visited.add(id);
            return ContributionGroup.available(Collections.singletonMap("status:example:mana#0", amounts.get(id)));
        });
        assertEquals(StatusworksAuthoring.typeIds(), visited);
        assertEquals(15, visited.size());
        assertNull(read.failure);
        assertTrue(read.problems.isEmpty());
        ManaContribution contribution = read.contribution;
        assertEquals(20, contribution.getFlatMaximumMana());
        assertEquals(10.0D, contribution.getMaximumManaReduction());
        assertEquals(2.5D, contribution.getFlatRegeneration());
        assertEquals(25.0D, contribution.getRegenerationIncrease());
        assertEquals(-0.75D, contribution.getFlatLockoutSeconds());
        assertEquals(5.0D, contribution.getLockoutReduction());
        assertEquals(3.0D, contribution.getGlobalSpellEfficiency());
        for (int index = 0; index < schools.length; index++) {
            assertEquals(index + 1.0D, contribution.getElementSpellEfficiency(schools[index]));
        }
    }

    @Test
    void rejectsIndividualFractionsAndInvalidAggregateWhileKeepingOtherSources() {
        Map<String, Double> flat = new LinkedHashMap<>();
        flat.put("pool:example:valid#2", 20.0D);
        flat.put("pool:example:fraction#0", 0.5D);
        flat.put("pool:example:fraction#1", 0.5D);
        flat.put("mark:example:ignored#0@source:00000000-0000-0000-0000-000000000001", 45.0D);
        Map<String, Double> percent = new LinkedHashMap<>();
        percent.put("status:example:reduction#0", -60.0D);
        percent.put("status:example:reduction#1", -60.0D);
        StatusworksProjection.Read read = StatusworksProjection.read(id ->
                ContributionGroup.available(id.equals(MAX_FLAT) ? flat : id.equals(MAX_PERCENT)
                        ? percent : Collections.emptyMap()));
        assertNull(read.failure);
        assertEquals(20, read.contribution.getFlatMaximumMana());
        assertEquals(0.0D, read.contribution.getMaximumManaReduction());
        assertEquals(3, read.problems.size());
        assertTrue(read.problems.get(0).contains("fraction#0 FRACTIONAL_MAXIMUM"));
        assertTrue(read.problems.get(1).contains("fraction#1 FRACTIONAL_MAXIMUM"));
        assertTrue(read.problems.get(2).contains("aggregate BELOW_MINIMUM"));
    }

    @Test
    void acceptsNativeDefinitionIdsVerbatimAndStillIgnoresMarks() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("status:ManaBoost#0", 10.0D);
        values.put("pool:MixedCase#part@fill#1", 20.0D);
        values.put("mark:UnnamespacedMark#2@target:00000000-0000-0000-0000-000000000001", 40.0D);
        StatusworksProjection.Read read = StatusworksProjection.read(id ->
                ContributionGroup.available(id.equals(MAX_FLAT) ? values : Collections.emptyMap()));
        assertNull(read.failure);
        assertEquals(30, read.contribution.getFlatMaximumMana());
    }

    @Test
    void unknownOriginsAndNonCurrentGroupsNeverProduceVerifiedZero() {
        for (String key : new String[]{"cooldown:example:cast#0", "status:#0", "pool:spaces here#0", "status:id#x",
                "mark:example:wrong#0@other:00000000-0000-0000-0000-000000000001"}) {
            StatusworksProjection.Read read = StatusworksProjection.read(id ->
                    ContributionGroup.available(Collections.singletonMap(key, 0.0D)));
            assertTrue(read.failure.startsWith("UNKNOWN_ORIGIN"));
            assertNull(read.contribution);
        }
        for (GroupAvailability availability : GroupAvailability.values()) {
            if (availability == GroupAvailability.AVAILABLE) continue;
            StatusworksProjection.Read read = StatusworksProjection.read(id -> id.equals(MAX_PERCENT)
                    ? ContributionGroup.of(availability)
                    : ContributionGroup.available(Collections.singletonMap("status:example:mana#0", 20.0D)));
            assertEquals(availability + " " + MAX_PERCENT, read.failure);
            assertNull(read.contribution);
        }
        assertTrue(empty().contribution.isEmpty());
        assertFalse(StatusworksProjection.same(empty().contribution,
                ManaContribution.builder().flatMaximumMana(1).build()));
    }

    static StatusworksProjection.Read empty() {
        return StatusworksProjection.read(id -> ContributionGroup.available(Collections.emptyMap()));
    }
}
