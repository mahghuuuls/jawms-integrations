package com.mahghuuuls.jawmsintegrations.integration.qualitytools;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Owns the public attribute contract, validation, units, and JAWMS mapping. */
public final class QualityAttributeProjection {

    public static final String MAX_MANA_FLAT = "jawmsintegrations.max_mana_flat";
    public static final String MAX_MANA_PERCENT = "jawmsintegrations.max_mana_percent";
    public static final String MANA_REGEN_FLAT = "jawmsintegrations.mana_regen_flat";
    public static final String MANA_REGEN_PERCENT = "jawmsintegrations.mana_regen_percent";
    public static final String SPELL_EFFICIENCY = "jawmsintegrations.spell_efficiency";
    public static final String MANA_REGEN_DELAY_REDUCTION_FLAT =
            "jawmsintegrations.mana_regen_delay_reduction_flat";
    public static final String MANA_REGEN_DELAY_REDUCTION_PERCENT =
            "jawmsintegrations.mana_regen_delay_reduction_percent";

    private QualityAttributeProjection() {
    }

    public static Values project(Map<Attribute, List<ModifierValue>> modifiers,
                                 WarningSink warnings) {
        WarningSink target = warnings == null ? WarningSink.IGNORE : warnings;
        EnumMap<Attribute, Double> projected = new EnumMap<>(Attribute.class);
        Map<Attribute, List<ModifierValue>> source = modifiers == null
                ? Collections.emptyMap()
                : modifiers;
        for (Attribute attribute : Attribute.values()) {
            projected.put(attribute, project(attribute, safe(source.get(attribute)), target));
        }
        return new Values(projected);
    }

    public static double projectAttribute(String attributeName,
                                          List<ModifierValue> modifiers,
                                          WarningSink warnings) {
        WarningSink target = warnings == null ? WarningSink.IGNORE : warnings;
        Attribute attribute = Attribute.forName(attributeName);
        if (attribute == null) {
            target.warn(Problem.UNKNOWN_ATTRIBUTE, attributeName, 0, 0.0D);
            return 0.0D;
        }
        return project(attribute, safe(modifiers), target);
    }

    public static ManaContribution toContribution(Values values) {
        if (values == null || values.isZero()) {
            return ManaContribution.EMPTY;
        }
        ManaContribution.Builder builder = ManaContribution.builder();
        double flatMaximumMana = values.get(Attribute.MAX_MANA_FLAT);
        if (flatMaximumMana != 0.0D) {
            builder.flatMaximumMana((int) flatMaximumMana);
        }
        signedPercentage(values.get(Attribute.MAX_MANA_PERCENT), builder::maximumManaIncrease,
                builder::maximumManaReduction);
        double flatRegeneration = values.get(Attribute.MANA_REGEN_FLAT);
        if (flatRegeneration != 0.0D) {
            builder.flatRegeneration(flatRegeneration);
        }
        signedPercentage(values.get(Attribute.MANA_REGEN_PERCENT), builder::regenerationIncrease,
                builder::regenerationReduction);
        double flatDelayReduction = values.get(Attribute.MANA_REGEN_DELAY_REDUCTION_FLAT);
        if (flatDelayReduction != 0.0D) {
            builder.flatLockoutSeconds(-flatDelayReduction);
        }
        signedReduction(values.get(Attribute.MANA_REGEN_DELAY_REDUCTION_PERCENT),
                builder::lockoutReduction, builder::lockoutIncrease);
        double globalEfficiency = values.get(Attribute.SPELL_EFFICIENCY);
        if (globalEfficiency > 0.0D) {
            builder.globalSpellEfficiency(globalEfficiency);
        }
        for (Attribute attribute : Attribute.values()) {
            if (attribute.element != null) {
                double value = values.get(attribute);
                if (value > 0.0D) {
                    builder.elementSpellEfficiency(attribute.element, value);
                }
            }
        }
        return builder.build();
    }

    private static double project(Attribute attribute,
                                  List<ModifierValue> modifiers,
                                  WarningSink warnings) {
        switch (attribute.kind) {
            case FLAT_MAXIMUM_MANA:
                return flatMaximumMana(attribute.publicName, modifiers, warnings);
            case SIGNED_PERCENTAGE:
                return percentage(attribute.publicName, modifiers, warnings, false);
            case LOCKOUT_REDUCTION_PERCENTAGE:
                return percentage(attribute.publicName, modifiers, warnings, true);
            case SPELL_EFFICIENCY:
                return efficiency(attribute.publicName, modifiers, warnings);
            case SIGNED_FLAT:
            default:
                return sum(attribute.publicName, modifiers, warnings);
        }
    }

