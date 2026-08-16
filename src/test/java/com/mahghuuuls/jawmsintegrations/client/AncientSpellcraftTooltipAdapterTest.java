package com.mahghuuuls.jawmsintegrations.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AncientSpellcraftTooltipAdapterTest {

    @Test
    void presentationUsesReplacementSpecificMaximumManaAndSpellEfficiencySemantics() {
        ManaContribution lesser = AncientSpellcraftTooltipAdapter.contributionFor(
                AncientReplacement.LESSER_MANA_RING,
                new IntegrationPresentationSnapshot.Entry(true, 8.0D));
        ManaContribution majestic = AncientSpellcraftTooltipAdapter.contributionFor(
                AncientReplacement.MAJESTIC_MANA_CHARM,
                new IntegrationPresentationSnapshot.Entry(true, 15.0D));
        ManaContribution crystal = AncientSpellcraftTooltipAdapter.contributionFor(
                AncientReplacement.CRYSTAL_RING,
                new IntegrationPresentationSnapshot.Entry(true, 25.0D));

        assertEquals(8, lesser.getFlatMaximumMana());
        assertEquals(0.0D, lesser.getMaximumManaIncrease());
        assertEquals(0.0D, lesser.getGlobalSpellEfficiency());
        assertEquals(0, majestic.getFlatMaximumMana());
        assertEquals(15.0D, majestic.getMaximumManaIncrease());
        assertEquals(0.0D, majestic.getGlobalSpellEfficiency());
        assertEquals(0, crystal.getFlatMaximumMana());
        assertEquals(0.0D, crystal.getMaximumManaIncrease());
        assertEquals(25.0D, crystal.getGlobalSpellEfficiency());
    }
}
