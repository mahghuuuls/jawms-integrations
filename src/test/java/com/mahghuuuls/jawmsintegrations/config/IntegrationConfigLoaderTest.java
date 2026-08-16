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
}
