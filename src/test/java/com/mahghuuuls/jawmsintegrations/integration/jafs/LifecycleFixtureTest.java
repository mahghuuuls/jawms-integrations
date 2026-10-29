package com.mahghuuuls.jawmsintegrations.integration.jafs;

import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import com.mahghuuuls.jafs.config.DefinitionJson;
import com.mahghuuuls.jafs.core.DefinitionValidator;
import com.mahghuuuls.jafs.core.DisplayFormat;
import com.mahghuuuls.jafs.core.FoodDefinition;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/** Released native parser/validator/formatter contracts; no simulated eating or reload. */
final class LifecycleFixtureTest {
    private static DefinitionValidator.Result fixture(String name) throws Exception {
        Map<String, PropertySpec> catalog = new HashMap<>();
        for (PropertySpec spec : JafsIntegration.propertySpecs()) catalog.put(spec.id(), spec);
        try (Reader reader = new InputStreamReader(LifecycleFixtureTest.class.getResourceAsStream(
                "/validation/jafs/" + name + ".json"), StandardCharsets.UTF_8)) {
            return DefinitionValidator.validate(DefinitionJson.read(reader), catalog::get);
        }
    }

    @Test
    void nativeInputsReachAllOperationsAndConstructibleFractionAndReductionAggregates() throws Exception {
        DefinitionValidator.Result baseline = fixture("lifecycle-foods");
        assertTrue(baseline.problems.isEmpty(), baseline.problems.toString());
        assertEquals(8, baseline.definitions.size());
        FoodDefinition all = baseline.definitions.get(0);
        assertEquals(15, all.modifiers().size());
        assertEquals("minecraft:apple", all.matchers().get(0).id());
        assertEquals(-1.25, all.modifiers().get(4).amount());
        assertEquals(PropertyOperation.FLAT, all.modifiers().get(4).operation());
        assertEquals(-10, all.modifiers().get(5).amount());
        for (int i = 0; i < 8; i++) assertEquals(i + 1, all.modifiers().get(i + 7).amount());
        assertEquals(30, baseline.definitions.get(4).fullDurationSeconds());
        for (int i : new int[]{5, 6}) {
            assertEquals(0.5, baseline.definitions.get(i).modifiers().get(0).amount());
            assertEquals(5, baseline.definitions.get(i).modifiers().get(1).amount());
            assertEquals(-60, baseline.definitions.get(i).modifiers().get(2).amount());
        }
        DefinitionValidator.Result reloaded = fixture("lifecycle-reloaded");
        assertTrue(reloaded.problems.isEmpty(), reloaded.problems.toString());
        assertEquals(all.id(), reloaded.definitions.get(0).id());
        assertEquals(1, reloaded.definitions.get(0).modifiers().size());
        assertEquals(30, reloaded.definitions.get(0).modifiers().get(0).amount());
        DefinitionValidator.Result recovered = fixture("lifecycle-fraction-recovered");
        assertTrue(recovered.problems.isEmpty(), recovered.problems.toString());
        assertEquals(0.5, recovered.definitions.get(7).modifiers().get(0).amount());
        for (String bad : new String[]{"unknown", "percent-efficiency", "negative-efficiency", "excess-reduction"}) {
            DefinitionValidator.Result skipped = fixture("lifecycle-skip-" + bad);
            assertFalse(skipped.problems.isEmpty(), bad);
            assertEquals(8, skipped.definitions.size(), bad);
            assertEquals(1, skipped.definitions.get(0).modifiers().size(), bad);
            assertEquals(30, skipped.definitions.get(0).modifiers().get(0).amount(), bad);
        }
        assertThrows(com.google.gson.JsonParseException.class, () -> fixture("lifecycle-wrong-shape"));
        DefinitionValidator.Result overflow = fixture("lifecycle-overflow");
        assertTrue(overflow.problems.isEmpty(), overflow.problems.toString());
        assertEquals(2147483648D, overflow.definitions.get(7).modifiers().get(0).amount());
        assertEquals(1e308, overflow.definitions.get(5).modifiers().get(0).amount());
        assertEquals(1e308, overflow.definitions.get(6).modifiers().get(0).amount());
    }

    @Test
    void nativeFormatterKeepsPercentAndFlatUnitsDistinctAndLabelsResolve() throws Exception {
        assertEquals("+10%", DisplayFormat.amount(10, PropertyOperation.PERCENT, "s"));
        assertEquals("-1.3 s", DisplayFormat.amount(-1.25, PropertyOperation.FLAT, "s"));
        assertEquals("+1.5", DisplayFormat.amount(1.5, PropertyOperation.FLAT, ""));
        Properties lang = new Properties();
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/jawmsintegrations/lang/en_us.lang"), StandardCharsets.UTF_8)) {
            lang.load(reader);
        }
        for (PropertySpec spec : JafsIntegration.propertySpecs()) {
            String label = lang.getProperty(spec.displayNameKey());
            assertNotNull(label, spec.id());
            assertFalse(label.contains("%"), spec.id());
            assertFalse(label.contains("Shorter"), spec.id());
        }
    }

    @Test
    void campaignHasBoundedTraceableReadsAndNoManaWriteOnResume() throws Exception {
        JsonObject bundles;
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/validation/agenttesttoolkit/bundles/jawmsintegrations-jafs-lifecycle.json"), StandardCharsets.UTF_8)) {
            bundles = new JsonParser().parse(reader).getAsJsonObject();
        }
        assertEquals(30, bundles.entrySet().size());
        assertTrue(bundles.getAsJsonObject("cyc3_jafs_b_setup").get("stopOnFailure").getAsBoolean());
        String resume = bundles.getAsJsonObject("cyc3_jafs_b_resume").get("commands").toString();
        assertFalse(resume.contains("mana set"));
        assertFalse(resume.contains("cache dirty"));
        assertFalse(resume.contains("jafs clear"));
        String expiry = bundles.getAsJsonObject("cyc3_jafs_b_replacement_expiry").get("commands").toString();
        assertTrue(expiry.contains("620"));
        assertTrue(expiry.contains("JAFS_B_EXPIRY_OBSERVED"));
        String death = bundles.getAsJsonObject("cyc3_jafs_b_death_ready").get("commands").toString();
        assertTrue(death.endsWith("\"kill ${operator.name}\"]"));
        String portal = bundles.getAsJsonObject("cyc3_jafs_b_portal_build").get("commands").toString();
        assertTrue(portal.indexOf("tp ${operator.name} 0 200 0") < portal.indexOf("await chunks"));
        assertTrue(portal.indexOf("await chunks") < portal.indexOf("fill"));
        assertTrue(portal.contains("minecraft:air"));
        assertTrue(portal.contains("commands.fill.failed"));
        String setup = bundles.getAsJsonObject("cyc3_jafs_b_setup").get("commands").toString();
        assertTrue(setup.contains("commands.clear.failure"));
        String aboveBase = bundles.getAsJsonObject("cyc3_jafs_b_rebuild_ready").get("commands").toString();
        assertTrue(aboveBase.contains("mana set ${operator.name} 120"));
    }
}
