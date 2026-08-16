package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EverfullManaStateTest {

    private final EverfullManaState state = new EverfullManaState();

    @Test
    void proportionalImportClampsRoundsAndWritesVersionedNamespacedState() {
        assertEquals(0, EverfullManaState.importLegacy(-50));
        assertEquals(0, EverfullManaState.importLegacy(0));
        assertEquals(1, EverfullManaState.importLegacy(7));
        assertEquals(50, EverfullManaState.importLegacy(700));
        assertEquals(100, EverfullManaState.importLegacy(1400));
        assertEquals(100, EverfullManaState.importLegacy(5000));

        ItemStack stack = stack();
        assertEquals(50, state.getOrImport(stack, 700));
        NBTTagCompound integration = stack.getTagCompound()
                .getCompoundTag(EverfullManaState.ROOT_KEY);
        assertEquals(EverfullManaState.DATA_VERSION,
                integration.getInteger(EverfullManaState.VERSION_KEY));
        assertEquals(50, integration.getInteger(EverfullManaState.MANA_KEY));
    }

    @Test
    void importIsIdempotentAndNeverTouchesLegacyOrUnrelatedData() {
        ItemStack stack = stack();
        stack.setItemDamage(321);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("otherMod", "preserve-me");
        stack.setTagCompound(root);

        assertEquals(25, state.getOrImport(stack, 350));
        assertEquals(25, state.getOrImport(stack, 1400));
        assertEquals(321, stack.getItemDamage());
        assertEquals("preserve-me", stack.getTagCompound().getString("otherMod"));
    }

    @Test
    void unknownOrMalformedOwnedStateFailsClosedWithoutOverwrite() {
        ItemStack stack = stack();
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound future = new NBTTagCompound();
        future.setInteger(EverfullManaState.VERSION_KEY, 2);
        future.setInteger(EverfullManaState.MANA_KEY, 77);
        root.setTag(EverfullManaState.ROOT_KEY, future);
        stack.setTagCompound(root);
        NBTTagCompound before = stack.serializeNBT().copy();

        assertEquals(EverfullManaState.UNAVAILABLE, state.getOrImport(stack, 1400));
        assertFalse(state.set(stack, 20));
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void updatesClampOnlyTheOwnedManaField() {
        ItemStack stack = stack();
        state.getOrImport(stack, 700);

        assertTrue(state.set(stack, 500));
        assertEquals(100, state.peek(stack));
        assertTrue(state.set(stack, -4));
        assertEquals(0, state.peek(stack));
    }

    private static ItemStack stack() {
        return new ItemStack(new Item());
    }
}
