import crafttweaker.event.PlayerRightClickItemEvent;
import mods.jawmsintegrations.Mana;

// Development-only owner campaign. Right-click the named blaze rod in the air.
events.onPlayerRightClickItem(function(event as PlayerRightClickItemEvent) {
    if (!event.world.remote && event.item.definition.id == "minecraft:blaze_rod") {
        event.cancel();
        val player = event.player;
        val initial = Mana.getState(player);
        print("[JAWMS-CT] BEGIN player=" ~ player.name ~ " current=" ~ initial.currentMana
            ~ " maximum=" ~ initial.maximumMana ~ " regen=" ~ initial.effectiveRegeneration
            ~ " effectiveLockout=" ~ initial.effectiveLockoutTicks
            ~ " remainingLockout=" ~ initial.remainingLockoutTicks);

        val setResult = Mana.setCurrentMana(player, 40);
        print("[JAWMS-CT] SET success=" ~ setResult.successful ~ " failure=" ~ setResult.failure
            ~ " requested=" ~ setResult.requestedAmount ~ " delta=" ~ setResult.actualDelta
            ~ " actual=" ~ setResult.actualAmount ~ " old=" ~ setResult.oldState.currentMana
            ~ " new=" ~ setResult.newState.currentMana);

        val restoreResult = Mana.restoreMana(player, 10);
        print("[JAWMS-CT] RESTORE success=" ~ restoreResult.successful
            ~ " failure=" ~ restoreResult.failure ~ " requested=" ~ restoreResult.requestedAmount
            ~ " delta=" ~ restoreResult.actualDelta ~ " actual=" ~ restoreResult.actualAmount
            ~ " old=" ~ restoreResult.oldState.currentMana
            ~ " new=" ~ restoreResult.newState.currentMana);

        val consumeResult = Mana.consumeMana(player, 7);
        print("[JAWMS-CT] CONSUME success=" ~ consumeResult.successful
            ~ " failure=" ~ consumeResult.failure ~ " requested=" ~ consumeResult.requestedAmount
            ~ " delta=" ~ consumeResult.actualDelta ~ " actual=" ~ consumeResult.actualAmount
            ~ " old=" ~ consumeResult.oldState.currentMana
            ~ " new=" ~ consumeResult.newState.currentMana);

        val insufficientResult = Mana.consumeMana(player, 1000000);
        print("[JAWMS-CT] INSUFFICIENT success=" ~ insufficientResult.successful
            ~ " failure=" ~ insufficientResult.failure
            ~ " requested=" ~ insufficientResult.requestedAmount
            ~ " delta=" ~ insufficientResult.actualDelta
            ~ " actual=" ~ insufficientResult.actualAmount
            ~ " old=" ~ insufficientResult.oldState.currentMana
            ~ " new=" ~ insufficientResult.newState.currentMana);

        val drainResult = Mana.drainMana(player, 5);
        print("[JAWMS-CT] DRAIN success=" ~ drainResult.successful
            ~ " failure=" ~ drainResult.failure ~ " requested=" ~ drainResult.requestedAmount
            ~ " delta=" ~ drainResult.actualDelta ~ " actual=" ~ drainResult.actualAmount
            ~ " old=" ~ drainResult.oldState.currentMana
            ~ " new=" ~ drainResult.newState.currentMana);

        val beforeLockout = Mana.getState(player);
        val appliedTicks = Mana.startRegenerationLockout(player);
        val afterLockout = Mana.getState(player);
        print("[JAWMS-CT] LOCKOUT applied=" ~ appliedTicks
            ~ " manaBefore=" ~ beforeLockout.currentMana
            ~ " manaAfter=" ~ afterLockout.currentMana
            ~ " remaining=" ~ afterLockout.remainingLockoutTicks
            ~ " continuousBefore=" ~ beforeLockout.continuousCastActive
            ~ " continuousAfter=" ~ afterLockout.continuousCastActive);
        print("[JAWMS-CT] END");
    }
});
