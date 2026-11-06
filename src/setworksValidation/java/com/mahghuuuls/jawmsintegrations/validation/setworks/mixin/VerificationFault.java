package com.mahghuuuls.jawmsintegrations.validation.setworks.mixin;

import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import com.mahghuuuls.jawmsintegrations.validation.setworks.SetworksReadinessFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.mahghuuuls.jawmsintegrations.integration.setworks.SetworksPlayerGroup", remap = false)
abstract class VerificationFault {
    @Inject(method = "verify", at = @At("HEAD"))
    private void failSelectedGroup(CallbackInfoReturnable<ManaStatProjection.Result> ci) {
        if (SetworksReadinessFixture.failVerification()) throw new IllegalStateException("Injected verification fault");
    }
}
