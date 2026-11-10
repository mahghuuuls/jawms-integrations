package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Normalizes a whole native read without mutating either provider. */
final class StatusworksProjection {
    // Native definition IDs are nonempty, whitespace-free strings and may contain the key delimiters.
    private static final String DEFINITION = "\\S+#[0-9]+";
    private static final Pattern VALUE_KEY = Pattern.compile("(status|pool):" + DEFINITION);
    private static final Pattern MARK_KEY = Pattern.compile("mark:" + DEFINITION
            + "@(source|target):[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final int MAX_PROBLEMS = 16;

    private StatusworksProjection() {
    }

    static Read read(Function<String, ContributionGroup> groups) {
        Map<Stat, ManaStatProjection.Totals> totals = new EnumMap<>(Stat.class);
        List<String> problems = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            Field flat = readField(groups, stat, false, problems);
            if (flat.failure != null) return Read.unverified(flat.failure);
            double percent = 0.0D;
            if (stat.supportsPercent()) {
                Field result = readField(groups, stat, true, problems);
                if (result.failure != null) return Read.unverified(result.failure);
                percent = result.amount;
            }
            totals.put(stat, new ManaStatProjection.Totals(flat.amount, percent));
        }
        ManaStatProjection.Result projection = ManaStatProjection.project(totals::get);
        for (ManaStatProjection.Problem problem : projection.getProblems()) {
            addProblem(problems, StatusworksAuthoring.id(problem.getStat(), problem.isPercent())
                    + " aggregate " + problem.getReason());
        }
        return new Read(projection.getContribution(), null, Collections.unmodifiableList(problems));
    }

    private static Field readField(Function<String, ContributionGroup> groups, Stat stat, boolean percent,
                                   List<String> problems) {
        String id = StatusworksAuthoring.id(stat, percent);
        ContributionGroup group = groups.apply(id);
        if (group.availability() != GroupAvailability.AVAILABLE) {
            return new Field(0.0D, group.availability() + " " + id);
        }
        List<Map.Entry<String, Double>> values = new ArrayList<>(group.values().entrySet());
        values.sort(Map.Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())));
        double sum = 0.0D;
        for (Map.Entry<String, Double> entry : values) {
            String key = entry.getKey();
            if (key != null && MARK_KEY.matcher(key).matches()) continue;
            if (key == null || !VALUE_KEY.matcher(key).matches() || entry.getValue() == null) {
                return new Field(0.0D, "UNKNOWN_ORIGIN " + id + " " + abbreviated(key));
            }
            double amount = entry.getValue();
            ManaStatProjection.Reason reason = ManaStatProjection.invalidAmount(stat, percent, amount);
            if (reason != null) {
                addProblem(problems, id + " " + abbreviated(key) + " " + reason);
            } else {
                sum += amount;
            }
        }
        return new Field(sum, null);
    }

    private static void addProblem(List<String> problems, String problem) {
        if (problems.size() < MAX_PROBLEMS) problems.add(problem);
    }

    private static String abbreviated(String key) {
        if (key == null) return "<null>";
        String sanitized = key.replaceAll("[\\p{Cntrl}]", "?");
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160) + "...";
    }

    static boolean same(ManaContribution left, ManaContribution right) {
        if (left == right) return true;
        return left != null && right != null
                && left.getFlatMaximumMana() == right.getFlatMaximumMana()
                && left.getMaximumManaIncrease() == right.getMaximumManaIncrease()
                && left.getMaximumManaReduction() == right.getMaximumManaReduction()
                && left.getFlatRegeneration() == right.getFlatRegeneration()
                && left.getRegenerationIncrease() == right.getRegenerationIncrease()
                && left.getRegenerationReduction() == right.getRegenerationReduction()
                && left.getFlatLockoutSeconds() == right.getFlatLockoutSeconds()
                && left.getLockoutIncrease() == right.getLockoutIncrease()
                && left.getLockoutReduction() == right.getLockoutReduction()
                && left.getGlobalSpellEfficiency() == right.getGlobalSpellEfficiency()
                && left.getElementSpellEfficiency().equals(right.getElementSpellEfficiency())
                && left.getGlobalSpellCostMultiplier() == right.getGlobalSpellCostMultiplier()
                && left.getElementSpellCostMultipliers().equals(right.getElementSpellCostMultipliers())
                && left.canRegenerateDuringPostCastLockout() == right.canRegenerateDuringPostCastLockout()
                && left.canRegenerateDuringContinuousCasting() == right.canRegenerateDuringContinuousCasting();
    }

    private static final class Field {
        final double amount;
        final String failure;

        Field(double amount, String failure) {
            this.amount = amount;
            this.failure = failure;
        }
    }

    static final class Read {
        final ManaContribution contribution;
        final String failure;
        final List<String> problems;

        Read(ManaContribution contribution, String failure, List<String> problems) {
            this.contribution = contribution;
            this.failure = failure;
            this.problems = problems;
        }

        static Read unverified(String reason) {
            return new Read(null, reason, Collections.emptyList());
        }
    }
}
