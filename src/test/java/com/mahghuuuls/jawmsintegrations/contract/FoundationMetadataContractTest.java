package com.mahghuuuls.jawmsintegrations.contract;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FoundationMetadataContractTest {

    private static final String MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;";

    @Test
    void forgeMetadataRequiresJawmsOneAndOrdersBeforeCraftTweaker() throws IOException {
        Path classes = Paths.get(System.getProperty("jawmsintegrations.mainClassesDir"));
        Path modClass = classes.resolve(
                "com/mahghuuuls/jawmsintegrations/JawmsIntegrationsMod.class");
        Map<String, Object> values = new HashMap<>();

        try (InputStream input = Files.newInputStream(modClass)) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!MOD_ANNOTATION.equals(descriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM5) {
                        @Override
                        public void visit(String name, Object value) {
                            values.put(name, value);
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertEquals("[1.12.2]", values.get("acceptedMinecraftVersions"));
        assertEquals("required-after:jawms@[1.1.0,);before:crafttweaker",
                values.get("dependencies"));
        try (InputStream metadata = FoundationMetadataContractTest.class.getClassLoader()
                .getResourceAsStream("mcmod.info")) {
            assertTrue(metadata != null, "Processed mcmod.info must be present");
            String contents = new String(readAll(metadata), StandardCharsets.UTF_8);
            assertTrue(contents.contains("\"version\": \"1.1.1\""));
            assertTrue(contents.contains("\"requiredMods\": [\"jawms@[1.1.0,)\"]"));
            assertTrue(contents.contains("Quality Tools"));
            assertTrue(contents.contains("Ancient Spellcraft"));
            assertTrue(contents.contains("CraftTweaker"));
            assertFalse(contents.toLowerCase().contains("ars magica"));
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int length;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }
}
