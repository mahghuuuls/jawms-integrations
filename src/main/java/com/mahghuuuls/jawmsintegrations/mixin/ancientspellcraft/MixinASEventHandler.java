package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;
import com.windanesz.ancientspellcraft.item.ItemManaArtefact;
import com.windanesz.ancientspellcraft.registry.ASItems;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Contract-validated legacy-payment and Crystal cost seams; policy owns every decision. */
@Mixin(targets = "com.windanesz.ancientspellcraft.handler.ASEventHandler", remap = false)
public abstract class MixinASEventHandler {

    @Redirect(
            method = "onSpellCastPreEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/windanesz/ancientspellcraft/item/ItemManaArtefact;"
                            + "getMana(Lnet/minecraft/item/ItemStack;)I"
            ),
            require = 2
    )
    private static int jawmsIntegrations$suppressStoragePayment(
            ItemManaArtefact item, ItemStack stack) {
        if (AncientReplacementPolicy.active().isStorageReplacement(stack)) {
            return -1;
        }
        return item.getMana(stack);
    }

    @Redirect(
            method = "onSpellCastPreEvent",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/windanesz/ancientspellcraft/registry/ASItems;"
                            + "ring_mana_cost:Lnet/minecraft/item/Item;",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 1
    )
    private static Item jawmsIntegrations$suppressCrystalCostModifier() {
        return AncientReplacementPolicy.active().isStaticEnabled(AncientReplacement.CRYSTAL_RING)
                ? Items.AIR : ASItems.ring_mana_cost;
    }
}
