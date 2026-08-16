package com.mahghuuuls.jawmsintegrations.client;

import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityToolsTooltipAdapterTest {

    @Test
    void reductionDisplaysAsBlueNegativeDelayChange() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.BLUE + " +1.25 s Post-Cast Mana Regen Delay"));

        QualityToolsTooltipAdapter.invertDelaySigns(
                tooltip, "s Post-Cast Mana Regen Delay");

        assertEquals(TextFormatting.BLUE + " -1.25 s Post-Cast Mana Regen Delay", tooltip.get(0));
    }

    @Test
    void penaltyDisplaysAsRedPositiveDelayChange() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.RED + " -30% Post-Cast Mana Regen Delay"));

        QualityToolsTooltipAdapter.invertDelaySigns(
                tooltip, "Post-Cast Mana Regen Delay");

        assertEquals(TextFormatting.RED + " +30% Post-Cast Mana Regen Delay", tooltip.get(0));
    }

    @Test
    void unrelatedTooltipLinesRemainUntouched() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.BLUE + " +10 % Maximum Mana"));

        QualityToolsTooltipAdapter.invertDelaySigns(
                tooltip, "Post-Cast Mana Regen Delay");

        assertEquals(TextFormatting.BLUE + " +10 % Maximum Mana", tooltip.get(0));
    }

    @Test
    void operationZeroPercentageIsAttachedToTheNumber() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.BLUE + " +20 Mana Regeneration"));

        QualityToolsTooltipAdapter.compactPercentage(tooltip, "Mana Regeneration");

        assertEquals(TextFormatting.BLUE + " +20% Mana Regeneration", tooltip.get(0));
    }

    @Test
    void percentageAlreadyAddedByQualityToolsIsNotDuplicated() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.BLUE + " +5000% Maximum Mana"));

        QualityToolsTooltipAdapter.compactPercentage(tooltip, "Maximum Mana");

        assertEquals(TextFormatting.BLUE + " +5000% Maximum Mana", tooltip.get(0));
    }

    @Test
    void percentageSuffixDoesNotClaimFlatSecondsLine() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                TextFormatting.BLUE + " -1.25 s Post-Cast Mana Regen Delay"));

        QualityToolsTooltipAdapter.compactPercentage(
                tooltip, "Post-Cast Mana Regen Delay");

        assertEquals(TextFormatting.BLUE + " -1.25 s Post-Cast Mana Regen Delay", tooltip.get(0));
    }
}
