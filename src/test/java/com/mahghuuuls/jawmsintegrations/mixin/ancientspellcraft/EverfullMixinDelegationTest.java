package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EverfullMixinDelegationTest {

    private static final String MIXIN = "com/mahghuuuls/jawmsintegrations/mixin/ancientspellcraft/"
            + "MixinItemEverfullManaFlask";
    private static final String SERVICE = "com/mahghuuuls/jawmsintegrations/integration/"
            + "ancientspellcraft/EverfullManaService";
    private static final String PROXY = "com/mahghuuuls/jawmsintegrations/proxy/CommonProxy";

    @Test
    void exactHooksDelegateTickAndUseToTheCommonService() throws Exception {
        assertInvocation("jawmsIntegrations$replaceCarriedRegeneration", SERVICE,
                "tick", "(Lnet/minecraft/item/ItemStack;IJ)Z", 1);
        assertInvocation("jawmsIntegrations$replaceUse", SERVICE,
                "use", "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/util/EnumHand;Lnet/minecraft/item/ItemStack;I)"
                        + "Lnet/minecraft/util/ActionResult;", 1);
        assertInvocation("jawmsIntegrations$replaceCarriedRegeneration", PROXY,
                "isServerReplacementActive", "(Lnet/minecraft/util/ResourceLocation;)Z", 1);
        assertInvocation("jawmsIntegrations$replaceUse", PROXY,
                "isServerReplacementActive", "(Lnet/minecraft/util/ResourceLocation;)Z", 1);
    }

    private static void assertInvocation(String sourceMethod,
                                         String invokedOwner,
                                         String invokedMethod,
                                         String invokedDescriptor,
                                         int expected) throws Exception {
        Path classes = Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"));
        Path classFile = classes.resolve(MIXIN + ".class");
        AtomicInteger count = new AtomicInteger();
        try (InputStream input = Files.newInputStream(classFile)) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!sourceMethod.equals(name)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            if (invokedOwner.equals(owner) && invokedMethod.equals(name)
                                    && invokedDescriptor.equals(descriptor)) {
                                count.incrementAndGet();
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(expected, count.get(), sourceMethod + " must delegate exactly once");
    }
}
