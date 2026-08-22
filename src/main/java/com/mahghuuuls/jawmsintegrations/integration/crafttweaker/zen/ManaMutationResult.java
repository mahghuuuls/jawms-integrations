package com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen;

import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerManaMutationResult;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;

@ZenClass("mods.jawmsintegrations.ManaMutationResult")
public final class ManaMutationResult {

    private final CraftTweakerManaMutationResult result;

    ManaMutationResult(CraftTweakerManaMutationResult result) {
        if (result == null) throw new IllegalArgumentException("Mana mutation result must not be null");
        this.result = result;
    }

    @ZenGetter("successful") public boolean isSuccessful() { return result.isSuccessful(); }
    @ZenGetter("failure") public String getFailure() { return result.getFailure(); }
    @ZenGetter("requestedAmount") public int getRequestedAmount() {
        return result.getRequestedAmount();
    }
    @ZenGetter("actualDelta") public int getActualDelta() { return result.getActualDelta(); }
    @ZenGetter("actualAmount") public int getActualAmount() { return result.getActualAmount(); }
    @ZenGetter("oldState") public ManaState getOldState() {
        return new ManaState(result.getOldState());
    }
    @ZenGetter("newState") public ManaState getNewState() {
        return new ManaState(result.getNewState());
    }
}
