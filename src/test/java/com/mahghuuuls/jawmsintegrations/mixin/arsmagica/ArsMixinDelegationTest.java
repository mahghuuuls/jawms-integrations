package com.mahghuuuls.jawmsintegrations.mixin.arsmagica;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArsMixinDelegationTest {
    private static final String MIXIN =
            "com/mahghuuuls/jawmsintegrations/mixin/arsmagica/MixinEBWizardryCompatHandler.class";
    private static final String POLICY =
            "com/mahghuuuls/jawmsintegrations/integration/arsmagica/ArsWizardryPaymentPolicy";

    @Test
    void everyRedirectConsultsTheCentralPolicyExactlyOnce() throws IOException {
        ClassNode node = readMixin();
        int redirects = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.startsWith("jawmsintegrations$")) continue;
            redirects++;
            assertEquals(1, invocationCount(method, POLICY, "active"), method.name);
            assertEquals(1, invocationCount(method, POLICY, "ownsWizardryPayment"), method.name);
        }
        assertEquals(7, redirects);
    }

    @Test
    void inactiveBranchesRetainEveryNativeOperation() throws IOException {
        ClassNode node = readMixin();
        assertNativeCall(node, "jawmsintegrations$bypassArsAffordability",
                "am2/api/extensions/IEntityExtension", "hasEnoughMana");
        assertNativeCall(node, "jawmsintegrations$suppressFirstPaymentCancellation",
                "electroblob/wizardry/event/SpellCastEvent$Pre", "setCanceled");
        assertNativeCall(node, "jawmsintegrations$suppressSecondPaymentCancellation",
                "electroblob/wizardry/event/SpellCastEvent$Pre", "setCanceled");
        assertNativeCall(node, "jawmsintegrations$preserveFirstWizardryCost",
                "electroblob/wizardry/util/SpellModifiers", "set");
        assertNativeCall(node, "jawmsintegrations$preserveSecondWizardryCost",
                "electroblob/wizardry/util/SpellModifiers", "set");
        assertNativeCall(node, "jawmsintegrations$suppressArsManaDeduction",
                "am2/api/extensions/IEntityExtension", "deductMana");
        assertNativeCall(node, "jawmsintegrations$suppressArsBurnout",
                "am2/api/extensions/IEntityExtension", "setCurrentBurnout");
    }

    private static void assertNativeCall(ClassNode node, String methodName,
                                         String owner, String name) {
        MethodNode method = method(node, methodName);
        assertNotNull(method, methodName);
        assertEquals(1, invocationCount(method, owner, name), methodName);
    }

    private static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) if (name.equals(method.name)) return method;
        return null;
    }

    private static int invocationCount(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction.getOpcode() == Opcodes.INVOKEVIRTUAL
                    || instruction.getOpcode() == Opcodes.INVOKESTATIC
                    || instruction.getOpcode() == Opcodes.INVOKEINTERFACE) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) count++;
            }
        }
        return count;
    }

    private static ClassNode readMixin() throws IOException {
        try (InputStream input = ArsMixinDelegationTest.class.getClassLoader()
                .getResourceAsStream(MIXIN)) {
            assertNotNull(input, MIXIN);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
