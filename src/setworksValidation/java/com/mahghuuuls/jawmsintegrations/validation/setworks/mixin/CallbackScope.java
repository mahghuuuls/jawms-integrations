package com.mahghuuuls.jawmsintegrations.validation.setworks.mixin;

import com.mahghuuuls.jawmsintegrations.validation.setworks.SetworksReadinessFixture;
import com.mahghuuuls.setworks.api.WithdrawReason;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "com.mahghuuuls.jawmsintegrations.integration.setworks.SetworksGameplay", remap = false)
abstract class CallbackScope {
    @Inject(method = "apply", at = @At("HEAD"))
    private void observeApply(EntityPlayerMP player, String instance, Map<String, String> params, CallbackInfo ci) {
        SetworksReadinessFixture.observeCallback(player, "apply", instance);
    }

    @Inject(method = "withdraw", at = @At("HEAD"))
    private void observeWithdraw(EntityPlayerMP player, String instance, WithdrawReason reason, CallbackInfo ci) {
        SetworksReadinessFixture.observeCallback(player, "withdraw:" + reason.name(), instance);
    }

    @Inject(method = "afterGroup", at = @At("HEAD"))
    private void enterNativeGroup(EntityPlayerMP player, CallbackInfo ci) {
        SetworksReadinessFixture.enterCallback(player);
    }

    @Inject(method = "afterGroup", at = @At("RETURN"))
    private void exitNativeGroup(EntityPlayerMP player, CallbackInfo ci) {
        SetworksReadinessFixture.exitCallback();
        SetworksReadinessFixture.observeNativeGroup(player);
    }
}
