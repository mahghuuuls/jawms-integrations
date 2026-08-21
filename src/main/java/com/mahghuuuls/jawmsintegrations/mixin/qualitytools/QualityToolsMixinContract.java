package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.mixin.BytecodeContract;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;

/** Narrow transformation contract for minimum-or-newer Quality Tools artifacts. */
public final class QualityToolsMixinContract {
    private QualityToolsMixinContract() {
    }

    public static void validate(ClassLoader loader) throws IOException, BytecodeContract.Violation {
        ClassNode qualityType = BytecodeContract.read(loader,
                "com/tmtravlr/qualitytools/config/QualityType");
        MethodNode generate = BytecodeContract.requireMethod(qualityType, "generateQualityTag",
                "(Lnet/minecraft/item/ItemStack;Z)V");
        BytecodeContract.requireInvocationCount(generate,
                "com/tmtravlr/qualitytools/config/QualityType", new String[]{"chooseQualityEntry"},
                "(Z)Lcom/tmtravlr/qualitytools/config/QualityEntry;", Opcodes.INVOKEVIRTUAL, 1);

        ClassNode eventHandler = BytecodeContract.read(loader,
                "com/tmtravlr/qualitytools/CommonEventHandler");
        MethodNode update = BytecodeContract.requireMethod(eventHandler, "onLivingUpdate",
                "(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingUpdateEvent;)V");
        BytecodeContract.requireInvocationCount(update,
                "net/minecraft/entity/ai/attributes/AbstractAttributeMap",
                new String[]{"func_111148_a", "removeAttributeModifiers"},
                "(Lcom/google/common/collect/Multimap;)V", Opcodes.INVOKEVIRTUAL, 1);

        ClassNode reload = BytecodeContract.read(loader,
                "com/tmtravlr/qualitytools/config/CommandQualityToolsReload");
        MethodNode execute = BytecodeContract.requireMethod(reload,
                new String[]{"func_184881_a", "execute"},
                "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;"
                        + "[Ljava/lang/String;)V");
        BytecodeContract.requireInvocationCount(execute,
                "com/tmtravlr/qualitytools/config/ConfigLoader", new String[]{"reloadConfigs"},
                "()V", Opcodes.INVOKESTATIC, 1);
    }
}
