package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hides the legacy charge bar while a server-authorized storage replacement is active. */
@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @Redirect(
            method = "renderItemOverlayIntoGUI",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/Item;showDurabilityBar"
                            + "(Lnet/minecraft/item/ItemStack;)Z",
                    remap = false
            ),
            require = 1
    )
    private boolean jawmsIntegrations$hideLegacyChargeBar(Item item, ItemStack stack) {
        AncientReplacement replacement = AncientReplacement.forRegistryName(item.getRegistryName());
        if (AncientReplacementPolicy.isStorage(replacement)
                && ClientIntegrationPresentationCache.isAncientReplacementActive(
                        item.getRegistryName())) {
            return false;
        }
        return item.showDurabilityBar(stack);
    }
}
