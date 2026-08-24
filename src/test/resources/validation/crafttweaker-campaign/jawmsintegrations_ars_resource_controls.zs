import crafttweaker.event.PlayerRightClickItemEvent;
import mods.jawmsintegrations.Mana;

// Development-only IMP-013 controls. These make the JAWMS side of the
// cross-resource matrix exact without adding a production mutation command.
events.onPlayerRightClickItem(function(event as PlayerRightClickItemEvent) {
    if (!event.world.remote) {
        if (event.item.definition.id == "minecraft:nether_star") {
            event.cancel();
            val result = Mana.setCurrentMana(event.player, 60);
            print("[JAWMS-ARS-VALIDATION] SET_FULL success=" ~ result.successful
                ~ " requested=" ~ result.requestedAmount ~ " actual=" ~ result.actualAmount
                ~ " old=" ~ result.oldState.currentMana ~ " new=" ~ result.newState.currentMana);
        } else if (event.item.definition.id == "minecraft:redstone_torch") {
            event.cancel();
            val result = Mana.setCurrentMana(event.player, 0);
            print("[JAWMS-ARS-VALIDATION] SET_EMPTY success=" ~ result.successful
                ~ " requested=" ~ result.requestedAmount ~ " actual=" ~ result.actualAmount
                ~ " old=" ~ result.oldState.currentMana ~ " new=" ~ result.newState.currentMana);
        }
    }
});
