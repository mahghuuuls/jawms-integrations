package com.mahghuuuls.jawmsintegrations.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArsValidationBundleTest {

    @Test
    void campaignBundlesProvideControlledStateAndBoundedObservations() throws Exception {
        String resource = "/validation/agenttesttoolkit/bundles/jawmsintegrations-arsmagica.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing campaign bundle " + resource);
            JsonObject root = new JsonParser().parse(new InputStreamReader(
                    input, StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals(Arrays.asList(
                    "cycle1_ars_setup",
                    "cycle1_ars_full_jawms_ready",
                    "cycle1_ars_wand_observe",
                    "cycle1_ars_scroll_ready",
                    "cycle1_ars_scroll_observe",
                    "cycle1_ars_inverse_ready",
                    "cycle1_ars_inverse_observe",
                    "cycle1_ars_silence_ready",
                    "cycle1_ars_silence_cleanup",
                    "cycle1_ars_native_ready",
                    "cycle1_ars_native_observe",
                    "cycle1_ars_potency_control_ready",
                    "cycle1_ars_potency_control_observe",
                    "cycle1_ars_potency_disciplined_ready",
                    "cycle1_ars_potency_observe",
                    "cycle1_ars_disabled_setup",
                    "cycle1_ars_disabled_observe",
                    "cycle1_ars_cleanup"), root.entrySet().stream()
                    .map(java.util.Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList()));
            assertTrue(root.getAsJsonObject("cycle1_ars_setup")
                    .get("stopOnFailure").getAsBoolean());
            List<String> setup = commands(root.getAsJsonObject("cycle1_ars_setup"));
            assertTrue(setup.contains("am respec"));
            assertTrue(setup.contains("am magiclevel 1"));
            assertTrue(setup.contains("advancement grant @s only arsmagica2:compendium_data"));
            assertTrue(setup.contains("am setmana 0"));
            assertTrue(setup.stream().anyMatch(command -> command.contains("ebwizardry:magic_wand")
                    && command.contains("spells:[I;1,0,0,0,0]")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("ebwizardry:scroll 8 1")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("ebwizardry:apprentice_ice_wand")
                    && command.contains("spells:[I;20,0,0,0,0]")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("arsmagica2:infinity_orb 1 0")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("IMP-013 No Regeneration")
                    && command.contains("jawmsintegrations.mana_regen_percent")
                    && command.contains("Amount:-100.0d")));
            assertTrue(setup.contains("gamerule doDaylightCycle false"));
            assertTrue(setup.contains("time set night"));
            assertTrue(setup.stream().anyMatch(command -> command.contains("arsmagica2:spell")
                    && command.contains("Ars Validation Bolt")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("minecraft:nether_star")));
            assertTrue(setup.stream().anyMatch(command -> command.contains("minecraft:redstone_torch")));
            assertTrue(setup.contains("devtool inspect inventory"));
            assertTrue(setup.contains("devtool log entity_damage on radius 24"));
            List<String> disabledSetup = commands(root.getAsJsonObject("cycle1_ars_disabled_setup"));
            assertTrue(disabledSetup.contains("devtool session start cycle1_ars_disabled"));
            assertTrue(disabledSetup.contains("am magiclevel 1"));
            assertTrue(commands(root.getAsJsonObject("cycle1_ars_inverse_ready"))
                    .contains("am setmana 100000"));
            assertTrue(commands(root.getAsJsonObject("cycle1_ars_silence_ready"))
                    .contains("effect @s arsmagica2:silence 3600 0 true"));
            assertTrue(commands(root.getAsJsonObject("cycle1_ars_potency_control_ready"))
                    .contains("am respec"));
            assertTrue(commands(root.getAsJsonObject("cycle1_ars_potency_control_ready")).stream()
                    .anyMatch(command -> command.contains("ars_potency_control")
                            && command.contains("generic.maxHealth")
                            && command.contains("Health:100.0f")));
            assertTrue(commands(root.getAsJsonObject("cycle1_ars_potency_disciplined_ready")).stream()
                    .anyMatch(command -> command.contains("ars_potency_disciplined")
                            && command.contains("generic.maxHealth")
                            && command.contains("Health:100.0f")));
            List<String> cleanup = commands(root.getAsJsonObject("cycle1_ars_cleanup"));
            assertTrue(cleanup.contains("effect @s minecraft:speed 1 0 true"));
            assertTrue(cleanup.contains("effect @s clear"));
            assertEquals("devtool session stop", lastCommand(
                    root.getAsJsonObject("cycle1_ars_cleanup")));
        }

        String config = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/validation/arsmagica/arsmagica2/am2.cfg")),
                StandardCharsets.UTF_8);
        double controlledWizardryXpMultiplier = 0.0001D;
        assertTrue(config.contains("D:EBWiz_Magic_XP_Multiplier=0.0001"));
        assertTrue(config.contains("D:EBWiz_Affinity_Gain_Amount=1.0"));
        assertTrue(config.contains("D:EBWiz_Discipline_Potency_Bonus_Per_Level=100.0"));
        int controlledRegenTicks = 2_100_000_000;
        assertTrue(config.contains("I:base_ticks_for_full_regen=" + controlledRegenTicks));
        int controlledMagicLevelCap = 99;
        assertTrue(config.contains("I:magic_level_cap=" + controlledMagicLevelCap));
        assertTrue(config.contains("B:old_xp_calculations=true"));
        int controlledPlayerLevel = 1;
        double capLevelMaximumMana = Math.pow(controlledPlayerLevel, 1.5D)
                * (85.0D * controlledPlayerLevel / 100.0D) + 100.0D;
        double fastestRegenTicks = controlledRegenTicks * (0.75D - 0.25D);
        double manaRegeneratedInTenMinutes = capLevelMaximumMana / fastestRegenTicks * 20.0D * 600.0D;
        assertTrue(manaRegeneratedInTenMinutes < 1.0D,
                "Controlled Ars regeneration must stay below one mana over a ten-minute owner delay");
        double levelOneMaximumXp = Math.pow(0.25D * controlledPlayerLevel, 1.5D);
        double fiveWorstFiniteFloatCostCastsXp = 5.0D * Math.log(Float.MAX_VALUE)
                * controlledWizardryXpMultiplier;
        assertTrue(fiveWorstFiniteFloatCostCastsXp < levelOneMaximumXp,
                "Controlled Wizardry XP must not level the fresh campaign player");

        String controls = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/validation/crafttweaker-campaign/"
                        + "jawmsintegrations_ars_resource_controls.zs")), StandardCharsets.UTF_8);
        assertTrue(controls.contains("minecraft:nether_star"));
        assertTrue(controls.contains("Mana.setCurrentMana(event.player, 60)"));
        assertTrue(controls.contains("minecraft:redstone_torch"));
        assertTrue(controls.contains("Mana.setCurrentMana(event.player, 0)"));

        String inspector = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/mahghuuuls/jawmsintegrations/integration/arsmagica/optional/"
                        + "ArsStateReaderRegistrar.java")), StandardCharsets.UTF_8);
        assertTrue(inspector.contains("Ars disciplines:"));
        assertTrue(inspector.contains("getDisciplineLevel"));

        String gradle = new String(Files.readAllBytes(Paths.get(
                "gradle/scripts/dependencies.gradle")), StandardCharsets.UTF_8);
        assertTrue(gradle.contains("prepareArsMagicaDevelopmentValidationConfig"));
        assertTrue(gradle.contains("run/config"));
        assertTrue(gradle.contains("prepareArsMagicaPackagedValidationConfig"));
        assertTrue(gradle.contains("run/obfuscated/config"));
    }

    private static List<String> commands(JsonObject bundle) {
        List<String> commands = new ArrayList<>();
        for (JsonElement element : bundle.getAsJsonArray("commands")) {
            commands.add(element.isJsonPrimitive()
                    ? element.getAsString()
                    : element.getAsJsonObject().get("command").getAsString());
        }
        return commands;
    }

    private static String lastCommand(JsonObject bundle) {
        JsonArray commands = bundle.getAsJsonArray("commands");
        JsonElement last = commands.get(commands.size() - 1);
        return last.isJsonPrimitive()
                ? last.getAsString()
                : last.getAsJsonObject().get("command").getAsString();
    }
}
