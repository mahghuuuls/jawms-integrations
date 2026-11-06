package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.setworks.SetworksRuntime;
import com.mahghuuuls.setworks.api.BonusBuilder;
import com.mahghuuuls.setworks.api.CombinationRule;
import com.mahghuuuls.setworks.api.DefinitionSource;
import com.mahghuuuls.setworks.api.PieceBuilder;
import com.mahghuuuls.setworks.api.SetBuilder;
import com.mahghuuuls.setworks.api.definition.ConditionSpec;
import com.mahghuuuls.setworks.api.definition.EffectSpec;
import com.mahghuuuls.setworks.core.definition.ValidationContext;
import com.mahghuuuls.setworks.core.definition.ValidationReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class SetworksManaEffectTest {
    private final SetworksManaEffect kind = new SetworksManaEffect();

    @Test
    void nativeEffectAcceptsAllFifteenOperationsAndSuppliesTranslatedStatLabels() throws IOException {
        Properties language = new Properties();
        try (java.io.InputStream input = getClass().getResourceAsStream(
                "/assets/jawmsintegrations/lang/en_us.lang")) {
            assertNotNull(input);
            language.load(input);
        }
        assertEquals("jawmsintegrations:mana", kind.id());
        assertEquals(CombinationRule.SUM, kind.combinationRule());
        for (String stat : new String[]{"max_mana", "mana_regen", "mana_regen_delay",
                "spell_efficiency", "spell_efficiency_magic", "spell_efficiency_fire",
                "spell_efficiency_ice", "spell_efficiency_lightning", "spell_efficiency_necromancy",
                "spell_efficiency_earth", "spell_efficiency_sorcery", "spell_efficiency_healing"}) {
            String translation = "property.jawmsintegrations." + stat;
            assertNotNull(language.getProperty(translation), stat);
            assertNull(kind.validate(params(stat, "flat", "10")), stat);
            String flatSuffix = stat.equals("mana_regen_delay") ? " s" : "";
            assertEquals("+10" + flatSuffix + " ${" + translation + "}",
                    kind.describe(params(stat, "flat", "10")));
            if (stat.startsWith("spell_efficiency")) {
                assertNotNull(kind.validate(params(stat, "percent", "0")), stat);
                assertNotNull(kind.validate(params(stat, "flat", "-0.001")), stat);
            } else {
                assertNull(kind.validate(params(stat, "percent", "10")), stat);
                assertEquals("+10% ${" + translation + "}", kind.describe(params(stat, "percent", "10")));
            }
        }
        assertEquals("-1.25 s ${property.jawmsintegrations.mana_regen_delay}",
                kind.describe(params("mana_regen_delay", "flat", "-1.25")));
        assertNull(kind.validate(params("mana_regen_delay", "percent", "-100")));
        assertNull(kind.validate(params("max_mana", "flat", "2147483647")));
        assertNull(kind.validate(params("max_mana", "flat", "-2147483648")));
        assertNull(kind.validate(params("max_mana", "flat", "0")));
        assertNull(kind.validate(params("mana_regen", "flat", "-1.7976931348623158e308")));
    }

    @Test
    void nativeValidationRejectsWrongShapeUnsupportedOperationsAndOutOfRangeAmounts() {
        assertNotNull(kind.validate(Collections.emptyMap()));
        for (String required : new String[]{"stat", "operation", "amount"}) {
            Map<String, String> missing = params("max_mana", "flat", "10");
            missing.remove(required);
            assertNotNull(kind.validate(missing), required);
        }
        Map<String, String> extra = params("max_mana", "flat", "10");
        extra.put("typo", "1");
        assertNotNull(kind.validate(extra));
        assertNotNull(kind.validate(params("health", "flat", "10")));
        assertNotNull(kind.validate(params("max_mana", "add", "10")));
        for (String amount : new String[]{"1.25", "2147483647.00000001", "2147483648", "-2147483649",
                "NaN", "Infinity", "1e400", "10%", " "}) {
            assertNotNull(kind.validate(params("max_mana", "flat", amount)), amount);
        }
        assertNotNull(kind.validate(params("mana_regen", "percent", "-100.01")));
        assertNotNull(kind.validate(params("mana_regen", "percent", "-100.00000000000000001")));
        assertNotNull(kind.validate(params("spell_efficiency", "flat", "-1e-400")));
    }

    @Test
    void nativeRegistryReportsInvalidAndUnknownEffectsAndKeepsTheValidBonus() {
        SetworksRuntime runtime = new SetworksRuntime();
        DefinitionSource source = DefinitionSource.mod("jawms_test");
        assertTrue(runtime.registerEffectKind(kind).isOk());
        for (String set : new String[]{"jawms_test:valid_set", "jawms_test:invalid_set"}) {
            assertTrue(runtime.registerSet(SetBuilder.set(set)
                    .piece(PieceBuilder.piece("hood").at("head").accept("minecraft:leather_helmet"))
                    .build(), source, false).isOk());
        }
        assertTrue(runtime.registerBonus(BonusBuilder.bonus("jawms_test:valid")
                .requires("jawms_test:valid_set", 1).effect(kind.id(), params("max_mana", "flat", "20"))
                .build(), source, false).isOk());
        assertTrue(runtime.registerBonus(BonusBuilder.bonus("jawms_test:fractional")
                .requires("jawms_test:invalid_set", 1).effect(kind.id(), params("max_mana", "flat", "1.25"))
                .build(), source, false).isOk());
        assertTrue(runtime.registerBonus(BonusBuilder.bonus("jawms_test:unknown")
                .requires("jawms_test:invalid_set", 1).effect("jawms_test:missing", Collections.emptyMap())
                .build(), source, false).isOk());

        ValidationContext effects = runtime.validationContext();
        ValidationReport report = runtime.registry().freeze(new ValidationContext() {
            // Provide the one known item and location without starting the Forge registries.
            @Override public boolean itemExists(String id) { return "minecraft:leather_helmet".equals(id); }
            @Override public boolean locationExists(String id) { return "head".equals(id); }
            @Override public boolean oreHasEntries(String id) { return effects.oreHasEntries(id); }
            @Override public boolean effectKindExists(String id) { return effects.effectKindExists(id); }
            @Override public String effectProblem(EffectSpec effect) { return effects.effectProblem(effect); }
            @Override public boolean conditionKindExists(String id) { return effects.conditionKindExists(id); }
            @Override public String conditionProblem(ConditionSpec condition) {
                return effects.conditionProblem(condition);
            }
            @Override public boolean exactNbtParses(String text) { return effects.exactNbtParses(text); }
        });
        assertTrue(report.hasProblems());
        assertEquals(Collections.singleton("jawms_test:invalid_set"), report.disabledSets());
        assertEquals(3, report.errors().size(), report.lines().toString());
        assertTrue(report.errors().stream().anyMatch(error -> "invalid-effect".equals(error.rule())
                && "jawms_test:fractional".equals(error.definitionId())
                && error.message().contains("FRACTIONAL_MAXIMUM")));
        assertTrue(report.errors().stream().anyMatch(error -> "unknown-effect-kind".equals(error.rule())
                && "jawms_test:unknown".equals(error.definitionId())));
        assertNull(runtime.registry().definitions().bonus("jawms_test:fractional"));
        assertNull(runtime.registry().definitions().bonus("jawms_test:unknown"));
        assertNotNull(runtime.registry().definitions().bonus("jawms_test:valid"));
    }

    private static Map<String, String> params(String stat, String operation, String amount) {
        Map<String, String> result = new HashMap<>();
        result.put("stat", stat);
        result.put("operation", operation);
        result.put("amount", amount);
        return result;
    }
}
