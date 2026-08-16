package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.DagorimFlaskService;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.windanesz.ancientspellcraft.item.ItemRingManaTransfer", remap = false)
public abstract class MixinItemRingManaTransfer {

    @Inject(method = "onWornTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void jawmsintegrations$replaceWornTick(ItemStack stack,
                                                   EntityLivingBase wearer,
                                                   CallbackInfo callback) {
        if (wearer.world.isRemote) {
            if (JawmsIntegrationsMod.PROXY.isServerReplacementActive(
                    AncientReplacement.RING_OF_DAGORIM.getRegistryName())) {
                callback.cancel();
            }
            return;
        }
        DagorimFlaskService service = DagorimFlaskService.active();
        if (service.handles(stack)) {
            service.onWornTick(stack, wearer);
            callback.cancel();
        }
    }
}
