package com.mahghuuuls.jawmsintegrations.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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

    @Test
    void qualityBlockMovesBelowLaterJawmsItemStatsAsOneUnit() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                "Ice Battlemage Leggings",
                "When on legs:",
                " +6 Armor",
                "",
                "Quality: Efficient Casting",
                "When on legs:",
                " +5 Spell Efficiency",
                "+10 Mana",
                "+0.4 Mana regen",
                "+5 Ice spell efficiency"));

        QualityToolsTooltipAdapter.moveQualityBlockToEnd(
                tooltip, "Quality:", 1, 1);

        assertEquals(Arrays.asList(
                "Ice Battlemage Leggings",
                "When on legs:",
                " +6 Armor",
                "+10 Mana",
                "+0.4 Mana regen",
                "+5 Ice spell efficiency",
                "",
                "Quality: Efficient Casting",
                "When on legs:",
                " +5 Spell Efficiency"), tooltip);
    }

    @Test
    void qualityBlockAlreadyAtEndRemainsStable() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                "Wizard Hat",
                "+10 Mana",
                "",
                "Quality: Manawoven",
                "When on head:",
                " +5 Maximum Mana"));

        QualityToolsTooltipAdapter.moveQualityBlockToEnd(
                tooltip, "Quality:", 1, 1);

        assertEquals(Arrays.asList(
                "Wizard Hat",
                "+10 Mana",
                "",
                "Quality: Manawoven",
                "When on head:",
                " +5 Maximum Mana"), tooltip);
    }

    @Test
    void zeroAmountModifierIsNotCountedAsAVisibleQualityLine() {
        NBTTagList modifiers = new NBTTagList();
        NBTTagCompound zero = new NBTTagCompound();
        zero.setDouble("Amount", 0.0D);
        modifiers.appendTag(zero);
        NBTTagCompound visible = new NBTTagCompound();
        visible.setDouble("Amount", 5.0D);
        modifiers.appendTag(visible);

        assertEquals(1, QualityToolsTooltipAdapter.countDisplayedModifiers(modifiers));
    }

    @Test
    void negativeZeroModifierMatchesQualityToolsAndIsNotCountedAsVisible() {
        NBTTagList modifiers = new NBTTagList();
        NBTTagCompound negativeZero = new NBTTagCompound();
        negativeZero.setDouble("Amount", -0.0D);
        modifiers.appendTag(negativeZero);

        assertEquals(0, QualityToolsTooltipAdapter.countDisplayedModifiers(modifiers));
    }
}
