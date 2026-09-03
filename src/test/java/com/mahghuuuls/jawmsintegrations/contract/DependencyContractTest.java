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

    private static final String JAWMS_JAR = "jawmsintegrations.contract.jawmsJar";
    private static final String WIZARDRY_JAR = "jawmsintegrations.contract.wizardryJar";
    private static final String QUALITY_TOOLS_JAR = "jawmsintegrations.contract.qualityToolsJar";
    private static final String ANCIENT_SPELLCRAFT_JAR = "jawmsintegrations.contract.ancientSpellcraftJar";
    private static final String CRAFTTWEAKER_JAR = "jawmsintegrations.contract.craftTweakerJar";

    @Test
    void jawmsReleaseMatchesRequiredApiFoundation() throws IOException {
        Path jar = requiredJar(JAWMS_JAR);
        assertEquals("3BB68BCEA775B4E42534865C2E788A01ECB363A0AD6247F540E781DBB79FED98",
                sha256(jar));
        JarContract.assertField(jar,
                "com/mahghuuuls/jawms/api/ManaApiVersion", "CURRENT", "Ljava/lang/String;");
        JarContract.assertMethod(jar,
                "com/mahghuuuls/jawms/api/IManaService",
                "startRegenerationLockout",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/ResourceLocation;)J");
        JarContract.assertMethodInvocationCount(jar,
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "startRegenerationLockout",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/ResourceLocation;)J",
                "com/mahghuuuls/jawms/internal/mana/ManaState",
                "getEffectiveLockoutTicks", "()J", 1);
        JarContract.assertMethodInvocationCount(jar,
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "startRegenerationLockout",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/ResourceLocation;)J",
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "transition",
                "(Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lcom/mahghuuuls/jawms/internal/mana/ManaState;"
                        + "Lnet/minecraft/util/ResourceLocation;ZLjava/lang/Runnable;)V", 1);
        JarContract.assertMethodInvocationCount(jar,
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "startRegenerationLockout",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/ResourceLocation;)J",
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "setCurrentMana",
                "(Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/ResourceLocation;)"
                        + "Lcom/mahghuuuls/jawms/api/ManaMutationResult;", 0);
        JarContract.assertMethodInvocationCount(jar,
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "startRegenerationLockout",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/ResourceLocation;)J",
                "com/mahghuuuls/jawms/internal/mana/ManaService",
                "consumeMana",
                "(Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/ResourceLocation;)"
                        + "Lcom/mahghuuuls/jawms/api/ManaMutationResult;", 0);
    }

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
        Path wizardry = requiredJar(WIZARDRY_JAR);
        assertEquals("A47647AD039D1CBFEE94059C508800C4FA956B9A26B570B20C94C48A3961C215",
                sha256(jar));
        assertEquals("8A7F94DBCCAC622FEBFF5111F3B3C2BA439E1D2B2C743F2A5C31FDFCD92D241C",
                sha256(wizardry));
        JarContract.assertNoMethod(wizardry,
                "electroblob/wizardry/item/ItemArtefact",
                "showDurabilityBar",
                "(Lnet/minecraft/item/ItemStack;)Z");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/handler/ASEventHandler",
                "onSpellCastPreEvent",
                "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "getMana",
                "(Lnet/minecraft/item/ItemStack;)I");
        JarContract.assertMethodInvocationCount(jar,
                "com/windanesz/ancientspellcraft/handler/ASEventHandler",
                "onSpellCastPreEvent",
                "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V",
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "getMana",
                "(Lnet/minecraft/item/ItemStack;)I",
                2);
        JarContract.assertFieldAccessCount(jar,
                "com/windanesz/ancientspellcraft/handler/ASEventHandler",
                "onSpellCastPreEvent",
                "(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V",
                "com/windanesz/ancientspellcraft/registry/ASItems",
                "ring_mana_cost",
                "Lnet/minecraft/item/Item;",
                1);
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "onApplyButtonPressed",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/Slot;"
                        + "Lnet/minecraft/inventory/Slot;Lnet/minecraft/inventory/Slot;"
                        + "[Lnet/minecraft/inventory/Slot;)Z");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "func_77624_a",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Ljava/util/List;"
                        + "Lnet/minecraft/client/util/ITooltipFlag;)V");
        JarContract.assertNoMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "showDurabilityBar",
                "(Lnet/minecraft/item/ItemStack;)Z");
        JarContract.assertMethodInvocationCount(jar,
                "com/windanesz/ancientspellcraft/registry/ASItems",
                "register",
                "(Lnet/minecraftforge/event/RegistryEvent$Register;)V",
                "com/windanesz/ancientspellcraft/item/ItemManaArtefact",
                "<init>",
                "(Lnet/minecraft/item/EnumRarity;"
                        + "Lelectroblob/wizardry/item/ItemArtefact$Type;I)V",
                3);
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemEverfullManaFlask",
                "func_77659_a",
                "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemEverfullManaFlask",
                "func_77663_a",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/entity/Entity;IZ)V");
        JarContract.assertMethod(jar,
                "com/windanesz/ancientspellcraft/item/ItemRingManaTransfer",
                "onWornTick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V");
        JarContract.assertFieldAccessCount(jar,
                "com/windanesz/ancientspellcraft/item/ItemRingManaTransfer",
                "onWornTick",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
                "net/minecraft/world/World", "field_72995_K", "Z", 1);
    }

    @Test
    void craftTweakerReleaseArtifactMatchesApprovedIdentity() throws IOException {
        Path craftTweaker = requiredJar(CRAFTTWEAKER_JAR);
        assertEquals("2CD1C68628B78FF5B58A12C94C0DBED8E91D322836BF2D34C9156FEE60390E53",
                sha256(craftTweaker));
        JarContract.assertMethod(craftTweaker, "crafttweaker/CraftTweakerAPI", "registerClass",
                "(Ljava/lang/Class;)V");
        JarContract.assertMethod(craftTweaker, "crafttweaker/api/minecraft/CraftTweakerMC",
                "getPlayer",
                "(Lcrafttweaker/api/player/IPlayer;)Lnet/minecraft/entity/player/EntityPlayer;");
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
