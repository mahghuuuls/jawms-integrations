package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityAttributeProjection;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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

    private static String replaceSign(String line, int signIndex, char replacement) {
        return line.substring(0, signIndex) + replacement + line.substring(signIndex + 1);
    }
}
