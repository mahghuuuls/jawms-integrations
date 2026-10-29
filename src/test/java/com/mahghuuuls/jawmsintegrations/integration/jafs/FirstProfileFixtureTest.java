package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jafs.config.DefinitionJson;
import com.mahghuuuls.jafs.core.DefinitionValidator;
import com.mahghuuuls.jafs.core.FoodDefinition;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.core.Matcher;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Contract fixture check using the released native parser, not a replacement JSON schema. */
final class FirstProfileFixtureTest {
    @Test
    void nativeParserAcceptsTheLongLivedTwentyMaximumManaApple() throws Exception {
        Map<String, PropertySpec> catalog = new HashMap<>();
        for (PropertySpec spec : JafsIntegration.propertySpecs()) catalog.put(spec.id(), spec);
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/validation/jafs/first-profile-foods.json"), StandardCharsets.UTF_8)) {
            DefinitionValidator.Result result = DefinitionValidator.validate(DefinitionJson.read(reader),
                    catalog::get);
            assertTrue(result.problems.isEmpty(), result.problems.toString());
            assertEquals(1, result.definitions.size());
            FoodDefinition food = result.definitions.get(0);
            assertEquals("jawmsintegrations_c1_apple", food.id());
            assertEquals(3600, food.fullDurationSeconds());
            assertEquals(1, food.matchers().size());
            assertEquals(Matcher.Kind.ITEM, food.matchers().get(0).kind());
            assertEquals("minecraft:apple", food.matchers().get(0).id());
            assertEquals(0, food.matchers().get(0).meta());
            assertEquals(1, food.modifiers().size());
            assertEquals("jawmsintegrations:max_mana", food.modifiers().get(0).propertyId());
            assertEquals(PropertyOperation.FLAT, food.modifiers().get(0).operation());
            assertEquals(20, food.modifiers().get(0).amount());
        }
    }
}
