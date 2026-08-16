package com.mahghuuuls.jawmsintegrations.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationConfigLoaderTest {

    @Test
    void exposesApprovedCoreDefaults() {
        IntegrationConfigSnapshot snapshot = IntegrationConfigSnapshot.defaults();

        assertTrue(snapshot.getQualityTools().isIntegrationEnabled());
        assertTrue(snapshot.getQualityTools().areBuiltInQualitiesEnabled());
        assertEquals(12, snapshot.getQualityTools().getBuiltInQualities().size());
        for (IntegrationConfigSnapshot.BuiltInQuality quality
                : IntegrationConfigSnapshot.BuiltInQuality.values()) {
            IntegrationConfigSnapshot.BuiltInQualityConfig configured =
                    snapshot.getQualityTools().getBuiltInQuality(quality);
            assertTrue(configured.isEnabled());
            assertEquals(quality.getDefaultDisplayName(), configured.getDisplayName());
            assertEquals(quality.getDefaultAmount(), configured.getAmount());
            assertEquals(quality.getDefaultWeight(), configured.getWeight());
        }
        assertTrue(snapshot.getAncientSpellcraft().isIntegrationEnabled());
        assertFalse(snapshot.getDiagnostics().isEnabled());
    }

    @Test
    void invalidBooleanFallsBackWithoutChangingValidSiblingResult() {
        List<String> warnings = new ArrayList<>();

        boolean invalid = IntegrationConfigLoader.validateBoolean(
                "quality_tools", "enabled", "not-a-boolean", true, warnings);
        boolean validSibling = IntegrationConfigLoader.validateBoolean(
                "quality_tools", "builtInQualitiesEnabled", "false", true, warnings);

        assertTrue(invalid);
        assertFalse(validSibling);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("quality_tools.enabled"));
        assertTrue(warnings.get(0).contains("using default true"));
    }

    @Test
    void invalidBuiltInFieldsFallBackIndependently() {
        List<String> warnings = new ArrayList<>();

        assertEquals("Manawoven", IntegrationConfigLoader.validateNonBlankString(
                "quality_tools.built_in_qualities.manawoven", "displayName", "  ",
                "Manawoven", warnings));
        assertEquals(5.0D, IntegrationConfigLoader.validatePositiveDouble(
                "quality_tools.built_in_qualities.manawoven", "amount", "NaN",
                5.0D, warnings));
        assertEquals(5, IntegrationConfigLoader.validatePositiveInt(
                "quality_tools.built_in_qualities.manawoven", "weight", "0",
                5, warnings));
        assertEquals(7.5D, IntegrationConfigLoader.validatePositiveDouble(
                "quality_tools.built_in_qualities.manawoven", "amount", "7.5",
                5.0D, warnings));

        assertEquals(3, warnings.size());
        assertTrue(warnings.get(0).contains("displayName"));
        assertTrue(warnings.get(1).contains("amount"));
        assertTrue(warnings.get(2).contains("weight"));
    }

}
