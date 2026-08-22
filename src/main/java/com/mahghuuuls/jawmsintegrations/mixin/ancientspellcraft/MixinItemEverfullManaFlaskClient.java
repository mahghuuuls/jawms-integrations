package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Suppresses the native 0-1400 tooltip only after the server enables the replacement. */
@Mixin(targets = "com.windanesz.ancientspellcraft.item.ItemEverfullManaFlask", remap = false)
public abstract class MixinItemEverfullManaFlaskClient {

    @Inject(method = "addInformation", at = @At("HEAD"), cancellable = true,
            require = 1, remap = true)
    private void jawmsIntegrations$suppressLegacyTooltip(ItemStack stack,
                                                         World world,
                                                         List<String> tooltip,
                                                         ITooltipFlag flag,
                                                         CallbackInfo callback) {
        if (ClientIntegrationPresentationCache.isAncientReplacementActive(
                stack.getItem().getRegistryName())) {
            callback.cancel();
        }
    }
}
