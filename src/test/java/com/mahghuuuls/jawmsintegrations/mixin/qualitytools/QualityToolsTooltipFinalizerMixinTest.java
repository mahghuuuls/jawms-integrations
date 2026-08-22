package com.mahghuuuls.jawmsintegrations.mixin.qualitytools;

import com.mahghuuuls.jawmsintegrations.client.ClientIntegrationPresentationCache;
import com.mahghuuuls.jawmsintegrations.client.QualityToolsTooltipFinalizer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityToolsTooltipFinalizerMixinTest {

    @Test
    void clientMixinTargetsItemStackReturnAtOrderElevenHundred() throws IOException {
        ClassNode node = readClass(MixinItemStackTooltipFinalizer.class);
        AnnotationNode mixin = annotation(node.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;");
        @SuppressWarnings("unchecked")
        List<Type> targets = (List<Type>) value(mixin, "value");
        assertEquals("Lnet/minecraft/item/ItemStack;", targets.get(0).getDescriptor());

        MethodNode hook = null;
        for (MethodNode candidate : node.methods) {
            if (candidate.name.contains("finalizeQualityToolsBlock")) hook = candidate;
        }
        if (hook == null) throw new AssertionError("Missing tooltip finalizer hook");
        AnnotationNode inject = annotation(hook.visibleAnnotations, hook.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        @SuppressWarnings("unchecked")
        List<String> methods = (List<String>) value(inject, "method");
        assertEquals(1, methods.size());
        assertEquals("getTooltip(Lnet/minecraft/entity/player/EntityPlayer;"
                + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;", methods.get(0));
        assertEquals(1100, value(inject, "order"));
        assertEquals(1, value(inject, "require"));
        assertEquals(1, value(inject, "expect"));
        assertEquals(1, value(inject, "allow"));
        @SuppressWarnings("unchecked")
        List<AnnotationNode> points = (List<AnnotationNode>) value(inject, "at");
        assertEquals("RETURN", value(points.get(0), "value"));
    }

    @Test
    void qualityToolsMixinConfigKeepsFinalizerClientOnly() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/mixins.jawmsintegrations.qualitytools.json")) {
            if (input == null) throw new AssertionError("Missing Quality Tools Mixin config");
            String json = new String(readAll(input), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"client\""));
            assertTrue(json.contains("\"MixinItemStackTooltipFinalizer\""));
            int mixinsStart = json.indexOf("\"mixins\"");
            int clientStart = json.indexOf("\"client\"");
            assertTrue(json.indexOf("MixinItemStackTooltipFinalizer", mixinsStart) > clientStart);
        }
    }

    @Test
    void hookConsultsAcceptedStateAndDelegatesExactlyOnce() throws IOException {
        ClassNode node = readClass(MixinItemStackTooltipFinalizer.class);
        MethodNode hook = null;
        for (MethodNode candidate : node.methods) {
            if (candidate.name.contains("finalizeQualityToolsBlock")) hook = candidate;
        }
        if (hook == null) throw new AssertionError("Missing compiled finalizer hook");
        int stateCalls = 0;
        int finalizerCalls = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = hook.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(ClientIntegrationPresentationCache.class.getName()
                    .replace('.', '/')) && call.name.equals("isQualityToolsActive")) stateCalls++;
            if (call.owner.equals(QualityToolsTooltipFinalizer.class.getName()
                    .replace('.', '/')) && call.name.equals("finalizeTooltip")) finalizerCalls++;
        }
        assertEquals(1, stateCalls);
        assertEquals(1, finalizerCalls);
    }

    private static ClassNode readClass(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        ClassNode node = new ClassNode();
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Missing compiled class " + resource);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (descriptor.equals(annotation.desc)) return annotation;
            }
        }
        throw new AssertionError("Missing annotation " + descriptor);
    }

    private static AnnotationNode annotation(List<AnnotationNode> first,
                                             List<AnnotationNode> second,
                                             String descriptor) {
        if (first != null) {
            for (AnnotationNode annotation : first) {
                if (descriptor.equals(annotation.desc)) return annotation;
            }
        }
        return annotation(second, descriptor);
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) return annotation.values.get(index + 1);
        }
        throw new AssertionError("Missing annotation value " + name);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
