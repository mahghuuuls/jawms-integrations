package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.mahghuuuls.jawmsintegrations.network.IntegrationPresentationSnapshot;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AncientSuppressionMixinTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @AfterEach
    void resetAuthority() {
        AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
        ClientIntegrationPresentationCache.clear();
    }

    @Test
    void spellCastRedirectSuppressesStoragePaymentAndCrystalCostField() throws Exception {
        AncientReplacementPolicy.install(enabledPolicy());
        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);

        Method payment = MixinASEventHandler.class.getDeclaredMethod(
                "jawmsIntegrations$suppressStoragePayment",
                com.windanesz.ancientspellcraft.item.ItemManaArtefact.class,
                ItemStack.class);
        payment.setAccessible(true);
        assertEquals(-1, payment.invoke(null, null, lesser));

        Method crystal = MixinASEventHandler.class.getDeclaredMethod(
                "jawmsIntegrations$suppressCrystalCostModifier");
        crystal.setAccessible(true);
        assertSame(Items.AIR, crystal.invoke(null));
    }

    @Test
    void storageSentinelCannotEnterAncientZeroCostPaymentBranchOrMutateLegacyData()
            throws Exception {
        AncientReplacementPolicy.install(enabledPolicy());
        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("LegacyCharge", 400);
        lesser.setTagCompound(legacy);
        NBTTagCompound before = lesser.serializeNBT().copy();
        Method payment = MixinASEventHandler.class.getDeclaredMethod(
                "jawmsIntegrations$suppressStoragePayment",
                com.windanesz.ancientspellcraft.item.ItemManaArtefact.class,
                ItemStack.class);
        payment.setAccessible(true);

        int redirectedMana = (Integer) payment.invoke(null, null, lesser);
        int zeroCost = 0;
        boolean ancientSetManaReached = false;
        if (redirectedMana >= zeroCost) {
            ancientSetManaReached = true;
            lesser.getTagCompound().setInteger("LegacyCharge", redirectedMana - zeroCost);
        }

        assertFalse(ancientSetManaReached);
        assertEquals(before, lesser.serializeNBT());
    }

    @Test
    void rechargeHookCancelsOnlyAnActiveStorageReplacement() throws Exception {
        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);
        InventoryBasic inventory = new InventoryBasic("test", false, 1);
        inventory.setInventorySlotContents(0, lesser);
        Slot itemSlot = new Slot(inventory, 0, 0, 0);
        Method recharge = MixinItemManaArtefact.class.getDeclaredMethod(
                "jawmsIntegrations$suppressRecharge",
                net.minecraft.entity.player.EntityPlayer.class,
                Slot.class, Slot.class, Slot.class, Slot[].class,
                CallbackInfoReturnable.class);
        recharge.setAccessible(true);

        AncientReplacementPolicy.install(enabledPolicy());
        CallbackInfoReturnable<Boolean> active =
                new CallbackInfoReturnable<>("test", true);
        recharge.invoke(new TestManaArtefactMixin(), null, itemSlot, null, null,
                new Slot[0], active);
        assertTrue(active.isCancelled());
        assertFalse(active.getReturnValue());

        AncientReplacementPolicy.install(AncientReplacementPolicy.disabled());
        CallbackInfoReturnable<Boolean> disabled =
                new CallbackInfoReturnable<>("test", true);
        recharge.invoke(new TestManaArtefactMixin(), null, itemSlot, null, null,
                new Slot[0], disabled);
        assertFalse(disabled.isCancelled());
    }

    @Test
    void clientHooksSuppressLegacyTooltipAndBarOnlyFromAcceptedSnapshot() throws Exception {
        ItemStack lesser = stack(AncientReplacement.LESSER_MANA_RING);
        Method tooltip = MixinItemManaArtefactClient.class.getDeclaredMethod(
                "jawmsIntegrations$suppressLegacyTooltip",
                ItemStack.class, net.minecraft.world.World.class, java.util.List.class,
                net.minecraft.client.util.ITooltipFlag.class, CallbackInfo.class);
        tooltip.setAccessible(true);
        Method bar = MixinItemManaArtefactClient.class.getDeclaredMethod(
                "showDurabilityBar", ItemStack.class);
        Item nativeItem = new Item().setMaxDamage(500);
        nativeItem.setRegistryName(AncientReplacement.LESSER_MANA_RING.getRegistryName());
        ItemStack nativeStack = new ItemStack(nativeItem);
        nativeStack.setItemDamage(100);
        NBTTagCompound originalTag = new NBTTagCompound();
        originalTag.setInteger("LegacyCharge", 400);
        nativeStack.setTagCompound(originalTag);
        NBTTagCompound before = nativeStack.serializeNBT().copy();

        CallbackInfo beforeSnapshot = new CallbackInfo("test", true);
        tooltip.invoke(new TestTooltipMixin(), lesser, null, new ArrayList<>(), null,
                beforeSnapshot);
        assertFalse(beforeSnapshot.isCancelled());
        assertTrue((Boolean) bar.invoke(new TestTooltipMixin(), nativeStack));
        assertEquals(before, nativeStack.serializeNBT());

        ClientIntegrationPresentationCache.install(
                IntegrationPresentationSnapshot.from(false, enabledPolicy()));
        CallbackInfo active = new CallbackInfo("test", true);
        tooltip.invoke(new TestTooltipMixin(), lesser, null, new ArrayList<>(), null, active);
        assertTrue(active.isCancelled());
        assertFalse((Boolean) bar.invoke(new TestTooltipMixin(), nativeStack));
        assertEquals(before, nativeStack.serializeNBT());
    }

    @Test
    void itemLocalBarDecisionPreservesNativeFallbackForUndamagedAndUnlistedItems()
            throws Exception {
        Method bar = MixinItemManaArtefactClient.class.getDeclaredMethod(
                "showDurabilityBar", ItemStack.class);
        TestTooltipMixin mixin = new TestTooltipMixin();

        Item storageItem = new Item().setMaxDamage(500);
        storageItem.setRegistryName(AncientReplacement.GREATER_MANA_RING.getRegistryName());
        ItemStack undamagedStorage = new ItemStack(storageItem);
        assertFalse((Boolean) bar.invoke(mixin, undamagedStorage));

        Item unlistedItem = new Item().setMaxDamage(500);
        unlistedItem.setRegistryName("ancientspellcraft", "unlisted_mana_artefact");
        ItemStack damagedUnlisted = new ItemStack(unlistedItem);
        damagedUnlisted.setItemDamage(100);
        NBTTagCompound unlistedTag = new NBTTagCompound();
        unlistedTag.setInteger("LegacyCharge", 400);
        damagedUnlisted.setTagCompound(unlistedTag);
        NBTTagCompound unlistedBefore = damagedUnlisted.serializeNBT().copy();
        ClientIntegrationPresentationCache.install(
                IntegrationPresentationSnapshot.from(false, enabledPolicy()));
        assertTrue((Boolean) bar.invoke(mixin, damagedUnlisted));
        assertEquals(unlistedBefore, damagedUnlisted.serializeNBT());
    }

    @Test
    void itemLocalBarDecisionCoversEveryStorageReplacementAndIndividualDisable()
            throws Exception {
        Method bar = MixinItemManaArtefactClient.class.getDeclaredMethod(
                "showDurabilityBar", ItemStack.class);
        TestTooltipMixin mixin = new TestTooltipMixin();
        for (AncientReplacement replacement : new AncientReplacement[]{
                AncientReplacement.LESSER_MANA_RING,
                AncientReplacement.GREATER_MANA_RING,
                AncientReplacement.MAJESTIC_MANA_CHARM}) {
            ItemStack stack = damagedStack(replacement);
            NBTTagCompound before = stack.serializeNBT().copy();

            ClientIntegrationPresentationCache.install(
                    IntegrationPresentationSnapshot.from(false, enabledPolicy()));
            assertFalse((Boolean) bar.invoke(mixin, stack), replacement.name());
            assertEquals(before, stack.serializeNBT(), replacement.name());

            ClientIntegrationPresentationCache.install(
                    IntegrationPresentationSnapshot.from(false, policyWithDisabled(replacement)));
            assertTrue((Boolean) bar.invoke(mixin, stack), replacement.name());
            assertEquals(before, stack.serializeNBT(), replacement.name());
        }
    }

    @Test
    void wrappedNativeDescriptionsAreSuppressedAtSourceForCrystalAndDagorimOnly()
            throws Exception {
        Method tooltip = MixinItemArtefactClient.class.getDeclaredMethod(
                "jawmsIntegrations$suppressReplacedDescription",
                ItemStack.class, net.minecraft.world.World.class, java.util.List.class,
                net.minecraft.client.util.ITooltipFlag.class, CallbackInfo.class);
        tooltip.setAccessible(true);
        ItemStack crystal = stack(AncientReplacement.CRYSTAL_RING);
        ItemStack dagorim = stack(AncientReplacement.RING_OF_DAGORIM);
        ItemStack unrelated = new ItemStack(new Item().setRegistryName(
                new net.minecraft.util.ResourceLocation("ancientspellcraft", "ring_power")));

        CallbackInfo beforeSnapshot = new CallbackInfo("test", true);
        tooltip.invoke(new TestArtefactMixin(), crystal, null, new ArrayList<>(), null,
                beforeSnapshot);
        assertFalse(beforeSnapshot.isCancelled());

        ClientIntegrationPresentationCache.install(
                IntegrationPresentationSnapshot.from(false, enabledPolicy()));
        CallbackInfo crystalActive = new CallbackInfo("test", true);
        tooltip.invoke(new TestArtefactMixin(), crystal, null, new ArrayList<>(), null,
                crystalActive);
        assertTrue(crystalActive.isCancelled());
        CallbackInfo dagorimActive = new CallbackInfo("test", true);
        tooltip.invoke(new TestArtefactMixin(), dagorim, null, new ArrayList<>(), null,
                dagorimActive);
        assertTrue(dagorimActive.isCancelled());
        CallbackInfo unrelatedActive = new CallbackInfo("test", true);
        tooltip.invoke(new TestArtefactMixin(), unrelated, null, new ArrayList<>(), null,
                unrelatedActive);
        assertFalse(unrelatedActive.isCancelled());
    }

    private static AncientReplacementPolicy enabledPolicy() {
        return new AncientReplacementPolicy(true,
                IntegrationConfigSnapshot.defaults().getAncientSpellcraft());
    }

    private static ItemStack stack(AncientReplacement replacement) {
        Item item = new Item();
        item.setRegistryName(replacement.getRegistryName());
        return new ItemStack(item);
    }

    private static ItemStack damagedStack(AncientReplacement replacement) {
        Item item = new Item().setMaxDamage(500);
        item.setRegistryName(replacement.getRegistryName());
        ItemStack stack = new ItemStack(item);
        stack.setItemDamage(100);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("LegacyCharge", 400);
        stack.setTagCompound(tag);
        return stack;
    }

    private static AncientReplacementPolicy policyWithDisabled(AncientReplacement disabled) {
        IntegrationConfigSnapshot.AncientSpellcraftConfig config =
                new IntegrationConfigSnapshot.AncientSpellcraftConfig(
                        true,
                        new IntegrationConfigSnapshot.ToggleIntConfig(
                                disabled != AncientReplacement.LESSER_MANA_RING, 8),
                        new IntegrationConfigSnapshot.ToggleIntConfig(
                                disabled != AncientReplacement.GREATER_MANA_RING, 12),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(
                                disabled != AncientReplacement.MAJESTIC_MANA_CHARM, 15.0D),
                        new IntegrationConfigSnapshot.ToggleDoubleConfig(true, 25.0D));
        return new AncientReplacementPolicy(true, config);
    }

    private static final class TestManaArtefactMixin extends MixinItemManaArtefact {
    }

    private static final class TestTooltipMixin extends MixinItemManaArtefactClient {
    }

    private static final class TestArtefactMixin extends MixinItemArtefactClient {
    }

}
