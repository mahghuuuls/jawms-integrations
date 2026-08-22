package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DagorimMixinDelegationTest {
    private static final String MIXIN = "com/mahghuuuls/jawmsintegrations/mixin/ancientspellcraft/"
            + "MixinItemRingManaTransfer";
    private static final String SERVICE = "com/mahghuuuls/jawmsintegrations/integration/"
            + "ancientspellcraft/DagorimFlaskService";

    @Test void hookDelegatesServerBehaviorAndUsesSnapshotBackedClientSuppression() throws Exception {
        assertInvocation(MIXIN, "jawmsintegrations$replaceWornTick", SERVICE,
                "handles", "(Lnet/minecraft/item/ItemStack;)Z", 1);
        assertInvocation(MIXIN, "jawmsintegrations$replaceWornTick", SERVICE,
                "onWornTick", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z", 1);
        assertInvocation(MIXIN, "jawmsintegrations$replaceWornTick",
                "com/mahghuuuls/jawmsintegrations/proxy/CommonProxy",
                "isServerReplacementActive", "(Lnet/minecraft/util/ResourceLocation;)Z", 1);
    }

    @Test void productionUsesConfiguredFlaskSnapshotAndNeverReadsWizardryCapacityFields()
            throws Exception {
        Path classFile = classes().resolve(SERVICE + ".class");
        AtomicInteger sizeReads = new AtomicInteger();
        AtomicInteger capacityReads = new AtomicInteger();
        AtomicInteger snapshotApiReferences = new AtomicInteger();
        try (InputStream input = Files.newInputStream(classFile)) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitFieldInsn(int opcode, String owner, String name,
                                                             String descriptor) {
                            if (opcode == Opcodes.GETFIELD
                                    && owner.equals("electroblob/wizardry/item/ItemManaFlask")
                                    && name.equals("size")) sizeReads.incrementAndGet();
                            if (opcode == Opcodes.GETFIELD
                                    && owner.equals("electroblob/wizardry/item/ItemManaFlask$Size")
                                    && name.equals("capacity")) capacityReads.incrementAndGet();
                        }
                        @Override public void visitInvokeDynamicInsn(String name,
                                                                    String descriptor,
                                                                    Handle bootstrap,
                                                                    Object... arguments) {
                            for (Object argument : arguments) {
                                if (!(argument instanceof Handle)) continue;
                                Handle handle = (Handle) argument;
                                if (handle.getOwner().equals("com/mahghuuuls/jawms/api/ManaApi")
                                        && handle.getName().equals("getConfiguredFlaskSnapshot")
                                        && handle.getDesc().equals(
                                        "()Lcom/mahghuuuls/jawms/api/ConfiguredFlaskSnapshot;")) {
                                    snapshotApiReferences.incrementAndGet();
                                }
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(1, snapshotApiReferences.get());
        assertEquals(0, sizeReads.get());
        assertEquals(0, capacityReads.get());
    }

    private static void assertInvocation(String className, String sourceMethod, String owner,
                                         String method, String descriptor, int expected) throws Exception {
        AtomicInteger count = new AtomicInteger();
        try (InputStream input = Files.newInputStream(classes().resolve(className + ".class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc,
                                                           String signature, String[] exceptions) {
                    if (!sourceMethod.equals(name)) return null;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitMethodInsn(int opcode, String invokedOwner,
                                                             String invokedName, String invokedDesc,
                                                             boolean isInterface) {
                            if (owner.equals(invokedOwner) && method.equals(invokedName)
                                    && descriptor.equals(invokedDesc)) count.incrementAndGet();
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(expected, count.get());
    }

    private static Path classes() {
        return Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"));
    }
}
