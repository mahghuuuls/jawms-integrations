package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Suppresses wrapped native descriptions before JAWMS replacement text is appended. */
@Mixin(targets = "electroblob.wizardry.item.ItemArtefact", remap = false)
public abstract class MixinItemArtefactClient {

    @Inject(method = "addInformation", at = @At("HEAD"), cancellable = true,
            require = 1, remap = true)
    private void jawmsIntegrations$suppressReplacedDescription(ItemStack stack,
                                                               World world,
                                                               List<String> tooltip,
                                                               ITooltipFlag flag,
                                                               CallbackInfo callback) {
        AncientReplacement replacement = AncientReplacement.forRegistryName(
                stack.getItem().getRegistryName());
        if ((replacement == AncientReplacement.CRYSTAL_RING
                || replacement == AncientReplacement.RING_OF_DAGORIM)
                && ClientIntegrationPresentationCache.isAncientReplacementActive(
                        stack.getItem().getRegistryName())) {
            callback.cancel();
        }
    }
}
