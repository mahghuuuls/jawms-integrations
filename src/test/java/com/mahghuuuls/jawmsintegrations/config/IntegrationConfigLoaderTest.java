package com.mahghuuuls.jawmsintegrations.config;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertTrue(snapshot.getAncientSpellcraft().getLesserManaRing().isEnabled());
        assertEquals(8, snapshot.getAncientSpellcraft().getLesserManaRing().getValue());
        assertTrue(snapshot.getAncientSpellcraft().getGreaterManaRing().isEnabled());
        assertEquals(12, snapshot.getAncientSpellcraft().getGreaterManaRing().getValue());
        assertTrue(snapshot.getAncientSpellcraft().getMajesticManaCharm().isEnabled());
        assertEquals(15.0D, snapshot.getAncientSpellcraft().getMajesticManaCharm().getValue());
        assertTrue(snapshot.getAncientSpellcraft().getCrystalRing().isEnabled());
        assertEquals(25.0D, snapshot.getAncientSpellcraft().getCrystalRing().getValue());
        assertTrue(snapshot.getAncientSpellcraft().getEverfullManaFlask().isEnabled());
        assertEquals(1, snapshot.getAncientSpellcraft().getEverfullManaFlask()
                .getRegenerationAmount());
        assertEquals(12, snapshot.getAncientSpellcraft().getEverfullManaFlask()
                .getRegenerationIntervalSeconds());
        assertEquals(10, snapshot.getAncientSpellcraft().getEverfullManaFlask()
                .getTransferAmount());
        assertTrue(snapshot.getAncientSpellcraft().getRingOfDagorim().isEnabled());
        assertEquals(5, snapshot.getAncientSpellcraft().getRingOfDagorim().getIntervalSeconds());
        assertEquals(20, snapshot.getAncientSpellcraft().getRingOfDagorim().getManaThreshold());
        assertEquals(20.0D, snapshot.getAncientSpellcraft().getRingOfDagorim()
                .getActivationChancePercent());
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

    @Test
    void invalidAncientReplacementValuesFallBackIndependently() {
        List<String> warnings = new ArrayList<>();

        assertEquals(8, IntegrationConfigLoader.validatePositiveInt(
                "ancient_spellcraft.replacements.lesser_mana_ring",
                "flatMaximumMana", "0", 8, warnings));
        assertEquals(12, IntegrationConfigLoader.validatePositiveInt(
                "ancient_spellcraft.replacements.greater_mana_ring",
                "flatMaximumMana", "10001", 12, warnings));
        assertEquals(15.0D, IntegrationConfigLoader.validatePositiveDouble(
                "ancient_spellcraft.replacements.majestic_mana_charm",
                "percentMaximumMana", "0", 15.0D, warnings));
        assertEquals(25.0D, IntegrationConfigLoader.validatePositiveDouble(
                "ancient_spellcraft.replacements.crystal_ring",
                "spellEfficiency", "-25", 25.0D, warnings));

        assertEquals(4, warnings.size());
        assertTrue(warnings.get(0).contains("lesser_mana_ring.flatMaximumMana"));
        assertTrue(warnings.get(1).contains("greater_mana_ring.flatMaximumMana"));
        assertTrue(warnings.get(2).contains("majestic_mana_charm.percentMaximumMana"));
        assertTrue(warnings.get(3).contains("crystal_ring.spellEfficiency"));
    }

    @Test
    void dagorimValueObjectEnforcesAllApprovedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 0, 20, 20.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, -1, 20.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new IntegrationConfigSnapshot.RingOfDagorimConfig(true, 5, 20, 100.1D));
        IntegrationConfigSnapshot.RingOfDagorimConfig boundary =
                new IntegrationConfigSnapshot.RingOfDagorimConfig(false, 1, 0, 100.0D);
        assertFalse(boundary.isEnabled());
        assertEquals(1, boundary.getIntervalSeconds());
        assertEquals(0, boundary.getManaThreshold());
        assertEquals(100.0D, boundary.getActivationChancePercent());
    }

}
