package com.mahghuuuls.jawmsintegrations.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

/** Owns only terminal placement of the complete NBT-backed Quality Tools block. */
public final class QualityToolsTooltipFinalizer {

    private QualityToolsTooltipFinalizer() {
    }

    public static void finalizeTooltip(boolean integrationActive,
                                       NBTTagCompound quality,
                                       List<String> tooltip,
                                       String translatedHeading) {
        if (!integrationActive || quality == null || quality.isEmpty()) {
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
}
