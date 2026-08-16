package com.mahghuuuls.jawmsintegrations.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DependencyContractTest {

    private static final String QUALITY_TOOLS_JAR = "jawmsintegrations.contract.qualityToolsJar";
    private static final String ANCIENT_SPELLCRAFT_JAR = "jawmsintegrations.contract.ancientSpellcraftJar";

    @Test
    void qualityToolsReleaseMatchesCandidateGenerationContract() throws IOException {
        Path jar = requiredJar(QUALITY_TOOLS_JAR);
        assertEquals("C979852CE107064F779A1D1A5260CAF7CAF6B1566DC7F7FA07CCF4EB80AA555A",
                sha256(jar));
        JarContract.assertMethod(jar,
                "com/tmtravlr/qualitytools/config/QualityType",
                "generateQualityTag",
                "(Lnet/minecraft/item/ItemStack;Z)V");
        JarContract.assertMethod(jar,
                "com/tmtravlr/qualitytools/config/QualityType",
                "chooseQualityEntry",
                "(Z)Lcom/tmtravlr/qualitytools/config/QualityEntry;");
        JarContract.assertField(jar,
                "com/tmtravlr/qualitytools/config/QualityType",
                "qualities",
                "[Lcom/tmtravlr/qualitytools/config/QualityEntry;");
        JarContract.assertMethodInvocationCount(jar,
                "com/tmtravlr/qualitytools/config/QualityType",
                "generateQualityTag",
                "(Lnet/minecraft/item/ItemStack;Z)V",
                "com/tmtravlr/qualitytools/config/QualityType",
                "chooseQualityEntry",
                "(Z)Lcom/tmtravlr/qualitytools/config/QualityEntry;",
                1);
        JarContract.assertMethod(jar,
                "com/tmtravlr/qualitytools/CommonEventHandler",
                "onLivingUpdate",
                "(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingUpdateEvent;)V");
        JarContract.assertMethodInvocationCount(jar,
                "com/tmtravlr/qualitytools/config/CommandQualityToolsReload",
                "func_184881_a",
                "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
                "com/tmtravlr/qualitytools/config/ConfigLoader",
                "reloadConfigs",
                "()V",
                1);
        JarContract.assertMethodInvocation(jar,
                "com/tmtravlr/qualitytools/CommonEventHandler",
                "onLivingUpdate",
                "(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingUpdateEvent;)V",
                "net/minecraft/entity/ai/attributes/AbstractAttributeMap",
                "func_111148_a",
                "(Lcom/google/common/collect/Multimap;)V");
    }

    @Test
    void ancientSpellcraftReleaseMatchesReplacementSeams() throws IOException {
        Path jar = requiredJar(ANCIENT_SPELLCRAFT_JAR);
        assertEquals("A47647AD039D1CBFEE94059C508800C4FA956B9A26B570B20C94C48A3961C215",
                sha256(jar));
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/handler/ASEventHandler",
                "onSpellCastPreEvent",
                "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "getMana",
                "(Lnet/minecraft/item/ItemStack;)I");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemEverfullManaFlask",
                "func_77659_a",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemRingManaTransfer",
                "onWornTick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V");
    }

    @Test
    void contractCheckFailsWhenDescriptorDrifts() {
        Path jar = requiredJar(QUALITY_TOOLS_JAR);
        assertThrows(AssertionError.class, () -> JarContract.assertMethod(jar,
                "com/tmtravlr/qualitytools/config/QualityType",
                "generateQualityTag",
                "(Lnet/minecraft/item/ItemStack;)V"));
    }

    private static Path requiredJar(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new AssertionError("Missing test system property " + propertyName);
        }
        Path path = Paths.get(value);
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Dependency contract JAR does not exist: " + path);
        }
        return path;
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02X", value & 0xFF));
        }
        return result.toString();
    }
}
