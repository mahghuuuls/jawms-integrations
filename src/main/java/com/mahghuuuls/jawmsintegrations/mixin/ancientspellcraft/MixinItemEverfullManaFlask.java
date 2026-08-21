package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.JawmsIntegrationsMod;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.EverfullManaService;
import electroblob.wizardry.item.IManaStoringItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Contract-gated adapter that replaces native Everfull ticking and use with the common service. */
@Mixin(targets = "com.windanesz.ancientspellcraft.item.ItemEverfullManaFlask", remap = false)
public abstract class MixinItemEverfullManaFlask {

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true,
            require = 1, remap = true)
    private void jawmsIntegrations$replaceCarriedRegeneration(ItemStack stack,
                                                              World world,
                                                              Entity entity,
                                                              int itemSlot,
                                                              boolean isSelected,
                                                              CallbackInfo callback) {
        EverfullManaService service = EverfullManaService.active();
        if (world.isRemote) {
            if (JawmsIntegrationsMod.PROXY.isServerReplacementActive(
                    stack.getItem().getRegistryName())) {
                callback.cancel();
            }
            return;
        }
        if (!service.handles(stack)) {
            return;
        }
        if (entity instanceof EntityPlayer) {
            int legacyMana = ((IManaStoringItem) (Object) this).getMana(stack);
            if (service.tick(stack, legacyMana, world.getTotalWorldTime())) {
                ((EntityPlayer) entity).inventory.markDirty();
            }
        }
        callback.cancel();
    }

    @Inject(method = "onItemRightClick", at = @At("HEAD"), cancellable = true,
            require = 1, remap = true)
    private void jawmsIntegrations$replaceUse(World world,
                                              EntityPlayer player,
                                              EnumHand hand,
                                              CallbackInfoReturnable<ActionResult<ItemStack>> callback) {
        ItemStack stack = player.getHeldItem(hand);
        EverfullManaService service = EverfullManaService.active();
        if (world.isRemote) {
            if (JawmsIntegrationsMod.PROXY.isServerReplacementActive(
                    stack.getItem().getRegistryName())) {
                callback.setReturnValue(new ActionResult<>(EnumActionResult.SUCCESS, stack));
            }
            return;
        }
        if (!service.handles(stack)) {
            return;
        }
        int legacyMana = ((IManaStoringItem) (Object) this).getMana(stack);
        callback.setReturnValue(service.use(world, player, hand, stack, legacyMana));
    }
}
