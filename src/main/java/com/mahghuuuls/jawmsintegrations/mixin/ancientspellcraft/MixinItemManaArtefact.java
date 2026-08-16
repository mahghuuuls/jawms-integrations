package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents workbench recharge without mutating preserved legacy charge. */
@Mixin(targets = "com.windanesz.ancientspellcraft.item.ItemManaArtefact", remap = false)
public abstract class MixinItemManaArtefact {

    @Inject(method = "onApplyButtonPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void jawmsIntegrations$suppressRecharge(EntityPlayer player,
                                                     Slot itemSlot,
                                                     Slot crystalSlot,
                                                     Slot spellSlot,
                                                     Slot[] upgradeSlots,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (AncientReplacementPolicy.active().isStorageReplacement(itemSlot.getStack())) {
            callback.setReturnValue(false);
        }
    }
}
