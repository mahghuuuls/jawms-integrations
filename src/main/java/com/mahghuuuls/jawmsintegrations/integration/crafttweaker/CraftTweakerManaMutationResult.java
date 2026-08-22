package com.mahghuuuls.jawmsintegrations.integration.crafttweaker;

import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaMutationResult;

/** Immutable dependency-free mutation result exposed through the optional Zen wrapper. */
public final class CraftTweakerManaMutationResult {

    private final boolean successful;
    private final String failure;
    private final int requestedAmount;
    private final int actualDelta;
    private final int actualAmount;
    private final CraftTweakerManaState oldState;
    private final CraftTweakerManaState newState;

    private CraftTweakerManaMutationResult(ManaMutationResult result) {
        this.successful = result.isSuccessful();
        this.failure = failureName(result.getFailure());
        this.requestedAmount = result.getRequestedAmount();
        this.actualDelta = result.getActualDelta();
        this.actualAmount = result.getActualAmount();
        this.oldState = CraftTweakerManaState.from(result.getOldState());
        this.newState = CraftTweakerManaState.from(result.getNewState());
    }

    public static CraftTweakerManaMutationResult from(ManaMutationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("JAWMS mutation result must not be null");
        }
        return new CraftTweakerManaMutationResult(result);
    }

    private static String failureName(ManaMutationFailure failure) {
        if (failure == null) {
            throw new IllegalArgumentException("JAWMS mutation failure must not be null");
        }
        switch (failure) {
            case NONE:
                return "none";
            case INSUFFICIENT_MANA:
                return "insufficient_mana";
            case REJECTED:
                return "rejected";
            default:
                throw new IllegalStateException("Unknown JAWMS mutation failure " + failure);
        }
    }

    public boolean isSuccessful() { return successful; }
    public String getFailure() { return failure; }
    public int getRequestedAmount() { return requestedAmount; }
    public int getActualDelta() { return actualDelta; }
    public int getActualAmount() { return actualAmount; }
    public CraftTweakerManaState getOldState() { return oldState; }
    public CraftTweakerManaState getNewState() { return newState; }
}
