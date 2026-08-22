package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityAttributeProjectionBytecodeTest {

    @Test
    void flatLockoutUsesBothDirectionalApiHelpersInsteadOfSignedSetter() throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream input = QualityAttributeProjection.class.getResourceAsStream(
                "/com/mahghuuuls/jawmsintegrations/integration/qualitytools/QualityAttributeProjection.class")) {
            if (input == null) throw new AssertionError("Missing compiled projector class");
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        MethodNode method = null;
        for (MethodNode candidate : node.methods) {
            if ("toContribution".equals(candidate.name)) method = candidate;
        }
        if (method == null) throw new AssertionError("Missing toContribution method");

        int increases = 0;
        int reductions = 0;
        int signed = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"com/mahghuuuls/jawms/api/ManaContribution$Builder".equals(call.owner)) continue;
            if ("flatLockoutIncreaseSeconds".equals(call.name)) increases++;
            if ("flatLockoutReductionSeconds".equals(call.name)) reductions++;
            if ("flatLockoutSeconds".equals(call.name)) signed++;
        }
        assertEquals(1, increases);
        assertEquals(1, reductions);
        assertEquals(0, signed);
    }
}
