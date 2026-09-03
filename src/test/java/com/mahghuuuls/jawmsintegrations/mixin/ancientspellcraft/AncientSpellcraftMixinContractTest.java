package com.mahghuuuls.jawmsintegrations.mixin.ancientspellcraft;

import com.mahghuuuls.jawmsintegrations.mixin.BytecodeContract;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AncientSpellcraftMixinContractTest {

    private static final String BAR_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;)Z";

    @Test
    void nativeDurabilityContractAcceptsAnUnchangedInheritanceChain() {
        ClassNode target = target("example/Intermediate");
        ResourceClassLoader loader = new ResourceClassLoader();
        loader.add(classBytes("example/Intermediate", "example/Artefact", 0));
        loader.add(classBytes("example/Artefact", "net/minecraft/item/Item", 0));

        assertDoesNotThrow(() -> AncientSpellcraftMixinContract
                .requireNativeDurabilityInheritance(loader, target));
    }

    @Test
    void nativeDurabilityContractRejectsInheritedOverrideAndFinalMethod() {
        for (int access : new int[]{Opcodes.ACC_PUBLIC,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL}) {
            ClassNode target = target("example/Intermediate");
            ResourceClassLoader loader = new ResourceClassLoader();
            loader.add(classBytes("example/Intermediate", "example/Artefact", access));
            loader.add(classBytes("example/Artefact", "net/minecraft/item/Item", 0));

            assertThrows(BytecodeContract.Violation.class,
                    () -> AncientSpellcraftMixinContract
                            .requireNativeDurabilityInheritance(loader, target));
        }
    }

    private static ClassNode target(String parent) {
        ClassNode target = new ClassNode();
        target.name = "example/Target";
        target.superName = parent;
        return target;
    }

    private static byte[] classBytes(String name, String parent, int barAccess) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, parent, null);
        if (barAccess != 0) {
            writer.visitMethod(barAccess, "showDurabilityBar", BAR_DESCRIPTOR, null, null)
                    .visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources = new HashMap<>();

        private ResourceClassLoader() {
            super(null);
        }

        private void add(byte[] bytes) {
            ClassNode node = new ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            resources.put(node.name + ".class", bytes);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] bytes = resources.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }
    }
}
