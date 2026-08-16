package com.mahghuuuls.jawmsintegrations.network;

import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacement;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.AncientReplacementPolicy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Versioned, server-authored facts used only for client presentation. */
public final class IntegrationPresentationSnapshot {

    public static final int PROTOCOL_VERSION = 3;
    private final Map<AncientReplacement, Entry> ancientStatic;

    public IntegrationPresentationSnapshot(Map<AncientReplacement, Entry> ancientStatic) {
        if (ancientStatic == null) {
            throw new NullPointerException("ancientStatic");
        }
        EnumMap<AncientReplacement, Entry> copy = new EnumMap<>(AncientReplacement.class);
        for (AncientReplacement replacement : presentationReplacements()) {
            Entry entry = ancientStatic.get(replacement);
            if (entry == null) {
                throw new IllegalArgumentException("Missing presentation entry for " + replacement);
            }
            validateValue(replacement, entry);
            copy.put(replacement, entry);
        }
        this.ancientStatic = Collections.unmodifiableMap(copy);
    }

    public static IntegrationPresentationSnapshot from(AncientReplacementPolicy policy) {
        EnumMap<AncientReplacement, Entry> entries = new EnumMap<>(AncientReplacement.class);
        for (AncientReplacement replacement : presentationReplacements()) {
            if (replacement == AncientReplacement.RING_OF_DAGORIM) {
                entries.put(replacement, new Entry(policy.isPresentationEnabled(replacement),
                        policy.getDagorimConfig().getIntervalSeconds(),
                        policy.getDagorimConfig().getManaThreshold(),
                        policy.getDagorimConfig().getActivationChancePercent()));
            } else {
                entries.put(replacement, new Entry(policy.isPresentationEnabled(replacement),
                        policy.presentationValue(replacement)));
            }
        }
        return new IntegrationPresentationSnapshot(entries);
    }

    public static AncientReplacement[] staticReplacements() {
        return presentationReplacements();
    }

    public static AncientReplacement[] presentationReplacements() {
        AncientReplacement[] staticValues = AncientReplacementPolicy.staticReplacements();
        AncientReplacement[] result = new AncientReplacement[staticValues.length + 2];
        System.arraycopy(staticValues, 0, result, 0, staticValues.length);
        result[staticValues.length] = AncientReplacement.EVERFULL_MANA_FLASK;
        result[staticValues.length + 1] = AncientReplacement.RING_OF_DAGORIM;
        return result;
    }

    public Entry get(AncientReplacement replacement) {
        return ancientStatic.get(replacement);
    }

    private static void validateValue(AncientReplacement replacement, Entry entry) {
        double value = entry.getValue();
        if (replacement == AncientReplacement.RING_OF_DAGORIM) {
            if (value != Math.rint(value) || value < 1 || value > 10000
                    || entry.getSecondaryValue() != Math.rint(entry.getSecondaryValue())
                    || entry.getSecondaryValue() < 0 || entry.getSecondaryValue() > 10000
                    || entry.getTertiaryValue() < 0 || entry.getTertiaryValue() > 100) {
                throw new IllegalArgumentException("Invalid Ring of Dagorim presentation values");
            }
            return;
        }
        if (replacement != AncientReplacement.CRYSTAL_RING
                && (value != Math.rint(value) || value > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException(
                    "Flat-mana presentation value must be an integer for " + replacement);
        }
    }

    public static final class Entry {
        private final boolean enabled;
        private final double value;
        private final double secondaryValue;
        private final double tertiaryValue;

        public Entry(boolean enabled, double value) {
            this(enabled, value, 0.0D, 0.0D);
        }

        public Entry(boolean enabled, double value, double secondaryValue, double tertiaryValue) {
            if (!Double.isFinite(value) || value <= 0.0D) {
                throw new IllegalArgumentException("Presentation value must be finite and positive");
            }
            if (!Double.isFinite(secondaryValue) || !Double.isFinite(tertiaryValue)) {
                throw new IllegalArgumentException("Presentation values must be finite");
            }
            this.enabled = enabled;
            this.value = value;
            this.secondaryValue = secondaryValue;
            this.tertiaryValue = tertiaryValue;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getValue() {
            return value;
        }

        public double getSecondaryValue() { return secondaryValue; }
        public double getTertiaryValue() { return tertiaryValue; }
    }
}
