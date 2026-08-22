package com.mahghuuuls.jawmsintegrations.integration.crafttweaker;

import com.mahghuuuls.jawms.api.ManaPublicState;

/** Immutable dependency-free state exposed through the optional Zen wrapper. */
public final class CraftTweakerManaState {

    private final int currentMana;
    private final int maximumMana;
    private final double effectiveRegeneration;
    private final long effectiveLockoutTicks;
    private final long remainingLockoutTicks;
    private final boolean regenerateDuringPostCastLockout;
    private final boolean regenerateDuringContinuousCasting;
    private final boolean continuousCastActive;
    private final boolean regenerationEligible;

    private CraftTweakerManaState(ManaPublicState state) {
        this.currentMana = state.getCurrentMana();
        this.maximumMana = state.getMaximumMana();
        this.effectiveRegeneration = state.getEffectiveRegeneration();
        this.effectiveLockoutTicks = state.getEffectiveLockoutTicks();
        this.remainingLockoutTicks = state.getRemainingLockoutTicks();
        this.regenerateDuringPostCastLockout = state.canRegenerateDuringPostCastLockout();
        this.regenerateDuringContinuousCasting = state.canRegenerateDuringContinuousCasting();
        this.continuousCastActive = state.isContinuousCastActive();
        this.regenerationEligible = state.isRegenerationEligible();
    }

    public static CraftTweakerManaState from(ManaPublicState state) {
        if (state == null) {
            throw new IllegalArgumentException("JAWMS state must not be null");
        }
        return new CraftTweakerManaState(state);
    }

    public int getCurrentMana() { return currentMana; }
    public int getMaximumMana() { return maximumMana; }
    public double getEffectiveRegeneration() { return effectiveRegeneration; }
    public long getEffectiveLockoutTicks() { return effectiveLockoutTicks; }
    public long getRemainingLockoutTicks() { return remainingLockoutTicks; }
    public double getEffectiveLockoutSeconds() { return effectiveLockoutTicks / 20.0D; }
    public double getRemainingLockoutSeconds() { return remainingLockoutTicks / 20.0D; }
    public boolean canRegenerateDuringPostCastLockout() { return regenerateDuringPostCastLockout; }
    public boolean canRegenerateDuringContinuousCasting() { return regenerateDuringContinuousCasting; }
    public boolean isContinuousCastActive() { return continuousCastActive; }
    public boolean isRegenerationEligible() { return regenerationEligible; }
}
