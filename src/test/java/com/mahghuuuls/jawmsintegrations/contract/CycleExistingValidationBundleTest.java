package com.mahghuuuls.jawmsintegrations.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycleExistingValidationBundleTest {

    @Test
    void swiftCampaignBundlesPreserveStoredFiveAndPrepareBlankReforgeItems()
            throws Exception {
        String resource = "/validation/agenttesttoolkit/bundles/"
                + "jawmsintegrations-cycle1-existing.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing campaign bundle " + resource);
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals(7, root.entrySet().size());
            assertSetup(root.getAsJsonObject("cycle1_swift_existing_setup"),
                    "devtool session start cycle1_existing_swift_five",
                    "devtool mark CYCLE1_SWIFT_EXISTING_FIVE_READY");
            assertSetup(root.getAsJsonObject("cycle1_swift_default_setup"),
                    "devtool session start cycle1_existing_swift_ten",
                    "devtool mark CYCLE1_SWIFT_DEFAULT_TEN_READY");
            assertObserve(root, "cycle1_swift_existing_stored_observe",
                    "devtool mark CYCLE1_SWIFT_EXISTING_STORED_FIVE_OBSERVED");
            assertObserve(root, "cycle1_swift_existing_reforge_observe",
                    "devtool mark CYCLE1_SWIFT_EXISTING_REFORGE_FIVE_OBSERVED");
            assertObserve(root, "cycle1_swift_default_stored_observe",
                    "devtool mark CYCLE1_SWIFT_DEFAULT_STORED_FIVE_OBSERVED");
            assertObserve(root, "cycle1_swift_default_reforge_observe",
                    "devtool mark CYCLE1_SWIFT_DEFAULT_REFORGE_TEN_OBSERVED");
            assertFalse(root.getAsJsonObject("cycle1_swift_cleanup")
                    .get("stopOnFailure").getAsBoolean());
        }
    }

    private static void assertSetup(JsonObject bundle, String session, String readyMark) {
        assertTrue(bundle.get("stopOnFailure").getAsBoolean());
        JsonArray commands = bundle.getAsJsonArray("commands");
        assertEquals(12, commands.size());
        assertEquals(session, primitive(commands, 0));
        assertEquals("gamemode creative", primitive(commands, 1));
        assertEquals("gamerule keepInventory true", primitive(commands, 2));
        assertEquals("gamerule doMobSpawning false", primitive(commands, 3));
        assertEquals("replaceitem entity @s slot.armor.head minecraft:air",
                primitive(commands, 4));
        assertEquals(storedFiveCommand(), primitive(commands, 5));
        assertEquals("replaceitem entity @s slot.hotbar.1 ebwizardry:wizard_hat",
                primitive(commands, 6));
        assertEquals("replaceitem entity @s slot.hotbar.6 qualitytools:reforging_station",
                primitive(commands, 7));
        assertEquals("replaceitem entity @s slot.hotbar.7 minecraft:nether_star 64",
                primitive(commands, 8));
        JsonObject delayedStatus = commands.get(9).getAsJsonObject();
        assertEquals("jawmsintegrations status @s",
                delayedStatus.get("command").getAsString());
        assertEquals(20, delayedStatus.get("delayTicks").getAsInt());
        assertEquals("devtool inspect player", primitive(commands, 10));
        assertEquals(readyMark, primitive(commands, 11));
    }

    private static void assertObserve(JsonObject root, String name, String mark) {
        JsonObject bundle = root.getAsJsonObject(name);
        assertNotNull(bundle);
        assertTrue(bundle.get("stopOnFailure").getAsBoolean());
        assertEquals(Arrays.asList(
                "jawmsintegrations status @s",
                "devtool nbt held",
                "devtool inspect player",
                mark), primitiveCommands(bundle));
    }

    private static List<String> primitiveCommands(JsonObject bundle) {
        List<String> commands = new ArrayList<>();
        for (JsonElement command : bundle.getAsJsonArray("commands")) {
            commands.add(command.getAsString());
        }
        return commands;
    }

    private static String primitive(JsonArray commands, int index) {
        return commands.get(index).getAsString();
    }

    private static String storedFiveCommand() {
        return "replaceitem entity @s slot.hotbar.0 ebwizardry:wizard_hat 1 0 "
                + "{Quality:{Name:\"Swift Recovery Stored 5\",Color:\"aqua\","
                + "Slots:[\"head\"],AttributeModifiers:[{AttributeName:"
                + "\"jawmsintegrations.mana_regen_delay_reduction_percent\","
                + "Name:\"qualitytools\",Amount:5.0d,Operation:0,UUIDMost:510L,"
                + "UUIDLeast:510L}]}}";
    }
}
