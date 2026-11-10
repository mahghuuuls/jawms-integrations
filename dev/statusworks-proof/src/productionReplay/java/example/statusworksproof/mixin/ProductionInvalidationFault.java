package example.statusworksproof.mixin;

import example.statusworksproof.NativeProof;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(targets = "com.mahghuuuls.jawmsintegrations.integration.statusworks.StatusworksGameplay$GroupType", remap = false)
public abstract class ProductionInvalidationFault {
    @Shadow @Final private String id;

    @Inject(method = "invalidate", at = @At("HEAD"), cancellable = true, require = 1)
    private void failOnlyArmedNativeInvalidation(EntityLivingBase entity, CallbackInfoReturnable<Boolean> callback) {
        if (!NativeProof.allowProductionInvalidation(id, entity)) callback.setReturnValue(false);
    }

    @Inject(method = "settled", at = @At("RETURN"), require = 1)
    private void observeSettled(EntityLivingBase entity, Map<String, Double> values, CallbackInfo callback) {
        NativeProof.observeProductionSettlement(id, entity, values);
    }
}
