package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/** Sole owner of the versioned, integration-specific Everfull charge representation. */
public final class EverfullManaState {

    public static final int DATA_VERSION = 1;
    public static final int CAPACITY = 100;
    public static final int LEGACY_CAPACITY = 1400;
    public static final String ROOT_KEY = "jawmsintegrations";
    public static final String VERSION_KEY = "dataVersion";
    public static final String MANA_KEY = "everfullMana";
    public static final int UNAVAILABLE = -1;

    public int getOrImport(ItemStack stack, int legacyMana) {
        if (stack == null || stack.isEmpty()) {
            return UNAVAILABLE;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(ROOT_KEY)) {
            int imported = importLegacy(legacyMana);
            NBTTagCompound integration = new NBTTagCompound();
            integration.setInteger(VERSION_KEY, DATA_VERSION);
            integration.setInteger(MANA_KEY, imported);
            if (root == null) {
                root = new NBTTagCompound();
                stack.setTagCompound(root);
            }
            root.setTag(ROOT_KEY, integration);
            return imported;
        }
        if (!root.hasKey(ROOT_KEY, Constants.NBT.TAG_COMPOUND)) {
            return UNAVAILABLE;
        }
        NBTTagCompound integration = root.getCompoundTag(ROOT_KEY);
        if (!integration.hasKey(VERSION_KEY, Constants.NBT.TAG_ANY_NUMERIC)
                || integration.getInteger(VERSION_KEY) != DATA_VERSION
                || !integration.hasKey(MANA_KEY, Constants.NBT.TAG_ANY_NUMERIC)) {
            return UNAVAILABLE;
        }
        int stored = clamp(integration.getInteger(MANA_KEY));
        if (stored != integration.getInteger(MANA_KEY)) {
            integration.setInteger(MANA_KEY, stored);
        }
        return stored;
    }

    /** Read-only client/presentation access. Missing or unsupported state is never imported here. */
    public int peek(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTagCompound() == null) {
            return UNAVAILABLE;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(ROOT_KEY, Constants.NBT.TAG_COMPOUND)) {
            return UNAVAILABLE;
        }
        NBTTagCompound integration = root.getCompoundTag(ROOT_KEY);
        if (integration.getInteger(VERSION_KEY) != DATA_VERSION
                || !integration.hasKey(MANA_KEY, Constants.NBT.TAG_ANY_NUMERIC)) {
            return UNAVAILABLE;
        }
        return clamp(integration.getInteger(MANA_KEY));
    }

    public boolean set(ItemStack stack, int value) {
        if (peek(stack) == UNAVAILABLE) {
            return false;
        }
        stack.getTagCompound().getCompoundTag(ROOT_KEY).setInteger(MANA_KEY, clamp(value));
        return true;
    }

    static int importLegacy(int legacyMana) {
        int bounded = Math.max(0, Math.min(LEGACY_CAPACITY, legacyMana));
        return clamp((int) Math.round((double) bounded * CAPACITY / LEGACY_CAPACITY));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(CAPACITY, value));
    }
}
