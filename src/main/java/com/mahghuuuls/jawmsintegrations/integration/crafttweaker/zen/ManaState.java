package com.mahghuuuls.jawmsintegrations.integration.crafttweaker.zen;

import com.mahghuuuls.jawmsintegrations.integration.crafttweaker.CraftTweakerManaState;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;

@ZenClass("mods.jawmsintegrations.ManaState")
public final class ManaState {

    private final CraftTweakerManaState state;

    ManaState(CraftTweakerManaState state) {
        if (state == null) throw new IllegalArgumentException("Mana state must not be null");
        this.state = state;
    }

    @ZenGetter("currentMana") public int getCurrentMana() { return state.getCurrentMana(); }
    @ZenGetter("maximumMana") public int getMaximumMana() { return state.getMaximumMana(); }
    @ZenGetter("effectiveRegeneration") public double getEffectiveRegeneration() {
        return state.getEffectiveRegeneration();
    }
    @ZenGetter("effectiveLockoutTicks") public long getEffectiveLockoutTicks() {
        return state.getEffectiveLockoutTicks();
    }
    @ZenGetter("remainingLockoutTicks") public long getRemainingLockoutTicks() {
        return state.getRemainingLockoutTicks();
    }
    @ZenGetter("effectiveLockoutSeconds") public double getEffectiveLockoutSeconds() {
        return state.getEffectiveLockoutSeconds();
    }
    @ZenGetter("remainingLockoutSeconds") public double getRemainingLockoutSeconds() {
        return state.getRemainingLockoutSeconds();
    }
    @ZenGetter("canRegenerateDuringPostCastLockout")
    public boolean canRegenerateDuringPostCastLockout() {
        return state.canRegenerateDuringPostCastLockout();
    }
    @ZenGetter("canRegenerateDuringContinuousCasting")
    public boolean canRegenerateDuringContinuousCasting() {
        return state.canRegenerateDuringContinuousCasting();
    }
    @ZenGetter("continuousCastActive") public boolean isContinuousCastActive() {
        return state.isContinuousCastActive();
    }
    @ZenGetter("regenerationEligible") public boolean isRegenerationEligible() {
        return state.isRegenerationEligible();
    }
}
