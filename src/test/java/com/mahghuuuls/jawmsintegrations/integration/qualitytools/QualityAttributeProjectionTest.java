package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import electroblob.wizardry.constants.Element;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityAttributeProjectionTest {

    private static final double EPSILON = 0.000001D;

    @Test
    void publicContractContainsFifteenUniqueNamespacedAttributes() {
        Set<String> names = new HashSet<>();
        for (QualityAttributeProjection.Attribute attribute : QualityAttributeProjection.Attribute.values()) {
            assertTrue(attribute.getPublicName().startsWith("jawmsintegrations."));
            assertTrue(names.add(attribute.getPublicName()));
        }
        assertEquals(15, names.size());
    }

    @Test
    void allNumericFamiliesMapToReleasedJawmsApiUnits() {
        EnumMap<QualityAttributeProjection.Attribute, List<QualityAttributeProjection.ModifierValue>> input =
                new EnumMap<>(QualityAttributeProjection.Attribute.class);
        put(input, QualityAttributeProjection.Attribute.MAX_MANA_FLAT, 8.0D);
        put(input, QualityAttributeProjection.Attribute.MAX_MANA_PERCENT, 12.0D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_FLAT, 1.5D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_PERCENT, 6.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY, 25.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_MAGIC, 1.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_FIRE, 2.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_ICE, 3.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_LIGHTNING, 4.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_NECROMANCY, 5.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_EARTH, 6.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_SORCERY, 7.0D);
        put(input, QualityAttributeProjection.Attribute.SPELL_EFFICIENCY_HEALING, 8.0D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_DELAY_REDUCTION_FLAT, 1.25D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_DELAY_REDUCTION_PERCENT, 30.0D);

        ManaContribution contribution = QualityAttributeProjection.toContribution(
                QualityAttributeProjection.project(input, QualityAttributeProjection.WarningSink.IGNORE));

        assertEquals(8, contribution.getFlatMaximumMana());
        assertEquals(12.0D, contribution.getMaximumManaIncrease(), EPSILON);
        assertEquals(1.5D, contribution.getFlatRegeneration(), EPSILON);
        assertEquals(6.0D, contribution.getRegenerationIncrease(), EPSILON);
        assertEquals(25.0D, contribution.getGlobalSpellEfficiency(), EPSILON);
        assertEquals(1.0D, contribution.getElementSpellEfficiency(Element.MAGIC), EPSILON);
        assertEquals(2.0D, contribution.getElementSpellEfficiency(Element.FIRE), EPSILON);
        assertEquals(3.0D, contribution.getElementSpellEfficiency(Element.ICE), EPSILON);
        assertEquals(4.0D, contribution.getElementSpellEfficiency(Element.LIGHTNING), EPSILON);
        assertEquals(5.0D, contribution.getElementSpellEfficiency(Element.NECROMANCY), EPSILON);
        assertEquals(6.0D, contribution.getElementSpellEfficiency(Element.EARTH), EPSILON);
        assertEquals(7.0D, contribution.getElementSpellEfficiency(Element.SORCERY), EPSILON);
        assertEquals(8.0D, contribution.getElementSpellEfficiency(Element.HEALING), EPSILON);
        assertEquals(-1.25D, contribution.getFlatLockoutSeconds(), EPSILON);
        assertEquals(30.0D, contribution.getLockoutReduction(), EPSILON);
    }

    @Test
    void signedValuesUseReductionAndPenaltyChannels() {
        EnumMap<QualityAttributeProjection.Attribute, List<QualityAttributeProjection.ModifierValue>> input =
                new EnumMap<>(QualityAttributeProjection.Attribute.class);
        put(input, QualityAttributeProjection.Attribute.MAX_MANA_FLAT, -8.0D);
        put(input, QualityAttributeProjection.Attribute.MAX_MANA_PERCENT, -25.0D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_FLAT, -0.5D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_PERCENT, -100.0D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_DELAY_REDUCTION_FLAT, -2.0D);
        put(input, QualityAttributeProjection.Attribute.MANA_REGEN_DELAY_REDUCTION_PERCENT, -40.0D);

        ManaContribution contribution = QualityAttributeProjection.toContribution(
                QualityAttributeProjection.project(input, QualityAttributeProjection.WarningSink.IGNORE));

        assertEquals(-8, contribution.getFlatMaximumMana());
        assertEquals(25.0D, contribution.getMaximumManaReduction(), EPSILON);
        assertEquals(-0.5D, contribution.getFlatRegeneration(), EPSILON);
        assertEquals(100.0D, contribution.getRegenerationReduction(), EPSILON);
        assertEquals(2.0D, contribution.getFlatLockoutSeconds(), EPSILON);
        assertEquals(40.0D, contribution.getLockoutIncrease(), EPSILON);
    }

    @Test
    void fourDefaultSwiftRecoveryPiecesAggregateToFortyPercentagePoints() {
        double amount = IntegrationConfigSnapshot.BuiltInQuality.SWIFT_RECOVERY.getDefaultAmount();
        EnumMap<QualityAttributeProjection.Attribute,
                List<QualityAttributeProjection.ModifierValue>> input =
                new EnumMap<>(QualityAttributeProjection.Attribute.class);
        input.put(QualityAttributeProjection.Attribute.MANA_REGEN_DELAY_REDUCTION_PERCENT,
                modifiers(value(amount, 0), value(amount, 0), value(amount, 0), value(amount, 0)));

        ManaContribution contribution = QualityAttributeProjection.toContribution(
                QualityAttributeProjection.project(input,
                        QualityAttributeProjection.WarningSink.IGNORE));

        assertEquals(40.0D, contribution.getLockoutReduction(), EPSILON);
    }

    @Test
    void operationAndUnsafeValuesAreRejectedWithoutDiscardingValidPeers() {
        RecordingWarnings warnings = new RecordingWarnings();
        double maximum = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MAX_MANA_PERCENT,
                modifiers(value(5.0D, 0), value(50.0D, 1), value(Double.NaN, 0), value(-101.0D, 0)),
                warnings);
        double efficiency = QualityAttributeProjection.projectAttribute(
                "jawmsintegrations.spell_efficiency_fire",
                modifiers(value(8.0D, 0), value(-3.0D, 0), value(4.0D, 2)),
                warnings);

        assertEquals(5.0D, maximum, EPSILON);
        assertEquals(8.0D, efficiency, EPSILON);
        assertTrue(warnings.problems.contains(QualityAttributeProjection.Problem.UNSUPPORTED_OPERATION));
        assertTrue(warnings.problems.contains(QualityAttributeProjection.Problem.NON_FINITE_VALUE));
        assertTrue(warnings.problems.contains(
                QualityAttributeProjection.Problem.PERCENTAGE_REDUCTION_OUT_OF_RANGE));
        assertTrue(warnings.problems.contains(QualityAttributeProjection.Problem.NEGATIVE_SPELL_EFFICIENCY));
    }

    @Test
    void flatMaximumManaRejectsFractionsAndIntegerOverflow() {
        RecordingWarnings warnings = new RecordingWarnings();
        double withFraction = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MAX_MANA_FLAT,
                modifiers(value(8.0D, 0), value(0.5D, 0)), warnings);
        double overflow = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MAX_MANA_FLAT,
                modifiers(value(Integer.MAX_VALUE, 0), value(1.0D, 0)), warnings);

        assertEquals(8.0D, withFraction, EPSILON);
        assertEquals(0.0D, overflow, EPSILON);
        assertTrue(warnings.problems.contains(
                QualityAttributeProjection.Problem.FRACTIONAL_FLAT_MAXIMUM_MANA));
        assertTrue(warnings.problems.contains(
                QualityAttributeProjection.Problem.FLAT_MAXIMUM_MANA_OUT_OF_RANGE));
    }

    @Test
    void bothPercentageReductionOrientationsEnforceOneHundredPercentLimit() {
        RecordingWarnings warnings = new RecordingWarnings();
        double maximum = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MAX_MANA_PERCENT,
                modifiers(value(-60.0D, 0), value(-60.0D, 0)), warnings);
        double delay = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_PERCENT,
                modifiers(value(60.0D, 0), value(60.0D, 0)), warnings);

        assertEquals(0.0D, maximum, EPSILON);
        assertEquals(0.0D, delay, EPSILON);
        assertTrue(warnings.problems.contains(
                QualityAttributeProjection.Problem.PERCENTAGE_TOTAL_OUT_OF_RANGE));
    }

    @Test
    void nonFiniteAggregateAndUnknownAttributeFailSafe() {
        RecordingWarnings warnings = new RecordingWarnings();
        double overflow = QualityAttributeProjection.projectAttribute(
                QualityAttributeProjection.MANA_REGEN_FLAT,
                modifiers(value(Double.MAX_VALUE, 0), value(Double.MAX_VALUE, 0)), warnings);
        double unknown = QualityAttributeProjection.projectAttribute(
                "anothermod.unknown", modifiers(value(10.0D, 0)), warnings);

        assertEquals(0.0D, overflow, EPSILON);
        assertEquals(0.0D, unknown, EPSILON);
        assertTrue(warnings.problems.contains(QualityAttributeProjection.Problem.NON_FINITE_TOTAL));
        assertTrue(warnings.problems.contains(QualityAttributeProjection.Problem.UNKNOWN_ATTRIBUTE));
    }

    @Test
    void allZeroValuesReuseJawmsEmptyContribution() {
        assertSame(ManaContribution.EMPTY,
                QualityAttributeProjection.toContribution(QualityAttributeProjection.Values.ZERO));
    }

    private static void put(Map<QualityAttributeProjection.Attribute,
            List<QualityAttributeProjection.ModifierValue>> target,
                            QualityAttributeProjection.Attribute attribute,
                            double amount) {
        target.put(attribute, modifiers(value(amount, 0)));
    }

    private static QualityAttributeProjection.ModifierValue value(double amount, int operation) {
        return new QualityAttributeProjection.ModifierValue(amount, operation);
    }

    private static List<QualityAttributeProjection.ModifierValue> modifiers(
            QualityAttributeProjection.ModifierValue... values) {
        return Arrays.asList(values);
    }

    private static final class RecordingWarnings implements QualityAttributeProjection.WarningSink {
        private final List<QualityAttributeProjection.Problem> problems = new ArrayList<>();

        @Override
        public void warn(QualityAttributeProjection.Problem problem,
                         String attributeName,
                         int operation,
                         double amount) {
            problems.add(problem);
        }
    }
}
