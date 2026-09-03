package com.mahghuuuls.jawmsintegrations.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AncientChargeBarValidationBundleTest {

    @Test
    void cycleTwoBundlesPinFixturesObservationsAndCleanup() throws Exception {
        String resource = "/validation/agenttesttoolkit/bundles/jawmsintegrations-ancient.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing Ancient validation bundle " + resource);
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

            JsonObject setup = root.getAsJsonObject("cycle2_ancient_bar_setup");
            assertNotNull(setup);
            assertTrue(setup.get("stopOnFailure").getAsBoolean());
            JsonArray setupCommands = setup.getAsJsonArray("commands");
            assertEquals(12, setupCommands.size());
            assertEquals("replaceitem entity @s slot.hotbar.0 "
                            + "ancientspellcraft:ring_mana_lesser 1 100",
                    setupCommands.get(4).getAsString());
            assertEquals("replaceitem entity @s slot.hotbar.1 "
                            + "ancientspellcraft:ring_mana_greater 1 200",
                    setupCommands.get(5).getAsString());
            assertEquals("replaceitem entity @s slot.hotbar.2 "
                            + "ancientspellcraft:charm_majestic_mana 1 300",
                    setupCommands.get(6).getAsString());
            assertEquals("replaceitem entity @s slot.hotbar.3 minecraft:diamond_pickaxe 1 100",
                    setupCommands.get(7).getAsString());
            assertEquals("CYCLE2_ANCIENT_BAR_READY",
                    setupCommands.get(11).getAsString().substring("devtool mark ".length()));

            JsonObject observe = root.getAsJsonObject("cycle2_ancient_bar_observe");
            assertNotNull(observe);
            assertTrue(observe.get("stopOnFailure").getAsBoolean());
            assertEquals(3, observe.getAsJsonArray("commands").size());

            JsonObject cleanup = root.getAsJsonObject("cycle2_ancient_bar_cleanup");
            assertNotNull(cleanup);
            assertFalse(cleanup.get("stopOnFailure").getAsBoolean());
            assertEquals(8, cleanup.getAsJsonArray("commands").size());
        }
    }
}
