package com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen;

import crafttweaker.CraftTweakerAPI;

/** Loaded only after exact supported CraftTweaker evidence is accepted. */
public final class CraftTweakerRegistrar {

    private CraftTweakerRegistrar() {
    }

    public static void register() {
        CraftTweakerAPI.registerClass(ManaState.class);
        CraftTweakerAPI.registerClass(ManaMutationResult.class);
        CraftTweakerAPI.registerClass(Mana.class);
    }
}
