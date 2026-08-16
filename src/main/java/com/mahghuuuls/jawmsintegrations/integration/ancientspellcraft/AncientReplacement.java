package com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft;

import net.minecraft.util.ResourceLocation;

/** The sole registry-ID allow-list for all approved Ancient Spellcraft replacements. */
public enum AncientReplacement {
    LESSER_MANA_RING("ring_mana_lesser"),
    GREATER_MANA_RING("ring_mana_greater"),
    MAJESTIC_MANA_CHARM("charm_majestic_mana"),
    CRYSTAL_RING("ring_mana_cost"),
    EVERFULL_MANA_FLASK("charm_mana_flask"),
    RING_OF_DAGORIM("ring_mana_transfer");

    private static final String NAMESPACE = "ancientspellcraft";
    private final ResourceLocation registryName;

    AncientReplacement(String path) {
        this.registryName = new ResourceLocation(NAMESPACE, path);
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public static AncientReplacement forRegistryName(ResourceLocation registryName) {
        if (registryName == null || !NAMESPACE.equals(registryName.getNamespace())) {
            return null;
        }
        for (AncientReplacement replacement : values()) {
            if (replacement.registryName.equals(registryName)) {
                return replacement;
            }
        }
        return null;
    }
}
