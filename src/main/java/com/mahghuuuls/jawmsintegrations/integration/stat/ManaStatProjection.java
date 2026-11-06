package com.mahghuuuls.jawmsintegrations.integration.stat;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.Tags;
import electroblob.wizardry.constants.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared stat catalog, scalar validation and contribution mapping. */
public final class ManaStatProjection {
    private ManaStatProjection() {
    }

    public enum Stat {
        MAX_MANA("max_mana", Kind.MAXIMUM, null),
        MANA_REGEN("mana_regen", Kind.REGENERATION, null),
        MANA_REGEN_DELAY("mana_regen_delay", Kind.DELAY, null),
        SPELL_EFFICIENCY("spell_efficiency", Kind.EFFICIENCY, null),
        MAGIC("spell_efficiency_magic", Kind.EFFICIENCY, Element.MAGIC),
        FIRE("spell_efficiency_fire", Kind.EFFICIENCY, Element.FIRE),
        ICE("spell_efficiency_ice", Kind.EFFICIENCY, Element.ICE),
        LIGHTNING("spell_efficiency_lightning", Kind.EFFICIENCY, Element.LIGHTNING),
        NECROMANCY("spell_efficiency_necromancy", Kind.EFFICIENCY, Element.NECROMANCY),
        EARTH("spell_efficiency_earth", Kind.EFFICIENCY, Element.EARTH),
        SORCERY("spell_efficiency_sorcery", Kind.EFFICIENCY, Element.SORCERY),
        HEALING("spell_efficiency_healing", Kind.EFFICIENCY, Element.HEALING);

        private final String name;
        private final Kind kind;
        private final Element element;

        Stat(String name, Kind kind, Element element) {
            this.name = name;
            this.kind = kind;
            this.element = element;
        }

        public String getId() {
            return Tags.MOD_ID + ":" + name;
        }

        public static Stat fromName(String name) {
            for (Stat stat : values()) {
                if (stat.name.equals(name)) return stat;
            }
            return null;
        }

        public boolean supportsPercent() {
            return kind != Kind.EFFICIENCY;
        }

        public double minimum(boolean percent) {
            return percent ? -100.0D : kind == Kind.EFFICIENCY ? 0.0D : -Double.MAX_VALUE;
        }

        public String unit() {
            return kind == Kind.DELAY ? "s" : "";
        }
    }

    private enum Kind {
        MAXIMUM, REGENERATION, DELAY, EFFICIENCY
    }

    public enum Reason {
        NON_FINITE, UNSUPPORTED_OPERATION, BELOW_MINIMUM, FRACTIONAL_MAXIMUM, MAXIMUM_OUT_OF_RANGE
    }

    public static final class Totals {
        public static final Totals ZERO = new Totals(0.0D, 0.0D);
        private final double flat;
        private final double percent;

        public Totals(double flat, double percent) {
            this.flat = flat;
            this.percent = percent;
        }

        public double getFlat() {
            return flat;
        }

        public double getPercent() {
            return percent;
        }
    }

    /** Identity excludes amount so changing invalid numbers cannot create log spam. */
    public static final class Problem {
        private final Stat stat;
        private final boolean percent;
        private final Reason reason;
        private final double amount;

        private Problem(Stat stat, boolean percent, Reason reason, double amount) {
            this.stat = stat;
            this.percent = percent;
            this.reason = reason;
            this.amount = amount;
        }

        public Stat getStat() {
            return stat;
        }

        public boolean isPercent() {
            return percent;
        }

        public Reason getReason() {
            return reason;
        }

        public double getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Problem)) return false;
            Problem problem = (Problem) other;
            return stat == problem.stat && percent == problem.percent && reason == problem.reason;
        }

        @Override
        public int hashCode() {
            return Objects.hash(stat, percent, reason);
        }

        @Override
        public String toString() {
            return stat.getId() + " " + (percent ? "percent" : "flat")
                    + " " + reason + " (amount=" + amount + ")";
        }
    }

    public static final class Result {
        private final ManaContribution contribution;
        private final Map<Stat, Totals> sourceTotals;
        private final List<Problem> problems;

        private Result(ManaContribution contribution, Map<Stat, Totals> sourceTotals,
                       List<Problem> problems) {
            this.contribution = contribution.isEmpty() ? ManaContribution.EMPTY : contribution;
            this.sourceTotals = Collections.unmodifiableMap(new EnumMap<>(sourceTotals));
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        public ManaContribution getContribution() {
            return contribution;
        }

        public Map<Stat, Totals> getSourceTotals() {
            return sourceTotals;
        }

        public List<Problem> getProblems() {
            return problems;
        }
    }

    @FunctionalInterface
    public interface TotalsSource {
        Totals read(Stat stat);
    }

    public static Result project(TotalsSource source) {
        Objects.requireNonNull(source, "Mana totals source");
        Map<Stat, Totals> raw = new EnumMap<>(Stat.class);
        List<Problem> problems = new ArrayList<>();
        ManaContribution.Builder builder = ManaContribution.builder();
        for (Stat stat : Stat.values()) {
            Totals totals = Objects.requireNonNull(source.read(stat), "Mana stat total");
            raw.put(stat, totals);
            double flat = accepted(stat, false, totals.flat, problems);
            double percent = accepted(stat, true, totals.percent, problems);
            switch (stat.kind) {
                case MAXIMUM:
                    builder.flatMaximumMana((int) flat);
                    if (percent >= 0.0D) builder.maximumManaIncrease(percent);
                    else builder.maximumManaReduction(-percent);
                    break;
                case REGENERATION:
                    builder.flatRegeneration(flat);
                    if (percent >= 0.0D) builder.regenerationIncrease(percent);
                    else builder.regenerationReduction(-percent);
                    break;
                case DELAY:
                    builder.flatLockoutSeconds(flat);
                    if (percent >= 0.0D) builder.lockoutIncrease(percent);
                    else builder.lockoutReduction(-percent);
                    break;
                case EFFICIENCY:
                    if (flat > 0.0D) {
                        if (stat.element == null) builder.globalSpellEfficiency(flat);
                        else builder.elementSpellEfficiency(stat.element, flat);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unhandled mana stat " + stat);
            }
        }
        return new Result(builder.build(), raw, problems);
    }

    private static double accepted(Stat stat, boolean percent, double amount,
                                   List<Problem> problems) {
        // The unused percent field is still present in the aggregate source shape.
        Reason reason = percent && !stat.supportsPercent() && amount == 0.0D
                ? null : invalidAmount(stat, percent, amount);
        if (reason != null) {
            problems.add(new Problem(stat, percent, reason, amount));
            return 0.0D;
        }
        return amount == 0.0D ? 0.0D : amount;
    }

    public static Reason invalidAmount(Stat stat, boolean percent, double amount) {
        if (!Double.isFinite(amount)) return Reason.NON_FINITE;
        if (percent && !stat.supportsPercent()) return Reason.UNSUPPORTED_OPERATION;
        if (amount < stat.minimum(percent)) return Reason.BELOW_MINIMUM;
        if (stat == Stat.MAX_MANA && !percent) {
            if (amount != Math.rint(amount)) return Reason.FRACTIONAL_MAXIMUM;
            if (amount < Integer.MIN_VALUE || amount > Integer.MAX_VALUE) {
                return Reason.MAXIMUM_OUT_OF_RANGE;
            }
        }
        return null;
    }
}

