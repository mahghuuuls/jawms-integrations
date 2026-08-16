package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.util.text.TextFormatting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AncientSpellcraftTooltipAdapterTest {

    @Test
    void presentationUsesTheServerSnapshotForFlatManaAndSpellEfficiency() {
        ManaContribution lesser = AncientSpellcraftTooltipAdapter.contributionFor(
                AncientReplacement.LESSER_MANA_RING,
                new IntegrationPresentationSnapshot.Entry(true, 8.0D));
        ManaContribution crystal = AncientSpellcraftTooltipAdapter.contributionFor(
                AncientReplacement.CRYSTAL_RING,
                new IntegrationPresentationSnapshot.Entry(true, 25.0D));

        assertEquals(8, lesser.getFlatMaximumMana());
        assertEquals(0.0D, lesser.getGlobalSpellEfficiency());
        assertEquals(0, crystal.getFlatMaximumMana());
        assertEquals(25.0D, crystal.getGlobalSpellEfficiency());
    }

    @Test
    void crystalCorrectionRemovesOnlyTheExactLegacyDescription() {
        List<String> tooltip = new ArrayList<>(Arrays.asList(
                "Crystal Ring",
                TextFormatting.GRAY + "Reduces mana costs by 7.5%",
                "Another mod: Reduces mana costs by 7.5% while charged",
                "Unrelated line"));

        AncientSpellcraftTooltipAdapter.removeExactLine(
                tooltip, "Reduces mana costs by 7.5%");

        assertEquals(Arrays.asList(
                "Crystal Ring",
                "Another mod: Reduces mana costs by 7.5% while charged",
                "Unrelated line"), tooltip);
    }
}
