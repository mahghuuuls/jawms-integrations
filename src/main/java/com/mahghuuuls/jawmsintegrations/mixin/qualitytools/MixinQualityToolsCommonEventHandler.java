package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.integration.qualitytools.QualityToolsIntegration;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.tmtravlr.qualitytools.CommonEventHandler", remap = false)
public abstract class MixinQualityToolsCommonEventHandler {

    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/attributes/AbstractAttributeMap;"
                            + "removeAttributeModifiers(Lcom/google/common/collect/Multimap;)V",
                    shift = At.Shift.AFTER,
                    remap = true
            )
    )
    private static void jawmsintegrations$afterAttributeUpdate(
            LivingEvent.LivingUpdateEvent event,
            CallbackInfo callbackInfo) {
        QualityToolsIntegration.afterAttributeUpdate(event.getEntityLiving());
    }
}
