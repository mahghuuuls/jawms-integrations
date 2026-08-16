package com.mahghuuuls.jawmsintegrations.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QualityToolsValidationFixtureTest {

    @Test
    void customAttributeFixtureCannotInterceptWizardryArmorByGenericSlot() throws Exception {
        String resource = "/validation/qualitytools/Quailities/jawms-integrations-validation.json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing validation fixture " + resource);
            JsonObject root = new JsonParser().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray whitelist = root.getAsJsonArray("whitelist");
            Set<String> items = new HashSet<>();
            for (JsonElement element : whitelist) {
                JsonObject entry = element.getAsJsonObject();
                assertFalse(entry.has("slot"), "Generic slot whitelist would intercept mage armor");
                items.add(entry.get("item").getAsString());
            }
            assertEquals(new HashSet<>(Arrays.asList(
                    "minecraft:iron_helmet", "minecraft:iron_chestplate")), items);
        }
    }
}
