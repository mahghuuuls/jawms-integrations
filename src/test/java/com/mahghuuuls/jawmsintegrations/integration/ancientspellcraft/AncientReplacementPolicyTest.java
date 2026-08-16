package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AncientReplacementPolicyTest {

    @Test
    void allowListContainsExactlyTheSixApprovedRegistryIds() {
        Set<ResourceLocation> ids = new HashSet<>();
        for (AncientReplacement replacement : AncientReplacement.values()) {
            assertTrue(ids.add(replacement.getRegistryName()));
            assertEquals("ancientspellcraft", replacement.getRegistryName().getNamespace());
        }
        assertEquals(6, ids.size());
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "ring_mana_lesser")));
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "ring_mana_greater")));
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "charm_majestic_mana")));
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "ring_mana_cost")));
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "charm_mana_flask")));
        assertTrue(ids.contains(new ResourceLocation("ancientspellcraft", "ring_mana_transfer")));
        assertNull(AncientReplacement.forRegistryName(
                new ResourceLocation("ancientspellcraft", "charm_mana_orb")));
        assertNull(AncientReplacement.forRegistryName(
                new ResourceLocation("otheraddon", "ring_mana_lesser")));
    }

    @Test
    void defaultStaticContributionsApplyOnlyFromBaublesSlots() {
        AncientReplacementPolicy policy = enabledDefaults();

        assertContribution(policy, AncientReplacement.LESSER_MANA_RING, 8, 0.0D, 0.0D);
        assertContribution(policy, AncientReplacement.GREATER_MANA_RING, 12, 0.0D, 0.0D);
        assertContribution(policy, AncientReplacement.MAJESTIC_MANA_CHARM, 0, 15.0D, 0.0D);
        assertContribution(policy, AncientReplacement.CRYSTAL_RING, 0, 0.0D, 25.0D);

        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);
        ManaContribution ignored = policy.contribution(
                new ManaItemContext(lesser, ManaSlotCategory.MAIN_HAND, 0));
        assertTrue(ignored.isEmpty());
    }

    @Test
    void independentDisablementSuppressesNeitherContributionNorLegacyBehavior() {
        IntegrationConfigSnapshot.AncientSpellcraftConfig config =
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(
                        true,
                        new IntegrationConfigSnapshot.ToggleIntConfig(false, 8),
                        new IntegrationConfigSnapshot.ToggleIntConfig(true, 14),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(true, 20.0D),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(true, 30.0D));
        AncientReplacementPolicy policy = new AncientReplacementPolicy(true, config);

        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);
        assertTrue(policy.contribution(context(lesser)).isEmpty());
        assertFalse(policy.isStorageReplacement(lesser));
        assertContribution(policy, AncientReplacement.GREATER_MANA_RING, 14, 0.0D, 0.0D);
        assertContribution(policy, AncientReplacement.MAJESTIC_MANA_CHARM, 0, 20.0D, 0.0D);
        assertContribution(policy, AncientReplacement.CRYSTAL_RING, 0, 0.0D, 30.0D);
    }

    @Test
    void policyInspectionPreservesLegacyNbtByteForByte() {
        AncientReplacementPolicy policy = enabledDefaults();
        ItemStack stack = stack(AncientReplacement.GREATER_MANA_RING);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("UnrelatedLegacyCharge", 731);
        stack.setTagCompound(legacy);
        NBTTagCompound before = stack.serializeNBT().copy();

        assertTrue(policy.isStorageReplacement(stack));
        assertEquals(12, policy.contribution(context(stack)).getFlatMaximumMana());
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void futureAndUnlistedItemsHaveNoStaticContributionOrSuppression() {
        AncientReplacementPolicy policy = enabledDefaults();
        ItemStack future = stack(AncientReplacement.EVERFULL_MANA_FLASK);
        ItemStack unlisted = stack(new ResourceLocation("ancientspellcraft", "charm_mana_orb"));

        assertTrue(policy.contribution(context(future)).isEmpty());
        assertFalse(policy.isStorageReplacement(future));
        assertTrue(policy.contribution(context(unlisted)).isEmpty());
        assertFalse(policy.isStorageReplacement(unlisted));
    }

    @Test
    void dagorimHasAnIndependentExactRegistryToggle() {
        AncientReplacementPolicy enabled = enabledDefaults();
        ItemStack ring = stack(AncientReplacement.RING_OF_DAGORIM);
        assertTrue(enabled.shouldReplaceDagorim(ring));
        assertFalse(enabled.shouldReplaceDagorim(stack(
                new ResourceLocation("ancientspellcraft", "other_ring"))));

        IntegrationConfigSnapshot.AncientSpellcraftConfig defaults =
                IntegrationConfigSnapshot.defaults().getAncientSpellcraft();
        AncientReplacementPolicy disabled = new AncientReplacementPolicy(true,
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(true,
                        defaults.getLesserManaRing(), defaults.getGreaterManaRing(),
                        defaults.getMajesticManaCharm(), defaults.getCrystalRing(),
                        defaults.getEverfullManaFlask(),
                        new IntegrationConfigSnapshot.RingOfDagorimConfig(false, 5, 20, 20.0D)));
        assertFalse(disabled.shouldReplaceDagorim(ring));
        assertTrue(disabled.isEverfullEnabled());
    }

    private static AncientReplacementPolicy enabledDefaults() {
        return new AncientReplacementPolicy(true,
                IntegrationConfigSnapshot.defaults().getAncientSpellcraft());
    }

    private static void assertContribution(AncientReplacementPolicy policy,
                                           AncientReplacement replacement,
                                           int flatMaximumMana,
                                           double maximumManaIncrease,
                                           double spellEfficiency) {
        ManaContribution contribution = policy.contribution(context(stack(replacement)));
        assertEquals(flatMaximumMana, contribution.getFlatMaximumMana());
        assertEquals(maximumManaIncrease, contribution.getMaximumManaIncrease());
        assertEquals(spellEfficiency, contribution.getGlobalSpellEfficiency());
    }

    private static ManaItemContext context(ItemStack stack) {
        return new ManaItemContext(stack, ManaSlotCategory.BAUBLES, 0);
    }

    private static ItemStack stack(AncientReplacement replacement) {
        return stack(replacement.getRegistryName());
    }

    private static ItemStack stack(ResourceLocation registryName) {
        Bootstrap.register();
        Item item = new Item();
        item.setRegistryName(registryName);
        return new ItemStack(item);
    }
}
