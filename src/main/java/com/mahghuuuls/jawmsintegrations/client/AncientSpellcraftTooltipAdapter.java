package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaTooltips;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.EverfullManaState;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Iterator;
import java.util.List;

/** Replaces misleading native descriptions with server-authored JAWMS contribution text. */
public final class AncientSpellcraftTooltipAdapter {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation registryName = stack.getItem().getRegistryName();
        AncientReplacement replacement = AncientReplacement.forRegistryName(registryName);
        IntegrationPresentationSnapshot.Entry entry =
                ClientAncientPresentationCache.entry(registryName);
        if (replacement == null || entry == null || !entry.isEnabled()) {
            return;
        }
        if (replacement == AncientReplacement.CRYSTAL_RING) {
            removeExactLine(event.getToolTip(), I18n.format(
                    "item.ancientspellcraft:ring_mana_cost.desc"));
        }
        if (replacement == AncientReplacement.EVERFULL_MANA_FLASK) {
            int stored = new EverfullManaState().peek(stack);
            if (stored == EverfullManaState.UNAVAILABLE) {
                event.getToolTip().add(TextFormatting.BLUE + I18n.format(
                        "tooltip.jawmsintegrations.everfull.syncing"));
            } else {
                event.getToolTip().add(TextFormatting.BLUE + I18n.format(
                        "tooltip.jawmsintegrations.everfull.charge",
                        stored, (int) entry.getValue()));
            }
            event.getToolTip().add(TextFormatting.GRAY + I18n.format(
                    "tooltip.jawmsintegrations.everfull.behavior"));
            return;
        }
        if (replacement == AncientReplacement.RING_OF_DAGORIM) {
            removeExactLine(event.getToolTip(), I18n.format(
                    "item.ancientspellcraft:ring_mana_transfer.desc"));
            event.getToolTip().add(TextFormatting.GRAY + I18n.format(
                    "tooltip.jawmsintegrations.dagorim.behavior",
                    format(entry.getTertiaryValue()), (int) entry.getValue(),
                    (int) entry.getSecondaryValue()));
            return;
        }
        ManaContribution contribution = contributionFor(replacement, entry);
        if (contribution.isEmpty()) {
            return;
        }
        event.getToolTip().addAll(ManaTooltips.format(contribution));
    }

    static ManaContribution contributionFor(AncientReplacement replacement,
                                             IntegrationPresentationSnapshot.Entry entry) {
        if (replacement == AncientReplacement.CRYSTAL_RING) {
            return ManaContribution.builder()
                    .globalSpellEfficiency(entry.getValue()).build();
        }
        if (AncientReplacementPolicy.isStorage(replacement)) {
            return ManaContribution.builder()
                    .flatMaximumMana((int) entry.getValue()).build();
        }
        return ManaContribution.EMPTY;
    }

    static void removeExactLine(List<String> tooltip, String translated) {
        String expected = TextFormatting.getTextWithoutFormattingCodes(translated);
        Iterator<String> iterator = tooltip.iterator();
        while (iterator.hasNext()) {
            String plain = TextFormatting.getTextWithoutFormattingCodes(iterator.next());
            if (expected != null && expected.equals(plain)) {
                iterator.remove();
            }
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
