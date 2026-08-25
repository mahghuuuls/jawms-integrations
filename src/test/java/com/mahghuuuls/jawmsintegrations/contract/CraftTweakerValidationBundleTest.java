package com.mahghuuuls.jawmsintegrations.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTweakerValidationBundleTest {

    @Test
    void campaignBundleUsesOneNamedTriggerAndBoundedCleanup() throws Exception {
        String resource = "/validation/agenttesttoolkit/bundles/"
                + "jawmsintegrations-crafttweaker.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing campaign bundle " + resource);
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(3, root.entrySet().size());

            JsonObject setup = root.getAsJsonObject("cycle1_crafttweaker_setup");
            assertTrue(setup.get("stopOnFailure").getAsBoolean());
            JsonArray commands = setup.getAsJsonArray("commands");
            assertEquals(12, commands.size());
            assertEquals("devtool session start cycle1_crafttweaker",
                    commands.get(0).getAsString());
            assertEquals("replaceitem entity @s slot.hotbar.0 minecraft:blaze_rod 1 0 "
                    + "{display:{Name:\"JAWMS CraftTweaker Validation\"}}",
                    commands.get(7).getAsString());
            assertEquals("jawmsintegrations status @s",
                    commands.get(9).getAsJsonObject().get("command").getAsString());
            assertEquals(20, commands.get(9).getAsJsonObject().get("delayTicks").getAsInt());
            assertEquals("devtool mark CYCLE1_CRAFTTWEAKER_READY",
                    commands.get(11).getAsString());

            JsonObject observe = root.getAsJsonObject("cycle1_crafttweaker_observe");
            assertEquals(Arrays.asList("jawmsintegrations status @s", "devtool inspect player",
                            "devtool mark CYCLE1_CRAFTTWEAKER_RESULT_OBSERVED"),
                    Arrays.asList(observe.getAsJsonArray("commands").get(0).getAsString(),
                            observe.getAsJsonArray("commands").get(1).getAsString(),
                            observe.getAsJsonArray("commands").get(2).getAsString()));

            JsonObject cleanup = root.getAsJsonObject("cycle1_crafttweaker_cleanup");
            assertFalse(cleanup.get("stopOnFailure").getAsBoolean());
            assertEquals("replaceitem entity @s slot.hotbar.0 minecraft:air",
                    cleanup.getAsJsonArray("commands").get(0).getAsString());
            assertEquals("devtool session stop",
                    cleanup.getAsJsonArray("commands").get(5).getAsString());
        }
    }

    @Test
    void runtimeScriptPinsServerSideAtomicAndLockoutScenario() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/validation/crafttweaker-campaign/"
                        + "jawmsintegrations_runtime_validation.zs")), StandardCharsets.UTF_8);
        for (String exact : Arrays.asList(
                "events.onPlayerRightClickItem(function(event as PlayerRightClickItemEvent)",
                "!event.world.remote",
                "event.item.definition.id == \"minecraft:blaze_rod\"",
                "Mana.setCurrentMana(player, 40)",
                "Mana.restoreMana(player, 10)",
                "Mana.consumeMana(player, 7)",
                "Mana.consumeMana(player, 1000000)",
                "Mana.drainMana(player, 5)",
                "Mana.startRegenerationLockout(player)",
                "beforeLockout.continuousCastActive",
                "afterLockout.continuousCastActive",
                "[JAWMS-CT] END")) {
            assertTrue(source.contains(exact), "Missing campaign contract " + exact);
        }
    }

    @Test
    void finalSmokeBundlePinsOverworldNetherAndMutationBoundaries() throws Exception {
        String resource = "/validation/agenttesttoolkit/bundles/jawmsintegrations-final.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing final-smoke bundle " + resource);
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(4, root.entrySet().size());
            assertEquals(Arrays.asList(
                    "devtool session start cycle1_final_smoke",
                    "gamemode survival",
                    "gamerule keepInventory true",
                    "gamerule doMobSpawning false",
                    "replaceitem entity @s slot.armor.head ebwizardry:wizard_hat 1 0 "
                            + "{Quality:{Name:\"Final Manawoven Fixture\",Color:\"aqua\","
                            + "Slots:[\"head\"],AttributeModifiers:[{AttributeName:"
                            + "\"jawmsintegrations.max_mana_percent\",Name:\"qualitytools\","
                            + "Amount:5.0d,Operation:0,UUIDMost:514L,UUIDLeast:514L}]}}",
                    "replaceitem entity @s slot.hotbar.0 minecraft:blaze_rod 1 0 "
                            + "{display:{Name:\"JAWMS Final Validation\"}}",
                    "jawmsintegrations status @s",
                    "devtool environment",
                    "devtool inspect player",
                    "devtool inspect inventory",
                    "devtool mark CYCLE1_FINAL_OVERWORLD_READY"),
                    commands(root, "cycle1_final_smoke_setup"));
            JsonArray setup = root.getAsJsonObject("cycle1_final_smoke_setup")
                    .getAsJsonArray("commands");
            assertEquals(20, setup.get(6).getAsJsonObject().get("delayTicks").getAsInt());
            assertEquals(Arrays.asList(
                    "devtool environment",
                    "jawmsintegrations status @s",
                    "devtool inspect player",
                    "devtool inspect inventory",
                    "devtool mark CYCLE1_FINAL_NETHER_MUTATION_READY"),
                    commands(root, "cycle1_final_smoke_nether_ready"));
            assertEquals(Arrays.asList(
                    "jawmsintegrations status @s",
                    "devtool inspect player",
                    "devtool inspect inventory",
                    "devtool environment",
                    "devtool mark CYCLE1_FINAL_NETHER_RESULT_OBSERVED"),
                    commands(root, "cycle1_final_smoke_result"));
            assertEquals(Arrays.asList(
                    "replaceitem entity @s slot.armor.head minecraft:air",
                    "replaceitem entity @s slot.hotbar.0 minecraft:air",
                    "devtool inspect inventory",
                    "devtool mark CYCLE1_FINAL_CLEANUP_COMPLETE",
                    "devtool log all off",
                    "devtool session stop"),
                    commands(root, "cycle1_final_smoke_cleanup"));
        }
    }

    @Test
    void packagedHarnessInstallsToolkitAndCraftTweakerCampaignOnlyForValidation() throws Exception {
        String dependencies = new String(Files.readAllBytes(Paths.get(
                "gradle/scripts/dependencies.gradle")), StandardCharsets.UTF_8);
        String profile = section(dependencies,
                "tasks.register('verifyPackagedRuntimeProfile')",
                "['runObfClient', 'runObfServer'].each");
        assertTrue(profile.contains("dependsOn tasks.named('prepareObfModsFolder')"));
        assertTrue(profile.contains("dependsOn tasks.named('preparePackagedAgentTestToolkitRuntime')"));
        String toolkitConfig = section(dependencies,
                "tasks.register('preparePackagedAgentTestToolkitConfiguration'",
                "tasks.register('prepareQualityToolsValidationFixtures'");
        assertTrue(toolkitConfig.contains("onlyIf { propertyBool('enable_agent_test_toolkit') }"));
        assertTrue(toolkitConfig.contains("run/obfuscated/config/devtool"));
        String qualityConfig = section(dependencies,
                "tasks.register('preparePackagedQualityToolsValidationFixtures'",
                "tasks.register('prepareCraftTweakerDevelopmentValidationScript'");
        assertTrue(qualityConfig.contains(
                "propertyBool('enable_agent_test_toolkit') && qualityToolsRuntimeEnabled"));
        assertTrue(qualityConfig.contains("run/obfuscated/config/qualitytools"));
        String campaign = section(dependencies,
                "tasks.register('prepareCraftTweakerPackagedCampaignScript'",
                "['runClient', 'runServer'].each");
        assertTrue(campaign.contains(
                "propertyBool('enable_agent_test_toolkit') && craftTweakerRuntimeEnabled"));
        assertTrue(campaign.contains("run/obfuscated/scripts/jawmsintegrations-validation"));
        String campaignWiring = section(dependencies,
                "tasks.register('prepareCraftTweakerPackagedCampaignScript'",
                "tasks.register('prepareIntegrationRuntimeMods')");
        assertTrue(campaignWiring.contains(
                "dependsOn tasks.named('prepareCraftTweakerPackagedCampaignScript')"));
        String cleanup = section(dependencies,
                "tasks.register('cleanManagedPackagedRuntimeArtifacts')",
                "def packagedRuntimeCounts");
        assertTrue(cleanup.contains("run/obfuscated/config/devtool"));
        assertTrue(cleanup.contains("jawms-integrations-validation.json"));
        assertTrue(cleanup.contains("run/obfuscated/scripts/jawmsintegrations-validation"));
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")),
                StandardCharsets.UTF_8);
        String wiring = section(build, "if (propertyBool('enable_agent_test_toolkit'))",
                "if (propertyBool('use_mixins'))");
        assertTrue(wiring.contains("['runClient', 'runServer'].each"));
        assertTrue(wiring.contains("dependsOn tasks.named('prepareAgentTestToolkitRuntime')"));
        assertTrue(wiring.contains("['runObfClient', 'runObfServer'].each"));
        assertTrue(wiring.contains(
                "dependsOn tasks.named('preparePackagedAgentTestToolkitConfiguration')"));
        assertTrue(wiring.contains(
                "dependsOn tasks.named('preparePackagedQualityToolsValidationFixtures')"));
    }

    private static List<String> commands(JsonObject root, String bundle) {
        List<String> result = new ArrayList<>();
        for (com.google.gson.JsonElement element
                : root.getAsJsonObject(bundle).getAsJsonArray("commands")) {
            result.add(element.isJsonObject()
                    ? element.getAsJsonObject().get("command").getAsString()
                    : element.getAsString());
        }
        return result;
    }

    private static String section(String source, String start, String end) {
        int first = source.indexOf(start);
        assertTrue(first >= 0, "Missing section start " + start);
        int last = source.indexOf(end, first + start.length());
        assertTrue(last > first, "Missing section end " + end);
        return source.substring(first, last);
    }
}
