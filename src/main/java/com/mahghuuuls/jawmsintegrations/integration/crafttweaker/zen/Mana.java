package com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen;

import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerManaService;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.player.IPlayer;
import net.minecraft.entity.player.EntityPlayer;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenClass("mods.jawmsintegrations.Mana")
public final class Mana {

    private Mana() {
    }

    @ZenMethod
    public static ManaState getState(IPlayer player) {
        return new ManaState(CraftTweakerManaService.get().getState(nativePlayer(player)));
    }

    @ZenMethod
    public static ManaMutationResult setCurrentMana(IPlayer player, int target) {
        return new ManaMutationResult(
                CraftTweakerManaService.get().setCurrentMana(nativePlayer(player), target));
    }

    @ZenMethod
    public static ManaMutationResult restoreMana(IPlayer player, int amount) {
        return new ManaMutationResult(
                CraftTweakerManaService.get().restoreMana(nativePlayer(player), amount));
    }

    @ZenMethod
    public static ManaMutationResult consumeMana(IPlayer player, int amount) {
        return new ManaMutationResult(
                CraftTweakerManaService.get().consumeMana(nativePlayer(player), amount));
    }

    @ZenMethod
    public static ManaMutationResult drainMana(IPlayer player, int maximumAmount) {
        return new ManaMutationResult(
                CraftTweakerManaService.get().drainMana(nativePlayer(player), maximumAmount));
    }

    @ZenMethod
    public static long startRegenerationLockout(IPlayer player) {
        return CraftTweakerManaService.get().startRegenerationLockout(nativePlayer(player));
    }

    private static EntityPlayer nativePlayer(IPlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("A CraftTweaker player is required");
        }
        EntityPlayer nativePlayer = CraftTweakerMC.getPlayer(player);
        if (nativePlayer == null) {
            throw new IllegalArgumentException("CraftTweaker could not resolve the supplied player");
        }
        return nativePlayer;
    }
}
