package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.client.QualityToolsTooltipFinalizer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Runs after JAWMS's default-order final tooltip assembler. */
@Mixin(ItemStack.class)
public abstract class MixinItemStackTooltipFinalizer {

    @Inject(
            method = "getTooltip(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            require = 1,
            expect = 1,
            allow = 1,
            order = 1100
    )
    private void jawmsIntegrations$finalizeQualityToolsBlock(
            EntityPlayer player,
            ITooltipFlag flags,
            CallbackInfoReturnable<List<String>> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        QualityToolsTooltipFinalizer.finalizeTooltip(
                ClientIntegrationPresentationCache.isQualityToolsActive(),
                stack.getSubCompound("Quality"),
                callback.getReturnValue(),
                I18n.format("info.quality.name"));
    }
}
