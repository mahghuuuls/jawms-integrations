package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityAttributeProjection;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Presents signed changes to the actual delay while preserving Quality Tools' benefit colors. */
public final class QualityToolsTooltipAdapter {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemTooltip(ItemTooltipEvent event) {
        List<String> tooltip = event.getToolTip();
        compactPercentage(tooltip,
                I18n.format("attribute.name." + QualityAttributeProjection.MAX_MANA_PERCENT));
        compactPercentage(tooltip,
                I18n.format("attribute.name." + QualityAttributeProjection.MANA_REGEN_PERCENT));
        compactPercentage(tooltip,
                I18n.format("attribute.name."
                        + QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_PERCENT));
        invertDelaySigns(tooltip,
                I18n.format("attribute.name."
                        + QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_FLAT));
        invertDelaySigns(tooltip,
                I18n.format("attribute.name."
                        + QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_PERCENT));
        moveQualityBlockToEnd(event.getItemStack().getSubCompound("Quality"), tooltip,
                I18n.format("info.quality.name"));
    }

    static void compactPercentage(List<String> tooltip, String translatedAttributeName) {
        if (tooltip == null || translatedAttributeName == null || translatedAttributeName.isEmpty()) {
            return;
        }
        for (int index = 0; index < tooltip.size(); index++) {
            String line = tooltip.get(index);
            if (line == null || !line.endsWith(translatedAttributeName)) {
                continue;
            }
            int nameStart = line.length() - translatedAttributeName.length();
            String numericPrefix = line.substring(0, nameStart).trim();
            // The percentage label is a suffix of the flat label ("s Post-Cast...").
            // Do not claim the flat seconds line while normalizing percentage spacing.
            if (numericPrefix.endsWith("s")) {
                continue;
            }
            if (!numericPrefix.endsWith("%")) {
                numericPrefix += "%";
            }
            tooltip.set(index, numericPrefix + " " + translatedAttributeName);
        }
    }

    static void invertDelaySigns(List<String> tooltip, String translatedAttributeName) {
        if (tooltip == null || translatedAttributeName == null || translatedAttributeName.isEmpty()) {
            return;
        }
        String positivePrefix = TextFormatting.BLUE + " +";
        String negativePrefix = TextFormatting.RED + " -";
        for (int index = 0; index < tooltip.size(); index++) {
            String line = tooltip.get(index);
            if (line == null || !line.endsWith(translatedAttributeName)) {
                continue;
            }
            if (line.startsWith(positivePrefix)) {
                tooltip.set(index, replaceSign(line, positivePrefix.length() - 1, '-'));
            } else if (line.startsWith(negativePrefix)) {
                tooltip.set(index, replaceSign(line, negativePrefix.length() - 1, '+'));
            }
        }
    }

    private static void moveQualityBlockToEnd(NBTTagCompound quality,
                                              List<String> tooltip,
                                              String translatedHeading) {
        if (quality == null || quality.isEmpty()) {
            return;
        }
        NBTTagList slots = quality.getTagList("Slots", 8);
        NBTTagList modifiers = quality.getTagList("AttributeModifiers", 10);
        moveQualityBlockToEnd(tooltip, translatedHeading, slots.tagCount(),
                countDisplayedModifiers(modifiers));
    }

    static int countDisplayedModifiers(NBTTagList modifiers) {
        int displayedModifiers = 0;
        for (int index = 0; index < modifiers.tagCount(); index++) {
            double amount = modifiers.getCompoundTagAt(index).getDouble("Amount");
            if (amount > 0.0D || amount < 0.0D) {
                displayedModifiers++;
            }
        }
        return displayedModifiers;
    }

    static void moveQualityBlockToEnd(List<String> tooltip,
                                      String translatedHeading,
                                      int slotCount,
                                      int modifierCount) {
        if (tooltip == null || translatedHeading == null || translatedHeading.isEmpty()
                || slotCount < 0 || modifierCount < 0) {
            return;
        }
        int qualityIndex = -1;
        for (int index = 0; index < tooltip.size(); index++) {
            String plain = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(index));
            if (plain != null && plain.startsWith(translatedHeading)) {
                qualityIndex = index;
                break;
            }
        }
        if (qualityIndex < 0) {
            return;
        }

        int blockStart = qualityIndex;
        if (qualityIndex > 0) {
            String previous = TextFormatting.getTextWithoutFormattingCodes(
                    tooltip.get(qualityIndex - 1));
            if (previous != null && previous.isEmpty()) {
                blockStart--;
            }
        }
        int blockEnd = qualityIndex + 1 + slotCount + modifierCount;
        if (blockEnd > tooltip.size() || blockEnd == tooltip.size()) {
            return;
        }

        List<String> qualityBlock = new ArrayList<>(tooltip.subList(blockStart, blockEnd));
        tooltip.subList(blockStart, blockEnd).clear();
        tooltip.addAll(qualityBlock);
    }

    private static String replaceSign(String line, int signIndex, char replacement) {
        return line.substring(0, signIndex) + replacement + line.substring(signIndex + 1);
    }
}
