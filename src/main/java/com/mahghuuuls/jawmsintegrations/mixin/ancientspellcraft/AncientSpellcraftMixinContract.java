package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.mixin.BytecodeContract;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

/** Narrow transformation contract for minimum-or-newer Ancient Spellcraft artifacts. */
public final class AncientSpellcraftMixinContract {
    private AncientSpellcraftMixinContract() {
    }

    public static void validate(ClassLoader loader) throws IOException, BytecodeContract.Violation {
        ClassNode handler = BytecodeContract.read(loader,
                "com/windanesz/ancientspellcraft/handler/ASEventHandler");
        MethodNode cast = BytecodeContract.requireMethod(handler, "onSpellCastPreEvent",
                "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V");
        BytecodeContract.requireInvocationCount(cast,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact", new String[]{"getMana"},
                "(Lnet/minecraft/item/ItemStack;)I", Opcodes.INVOKEVIRTUAL, 2);
        BytecodeContract.requireFieldCount(cast,
                "com/windanesz/ancientspellcraft/registry/ASItems", "ring_mana_cost",
                "Lnet/minecraft/item/Item;", Opcodes.GETSTATIC, 1);

        ClassNode artefact = BytecodeContract.read(loader,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact");
        BytecodeContract.requireMethod(artefact, "onApplyButtonPressed",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Slot;"
                        + "Lnet/minecraft/inventory/Slot;Lnet/minecraft/inventory/Slot;"
                        + "[Lnet/minecraft/inventory/Slot;)Z");
        BytecodeContract.requireMethod(artefact, new String[]{"func_77624_a", "addInformation"},
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Ljava/util/List;"
                        + "Lnet/minecraft/client/util/ITooltipFlag;)V");
        BytecodeContract.requireNoMethod(artefact,
                new String[]{"showDurabilityBar"},
                "(Lnet/minecraft/item/ItemStack;)Z");
        requireNativeDurabilityInheritance(loader, artefact);

        ClassNode items = BytecodeContract.read(loader,
                "com/windanesz/ancientspellcraft/registry/ASItems");
        MethodNode register = BytecodeContract.requireMethod(items, "register",
                "(Lnet/minecraftforge/event/RegistryEvent$Register;)V");
        String manaArtefact = "com/windanesz/ancientspellcraft/item/ItemManaArtefact";
        BytecodeContract.requireConstructionAfterString(register,
                "ring_mana_lesser", manaArtefact);
        BytecodeContract.requireConstructionAfterString(register,
                "ring_mana_greater", manaArtefact);
        BytecodeContract.requireConstructionAfterString(register,
                "charm_majestic_mana", manaArtefact);

        ClassNode everfull = BytecodeContract.read(loader,
                "com/windanesz/ancientspellcraft/item/ItemEverfullManaFlask");
        BytecodeContract.requireMethod(everfull, new String[]{"func_77663_a", "onUpdate"},
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/entity/Entity;IZ)V");
        BytecodeContract.requireMethod(everfull, new String[]{"func_77659_a", "onItemRightClick"},
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;");
        BytecodeContract.requireMethod(everfull, new String[]{"func_77624_a", "addInformation"},
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Ljava/util/List;"
                        + "Lnet/minecraft/client/util/ITooltipFlag;)V");

        ClassNode ring = BytecodeContract.read(loader,
                "com/windanesz/ancientspellcraft/item/ItemRingManaTransfer");
        BytecodeContract.requireMethod(ring, "onWornTick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V");
    }

    static void requireNativeDurabilityInheritance(ClassLoader loader, ClassNode target)
            throws IOException, BytecodeContract.Violation {
        String parentName = target.superName;
        while (parentName != null && !"net/minecraft/item/Item".equals(parentName)) {
            ClassNode parent = BytecodeContract.read(loader, parentName);
            BytecodeContract.requireNoMethod(parent,
                    new String[]{"showDurabilityBar"},
                    "(Lnet/minecraft/item/ItemStack;)Z");
            parentName = parent.superName;
        }
        if (!"net/minecraft/item/Item".equals(parentName)) {
            throw new BytecodeContract.Violation(
                    "ItemManaArtefact hierarchy does not reach net.minecraft.item.Item");
        }
    }
}
