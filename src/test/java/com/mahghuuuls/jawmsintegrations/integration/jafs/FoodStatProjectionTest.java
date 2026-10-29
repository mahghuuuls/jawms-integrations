package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static com.mahghuuuls.jawmsintegrations.integration.jafs.FoodStatProjection.Stat.*;
import static org.junit.jupiter.api.Assertions.*;

final class FoodStatProjectionTest {
    @Test
    void projectsEverySupportedComponentWithPercentagePointsAndSignedSeconds() {
        Map<FoodStatProjection.Stat, FoodStatProjection.Totals> values =
                new EnumMap<>(FoodStatProjection.Stat.class);
        values.put(MAX_MANA, new FoodStatProjection.Totals(8, 10));
        values.put(MANA_REGEN, new FoodStatProjection.Totals(-1.5, -20));
        values.put(MANA_REGEN_DELAY, new FoodStatProjection.Totals(-1.25, -30));
        values.put(SPELL_EFFICIENCY, new FoodStatProjection.Totals(25, 0));
        FoodStatProjection.Stat[] schools = {MAGIC, FIRE, ICE, LIGHTNING, NECROMANCY, EARTH, SORCERY, HEALING};
        Element[] elements = {Element.MAGIC, Element.FIRE, Element.ICE, Element.LIGHTNING,
                Element.NECROMANCY, Element.EARTH, Element.SORCERY, Element.HEALING};
        for (int i = 0; i < schools.length; i++) {
            values.put(schools[i], new FoodStatProjection.Totals(i + 1, 0));
        }
        FoodStatProjection.Result result = project(values);
        ManaContribution contribution = result.getContribution();
        assertTrue(result.getProblems().isEmpty());
        assertEquals(8, contribution.getFlatMaximumMana());
        assertEquals(10, contribution.getMaximumManaIncrease());
        assertEquals(-1.5, contribution.getFlatRegeneration());
        assertEquals(20, contribution.getRegenerationReduction());
        assertEquals(-1.25, contribution.getFlatLockoutSeconds());
        assertEquals(30, contribution.getLockoutReduction());
        assertEquals(25, contribution.getGlobalSpellEfficiency());
        for (int i = 0; i < elements.length; i++) {
            assertEquals(i + 1, contribution.getElementSpellEfficiency(elements[i]), elements[i].name());
        }
        assertEquals(12, result.getSourceTotals().size());
    }

    @Test
    void positiveDelayLengthensAndNegativeMaximumIsAReduction() {
        assertEquals(10, one(MAX_MANA, -8, -10).getContribution().getMaximumManaReduction());
        assertEquals(-8, one(MAX_MANA, -8, -10).getContribution().getFlatMaximumMana());
        ManaContribution delay = one(MANA_REGEN_DELAY, 1.25, 10).getContribution();
        assertEquals(1.25, delay.getFlatLockoutSeconds());
        assertEquals(10, delay.getLockoutIncrease());
        assertEquals(0, delay.getLockoutReduction());
        ManaContribution regeneration = one(MANA_REGEN, 2.5, 20).getContribution();
        assertEquals(2.5, regeneration.getFlatRegeneration());
        assertEquals(20, regeneration.getRegenerationIncrease());
        assertEquals(0, regeneration.getRegenerationReduction());
    }

    @Test
    void validatesCombinedFlatMaximumWithoutTruncationOrLastGoodFallback() {
        assertEquals(1, one(MAX_MANA, 1.0, 10).getContribution().getFlatMaximumMana());
        FoodStatProjection.Result fractional = one(MAX_MANA, 0.5, 10);
        assertEquals(0, fractional.getContribution().getFlatMaximumMana());
        assertEquals(10, fractional.getContribution().getMaximumManaIncrease());
        assertEquals(FoodStatProjection.Reason.FRACTIONAL_MAXIMUM, fractional.getProblems().get(0).getReason());
        assertEquals(1, one(MAX_MANA, 1.0, 10).getContribution().getFlatMaximumMana());
        assertEquals(Integer.MAX_VALUE,
                one(MAX_MANA, Integer.MAX_VALUE, 0).getContribution().getFlatMaximumMana());
        assertEquals(Integer.MIN_VALUE,
                one(MAX_MANA, Integer.MIN_VALUE, 0).getContribution().getFlatMaximumMana());
        assertEquals(0, one(MAX_MANA, (double) Integer.MAX_VALUE + 1, 10)
                .getContribution().getFlatMaximumMana());
    }

    @Test
    void invalidAggregateFieldsLeaveIndependentFieldsIntact() {
        Map<FoodStatProjection.Stat, FoodStatProjection.Totals> values =
                new EnumMap<>(FoodStatProjection.Stat.class);
        values.put(MAX_MANA, new FoodStatProjection.Totals(Double.POSITIVE_INFINITY, 10));
        values.put(MANA_REGEN, new FoodStatProjection.Totals(2.5, -101));
        values.put(SPELL_EFFICIENCY, new FoodStatProjection.Totals(7, 0));
        FoodStatProjection.Result result = project(values);
        assertEquals(2, result.getProblems().size());
        assertEquals(0, result.getContribution().getFlatMaximumMana());
        assertEquals(10, result.getContribution().getMaximumManaIncrease());
        assertEquals(2.5, result.getContribution().getFlatRegeneration());
        assertEquals(0, result.getContribution().getRegenerationReduction());
        assertEquals(7, result.getContribution().getGlobalSpellEfficiency());
    }

    @Test
    void rejectsNonFiniteValuesUnsupportedEfficiencyAndExcessReductionButAcceptsZero() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            FoodStatProjection.Result result = one(MANA_REGEN_DELAY, invalid, invalid);
            assertTrue(result.getContribution().isEmpty());
            assertEquals(2, result.getProblems().size());
            assertEquals(FoodStatProjection.Reason.NON_FINITE, result.getProblems().get(0).getReason());
        }
        assertTrue(one(SPELL_EFFICIENCY, -1, 25).getContribution().isEmpty());
        assertEquals(2, one(SPELL_EFFICIENCY, -1, 25).getProblems().size());
        assertEquals(100, one(MANA_REGEN_DELAY, 0, -100).getContribution().getLockoutReduction());
        assertTrue(one(MANA_REGEN_DELAY, 0, -100.01).getContribution().isEmpty());
        FoodStatProjection.Result zero = project(new EnumMap<>(FoodStatProjection.Stat.class));
        assertSame(ManaContribution.EMPTY, zero.getContribution());
        assertTrue(zero.getProblems().isEmpty());
    }

    private static FoodStatProjection.Result one(FoodStatProjection.Stat stat, double flat, double percent) {
        return FoodStatProjection.project(current -> current == stat
                ? new FoodStatProjection.Totals(flat, percent) : FoodStatProjection.Totals.ZERO);
    }

    private static FoodStatProjection.Result project(
            Map<FoodStatProjection.Stat, FoodStatProjection.Totals> values) {
        return FoodStatProjection.project(stat -> values.getOrDefault(stat, FoodStatProjection.Totals.ZERO));
    }
}