    private static List<ModifierValue> safe(List<ModifierValue> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static double flatMaximumMana(String attributeName,
                                          List<ModifierValue> modifiers,
                                          WarningSink warnings) {
        double total = 0.0D;
        for (ModifierValue modifier : modifiers) {
            if (!supported(modifier, attributeName, warnings)) {
                continue;
            }
            if (modifier.amount != Math.rint(modifier.amount)) {
                warnings.warn(Problem.FRACTIONAL_FLAT_MAXIMUM_MANA,
                        attributeName, modifier.operation, modifier.amount);
                continue;
            }
            total += modifier.amount;
            if (!Double.isFinite(total)) {
                warnings.warn(Problem.NON_FINITE_TOTAL, attributeName, modifier.operation, total);
                return 0.0D;
            }
        }
        if (total < Integer.MIN_VALUE || total > Integer.MAX_VALUE) {
            warnings.warn(Problem.FLAT_MAXIMUM_MANA_OUT_OF_RANGE, attributeName, 0, total);
            return 0.0D;
        }
        return normalizedZero(total);
    }

    private static double percentage(String attributeName,
                                     List<ModifierValue> modifiers,
                                     WarningSink warnings,
                                     boolean positiveIsReduction) {
        double total = 0.0D;
        for (ModifierValue modifier : modifiers) {
            if (!supported(modifier, attributeName, warnings)) {
                continue;
            }
            double reduction = positiveIsReduction ? modifier.amount : -modifier.amount;
            if (reduction > 100.0D) {
                warnings.warn(Problem.PERCENTAGE_REDUCTION_OUT_OF_RANGE,
                        attributeName, modifier.operation, modifier.amount);
                continue;
            }
            total += modifier.amount;
            if (!Double.isFinite(total)) {
                warnings.warn(Problem.NON_FINITE_TOTAL, attributeName, modifier.operation, total);
                return 0.0D;
            }
        }
        double totalReduction = positiveIsReduction ? total : -total;
        if (totalReduction > 100.0D) {
            warnings.warn(Problem.PERCENTAGE_TOTAL_OUT_OF_RANGE, attributeName, 0, total);
            return 0.0D;
        }
        return normalizedZero(total);
    }

    private static double efficiency(String attributeName,
                                     List<ModifierValue> modifiers,
                                     WarningSink warnings) {
        double total = 0.0D;
        for (ModifierValue modifier : modifiers) {
            if (!supported(modifier, attributeName, warnings)) {
                continue;
            }
            if (modifier.amount < 0.0D) {
                warnings.warn(Problem.NEGATIVE_SPELL_EFFICIENCY,
                        attributeName, modifier.operation, modifier.amount);
                continue;
            }
            total += modifier.amount;
            if (!Double.isFinite(total)) {
                warnings.warn(Problem.NON_FINITE_TOTAL, attributeName, modifier.operation, total);
                return 0.0D;
            }
        }
        return normalizedZero(total);
    }

    private static double sum(String attributeName,
                              List<ModifierValue> modifiers,
                              WarningSink warnings) {
        double total = 0.0D;
        for (ModifierValue modifier : modifiers) {
            if (!supported(modifier, attributeName, warnings)) {
                continue;
            }
            total += modifier.amount;
            if (!Double.isFinite(total)) {
                warnings.warn(Problem.NON_FINITE_TOTAL, attributeName, modifier.operation, total);
                return 0.0D;
            }
        }
        return normalizedZero(total);
    }

    private static boolean supported(ModifierValue modifier,
                                     String attributeName,
                                     WarningSink warnings) {
        if (modifier == null) {
            warnings.warn(Problem.NULL_MODIFIER, attributeName, 0, 0.0D);
            return false;
        }
        if (modifier.operation != 0) {
            warnings.warn(Problem.UNSUPPORTED_OPERATION,
                    attributeName, modifier.operation, modifier.amount);
            return false;
        }
        if (!Double.isFinite(modifier.amount)) {
            warnings.warn(Problem.NON_FINITE_VALUE,
                    attributeName, modifier.operation, modifier.amount);
            return false;
        }
        return true;
    }

    private static void signedPercentage(double value,
                                         DoubleSetter increase,
                                         DoubleSetter reduction) {
        if (value > 0.0D) {
            increase.set(value);
        } else if (value < 0.0D) {
            reduction.set(-value);
        }
    }

    private static void signedReduction(double value,
                                        DoubleSetter reduction,
                                        DoubleSetter increase) {
        if (value > 0.0D) {
            reduction.set(value);
        } else if (value < 0.0D) {
            increase.set(-value);
        }
    }

    private static double normalizedZero(double value) {
        return value == 0.0D ? 0.0D : value;
    }

    public enum Attribute {
        MAX_MANA_FLAT(QualityAttributeProjection.MAX_MANA_FLAT, Kind.FLAT_MAXIMUM_MANA, null),
        MAX_MANA_PERCENT(QualityAttributeProjection.MAX_MANA_PERCENT, Kind.SIGNED_PERCENTAGE, null),
        MANA_REGEN_FLAT(QualityAttributeProjection.MANA_REGEN_FLAT, Kind.SIGNED_FLAT, null),
        MANA_REGEN_PERCENT(QualityAttributeProjection.MANA_REGEN_PERCENT, Kind.SIGNED_PERCENTAGE, null),
        SPELL_EFFICIENCY(QualityAttributeProjection.SPELL_EFFICIENCY, Kind.SPELL_EFFICIENCY, null),
        SPELL_EFFICIENCY_MAGIC("jawmsintegrations.spell_efficiency_magic", Kind.SPELL_EFFICIENCY, Element.MAGIC),
        SPELL_EFFICIENCY_FIRE("jawmsintegrations.spell_efficiency_fire", Kind.SPELL_EFFICIENCY, Element.FIRE),
        SPELL_EFFICIENCY_ICE("jawmsintegrations.spell_efficiency_ice", Kind.SPELL_EFFICIENCY, Element.ICE),
        SPELL_EFFICIENCY_LIGHTNING("jawmsintegrations.spell_efficiency_lightning", Kind.SPELL_EFFICIENCY, Element.LIGHTNING),
        SPELL_EFFICIENCY_NECROMANCY("jawmsintegrations.spell_efficiency_necromancy", Kind.SPELL_EFFICIENCY, Element.NECROMANCY),
        SPELL_EFFICIENCY_EARTH("jawmsintegrations.spell_efficiency_earth", Kind.SPELL_EFFICIENCY, Element.EARTH),
        SPELL_EFFICIENCY_SORCERY("jawmsintegrations.spell_efficiency_sorcery", Kind.SPELL_EFFICIENCY, Element.SORCERY),
        SPELL_EFFICIENCY_HEALING("jawmsintegrations.spell_efficiency_healing", Kind.SPELL_EFFICIENCY, Element.HEALING),
        MANA_REGEN_DELAY_REDUCTION_FLAT(QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_FLAT,
                Kind.SIGNED_FLAT, null),
        MANA_REGEN_DELAY_REDUCTION_PERCENT(QualityAttributeProjection.MANA_REGEN_DELAY_REDUCTION_PERCENT,
                Kind.LOCKOUT_REDUCTION_PERCENTAGE, null);

        private final String publicName;
        private final Kind kind;
        private final Element element;

        Attribute(String publicName, Kind kind, Element element) {
            this.publicName = publicName;
            this.kind = kind;
            this.element = element;
        }

        public String getPublicName() {
            return publicName;
        }

        static Attribute forName(String name) {
            for (Attribute attribute : values()) {
                if (attribute.publicName.equals(name)) {
                    return attribute;
                }
            }
            return null;
        }
    }

    private enum Kind {
        FLAT_MAXIMUM_MANA,
        SIGNED_FLAT,
        SIGNED_PERCENTAGE,
        LOCKOUT_REDUCTION_PERCENTAGE,
        SPELL_EFFICIENCY
    }

    public enum Problem {
        UNKNOWN_ATTRIBUTE,
        NULL_MODIFIER,
        UNSUPPORTED_OPERATION,
        NON_FINITE_VALUE,
        NON_FINITE_TOTAL,
        PERCENTAGE_REDUCTION_OUT_OF_RANGE,
        PERCENTAGE_TOTAL_OUT_OF_RANGE,
        FRACTIONAL_FLAT_MAXIMUM_MANA,
        FLAT_MAXIMUM_MANA_OUT_OF_RANGE,
        NEGATIVE_SPELL_EFFICIENCY
    }

    public interface WarningSink {
        WarningSink IGNORE = (problem, attributeName, operation, amount) -> { };

        void warn(Problem problem, String attributeName, int operation, double amount);
    }

    public static final class ModifierValue {
        private final double amount;
        private final int operation;

        public ModifierValue(double amount, int operation) {
            this.amount = amount;
            this.operation = operation;
        }

        public double getAmount() {
            return amount;
        }

        public int getOperation() {
            return operation;
        }
    }

    public static final class Values {
        public static final Values ZERO = new Values(Collections.emptyMap());

        private final EnumMap<Attribute, Double> values;

        public Values(Map<Attribute, Double> values) {
            this.values = new EnumMap<>(Attribute.class);
            for (Attribute attribute : Attribute.values()) {
                Double value = values == null ? null : values.get(attribute);
                this.values.put(attribute, normalizedZero(value == null ? 0.0D : value));
            }
        }

        public double get(Attribute attribute) {
            Double value = values.get(attribute);
            return value == null ? 0.0D : value;
        }

        public double getMaximumManaPercent() {
            return get(Attribute.MAX_MANA_PERCENT);
        }

        public double getManaRegenerationPercent() {
            return get(Attribute.MANA_REGEN_PERCENT);
        }

        public double getSpellEfficiency() {
            return get(Attribute.SPELL_EFFICIENCY);
        }

        public boolean isZero() {
            for (double value : values.values()) {
                if (value != 0.0D) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Values && values.equals(((Values) other).values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }

        @Override
        public String toString() {
            return values.toString();
        }
    }

    private interface DoubleSetter {
        void set(double value);
    }
}
