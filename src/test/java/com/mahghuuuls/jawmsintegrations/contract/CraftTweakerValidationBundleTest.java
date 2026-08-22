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
}
