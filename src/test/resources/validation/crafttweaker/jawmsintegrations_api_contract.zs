import crafttweaker.player.IPlayer;
import mods.jawmsintegrations.Mana;

// Compile-only contract fixture. The function is intentionally not invoked at load time:
// owner validation invokes mutations explicitly against a known logical-server player.
function jawmsIntegrationsApiContract(player as IPlayer) {
    val state = Mana.getState(player);
    print("" ~ state.currentMana);
    print("" ~ state.maximumMana);
    print("" ~ state.effectiveRegeneration);
    print("" ~ state.effectiveLockoutTicks);
    print("" ~ state.remainingLockoutTicks);
    print("" ~ state.effectiveLockoutSeconds);
    print("" ~ state.remainingLockoutSeconds);
    print("" ~ state.canRegenerateDuringPostCastLockout);
    print("" ~ state.canRegenerateDuringContinuousCasting);
    print("" ~ state.continuousCastActive);
    print("" ~ state.regenerationEligible);

    val setResult = Mana.setCurrentMana(player, 0);
    val restoreResult = Mana.restoreMana(player, 0);
    val consumeResult = Mana.consumeMana(player, 0);
    val drainResult = Mana.drainMana(player, 0);
    print("" ~ setResult.successful);
    print("" ~ setResult.failure);
    print("" ~ setResult.requestedAmount);
    print("" ~ setResult.actualDelta);
    print("" ~ setResult.actualAmount);
    print("" ~ setResult.oldState.currentMana);
    print("" ~ setResult.newState.currentMana);
    print("" ~ restoreResult.successful);
    print("" ~ consumeResult.successful);
    print("" ~ drainResult.successful);
    print("" ~ Mana.startRegenerationLockout(player));
}
