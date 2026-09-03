package com.mahghuuuls.jawmsintegrations.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BytecodeContractTest {

    @Test
    void noMethodContractRejectsAConflictingDeclaredMethod() {
        ClassNode target = new ClassNode();
        target.name = "example/Target";

        assertDoesNotThrow(() -> BytecodeContract.requireNoMethod(target,
                new String[]{"showDurabilityBar"},
                "(Lnet/minecraft/item/ItemStack;)Z"));

        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC,
                "showDurabilityBar",
                "(Lnet/minecraft/item/ItemStack;)Z",
                null,
                null));

        assertThrows(BytecodeContract.Violation.class,
                () -> BytecodeContract.requireNoMethod(target,
                        new String[]{"showDurabilityBar"},
                        "(Lnet/minecraft/item/ItemStack;)Z"));
    }

    @Test
    void stringConstructionContractRequiresTheNamedConstructionAtItsRegistryEntry() {
        MethodNode register = new MethodNode();
        register.name = "register";
        register.desc = "()V";
        register.instructions.add(new LdcInsnNode("ring_mana_lesser"));
        register.instructions.add(new LdcInsnNode("ancientspellcraft"));
        register.instructions.add(new TypeInsnNode(Opcodes.NEW, "example/ExpectedItem"));
        register.instructions.add(new InsnNode(Opcodes.DUP));

        assertDoesNotThrow(() -> BytecodeContract.requireConstructionAfterString(
                register, "ring_mana_lesser", "example/ExpectedItem"));
        assertThrows(BytecodeContract.Violation.class,
                () -> BytecodeContract.requireConstructionAfterString(
                        register, "ring_mana_lesser", "example/OtherItem"));

        MethodNode wrapped = new MethodNode();
        wrapped.name = "register";
        wrapped.desc = "()V";
        wrapped.instructions.add(new LdcInsnNode("ring_mana_lesser"));
        wrapped.instructions.add(new TypeInsnNode(Opcodes.NEW, "example/WrongOuterItem"));
        wrapped.instructions.add(new InsnNode(Opcodes.DUP));
        wrapped.instructions.add(new TypeInsnNode(Opcodes.NEW, "example/ExpectedItem"));

        assertThrows(BytecodeContract.Violation.class,
                () -> BytecodeContract.requireConstructionAfterString(
                        wrapped, "ring_mana_lesser", "example/ExpectedItem"));
    }
}
