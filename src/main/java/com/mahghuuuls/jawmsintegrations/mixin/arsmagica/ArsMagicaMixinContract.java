package com.mahghuuuls.jawmsintegrations.mixin.arsmagica;

import com.mahghuuuls.jawmsintegrations.mixin.BytecodeContract;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

/** Exact released call-site contract for the Ars Wizardry payment bridge. */
public final class ArsMagicaMixinContract {
    private static final String HANDLER =
            "am2/common/compat/electroblob/EBWizardryCompatHandler";
    private static final String PRE =
            "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V";
    private static final String POST =
            "(Lelectroblob/wizardry/event/SpellCastEvent$Post;)V";

    private ArsMagicaMixinContract() {
    }

    public static void validate(ClassLoader loader) throws IOException, BytecodeContract.Violation {
        ClassNode handler = BytecodeContract.read(loader, HANDLER);
        MethodNode pre = BytecodeContract.requireMethod(handler, "onEBWizSpellCastPre", PRE);
        BytecodeContract.requireInvocationCount(pre,
                "am2/api/extensions/IEntityExtension", new String[]{"hasEnoughMana"},
                "(F)Z", Opcodes.INVOKEINTERFACE, 2);
        BytecodeContract.requireInvocationCount(pre,
                "electroblob/wizardry/event/SpellCastEvent$Pre", new String[]{"setCanceled"},
                "(Z)V", Opcodes.INVOKEVIRTUAL, 3);
        BytecodeContract.requireInvocationCount(pre,
                "electroblob/wizardry/util/SpellModifiers", new String[]{"set"},
                "(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;",
                Opcodes.INVOKEVIRTUAL, 3);
        BytecodeContract.requireInvocationCount(pre,
                "java/util/Map", new String[]{"put"},
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                Opcodes.INVOKEINTERFACE, 2);
        requireIncreasing(pre,
                invocation(pre, "electroblob/wizardry/event/SpellCastEvent$Pre",
                        "setCanceled", "(Z)V", 0),
                invocation(pre, "electroblob/wizardry/util/SpellModifiers", "set",
                        "(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;", 0),
                invocation(pre, "am2/api/extensions/IEntityExtension",
                        "hasEnoughMana", "(F)Z", 0),
                invocation(pre, "electroblob/wizardry/event/SpellCastEvent$Pre",
                        "setCanceled", "(Z)V", 1),
                invocation(pre, "electroblob/wizardry/util/SpellModifiers", "set",
                        "(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;", 1),
                invocation(pre, "java/util/Map", "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", 0),
                invocation(pre, "am2/api/extensions/IEntityExtension",
                        "hasEnoughMana", "(F)Z", 1),
                invocation(pre, "electroblob/wizardry/event/SpellCastEvent$Pre",
                        "setCanceled", "(Z)V", 2),
                invocation(pre, "electroblob/wizardry/util/SpellModifiers", "set",
                        "(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;", 2),
                invocation(pre, "java/util/Map", "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", 1));

        MethodNode post = BytecodeContract.requireMethod(handler, "onEBWizSpellCastPost", POST);
        BytecodeContract.requireInvocationCount(post,
                "java/util/Map", new String[]{"remove"},
                "(Ljava/lang/Object;)Ljava/lang/Object;", Opcodes.INVOKEINTERFACE, 1);
        BytecodeContract.requireInvocationCount(post,
                "am2/api/extensions/IEntityExtension", new String[]{"deductMana"},
                "(F)V", Opcodes.INVOKEINTERFACE, 1);
        BytecodeContract.requireInvocationCount(post,
                "am2/api/extensions/IEntityExtension", new String[]{"setCurrentBurnout"},
                "(F)V", Opcodes.INVOKEINTERFACE, 1);
        BytecodeContract.requireInvocationCount(post,
                "am2/api/extensions/IEntityExtension", new String[]{"addMagicXP"},
                "(F)V", Opcodes.INVOKEINTERFACE, 1);
        BytecodeContract.requireInvocationCount(post,
                "am2/common/extensions/AffinityData", new String[]{"incrementAffinity"},
                "(Lam2/api/affinity/Affinity;F)V", Opcodes.INVOKEVIRTUAL, 1);
        requireIncreasing(post,
                invocation(post, "java/util/Map", "remove",
                        "(Ljava/lang/Object;)Ljava/lang/Object;", 0),
                invocation(post, "am2/api/extensions/IEntityExtension",
                        "deductMana", "(F)V", 0),
                invocation(post, "am2/api/extensions/IEntityExtension",
                        "setCurrentBurnout", "(F)V", 0),
                invocation(post, "am2/api/extensions/IEntityExtension",
                        "addMagicXP", "(F)V", 0),
                invocation(post, "am2/common/extensions/AffinityData",
                        "incrementAffinity", "(Lam2/api/affinity/Affinity;F)V", 0));
    }

    private static int invocation(MethodNode method, String owner, String name,
                                  String descriptor, int ordinal)
            throws BytecodeContract.Violation {
        int found = 0;
        int index = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext(), index++) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)
                        && descriptor.equals(call.desc)) {
                    if (found == ordinal) return index;
                    found++;
                }
            }
        }
        throw new BytecodeContract.Violation("missing ordinal " + ordinal + " of "
                + owner.replace('/', '.') + "." + name + descriptor
                + " in " + method.name + method.desc);
    }

    private static void requireIncreasing(MethodNode method, int... indices)
            throws BytecodeContract.Violation {
        for (int i = 1; i < indices.length; i++) {
            if (indices[i] <= indices[i - 1]) {
                throw new BytecodeContract.Violation(
                        "payment/progression call order drifted in "
                                + method.name + method.desc);
            }
        }
    }
}
