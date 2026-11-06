package com.mahghuuuls.jawmsintegrations.integration.setworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection;
import com.mahghuuuls.jawmsintegrations.integration.stat.ManaStatProjection.Stat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

/** Owns the callback ledger and the last acknowledged publication for one server player. */
final class SetworksPlayerGroup {
    private final Slot slot;
    private final Consumer<String> warning;
    private final Set<String> warned = new HashSet<>();
    private final Map<String, Map<String, String>> instances = new LinkedHashMap<>();

    private boolean dirty;
    private ManaContribution verified;
    private String verifiedOmissions = "";
    private String verificationFailure;

    private ManaProfileGeneration generation;
    private long revision;
    private ManaContribution recorded;
    private String recordedOmissions = "";
    private boolean suspended;
    private boolean accepted;
    private boolean publishing;
    private boolean receiptUncertain;
    private String publicationFailure;

    SetworksPlayerGroup(Slot slot, Consumer<String> warning) {
        this.slot = slot;
        this.warning = warning;
    }

    /** A missing token can be temporary during a transfer. Keep the source receipt until replaced. */
    void bind(ManaProfileGeneration current) {
        if (current == null || current == generation) return;
        generation = current;
        revision = 0;
        recorded = null;
        recordedOmissions = "";
        suspended = false;
        accepted = false;
        receiptUncertain = false;
        publicationFailure = null;
    }

    void apply(String instanceId, Map<String, String> params) {
        Map<String, String> previous = instances.get(instanceId);
        if (previous != null && previous.equals(params)) return;
        // Store the callback input even if this group cannot currently be projected.
        instances.put(instanceId, params == null ? new HashMap<>() : new HashMap<>(params));
        invalidate();
    }

    void withdraw(String instanceId) {
        if (instances.remove(instanceId) != null) invalidate();
    }

    private void invalidate() {
        dirty = true;
        verified = null;
        if (revision == 0 || suspended || accepted || receiptUncertain) return;
        try {
            if (slot.readiness() == ManaProfileReadiness.READY) {
                accepted = true;
                return;
            }
            // A source generation can be temporarily hidden by an uncommitted transfer.
            // Suspend before the group finishes or a guarded evaluation unwinds.
            ManaContributionPublicationResult result = slot.suspend(generation, revision);
            if (result.getStatus() == ManaContributionPublicationStatus.ALREADY_ACCEPTED) {
                accepted = true;
            } else if (result.getStatus() == ManaContributionPublicationStatus.SUSPENDED
                    && result.getRevision().isPresent()) {
                revision = result.getRevision().getAsLong();
                suspended = true;
                recorded = null;
                publicationFailure = null;
            } else {
                reject("suspend", result.getStatus());
            }
        } catch (RuntimeException failure) {
            unknownReceipt("suspend", failure);
        }
    }

    /** A known lifecycle completion can establish the first empty group. A quiet pass cannot clear failure. */
    void finish(boolean lifecycle) {
        if (lifecycle && dirty) {
            verificationFailure = "effect changes did not reach the native group boundary";
            warnOnce("incomplete callback group", verificationFailure);
            return;
        }
        if (!dirty && !(lifecycle && verified == null && verificationFailure == null)) return;
        dirty = false;
        try {
            ManaStatProjection.Result projection = verify();
            verified = projection.getContribution();
            verificationFailure = null;
            StringJoiner omitted = new StringJoiner(", ");
            for (ManaStatProjection.Problem problem : projection.getProblems()) {
                String field = problem.getStat().getId() + (problem.isPercent() ? " percent" : " flat");
                omitted.add(field + " " + problem.getReason());
                warnOnce(field + " " + problem.getReason(), "Ignored invalid aggregate " + problem);
            }
            verifiedOmissions = omitted.toString();
        } catch (RuntimeException failure) {
            verified = null;
            verificationFailure = "could not verify the complete effect group (" + failure.getClass().getSimpleName() + ")";
            warnOnce("verification", verificationFailure);
        }
    }

    private ManaStatProjection.Result verify() {
        Map<Stat, double[]> sums = new EnumMap<>(Stat.class);
        for (Map<String, String> params : instances.values()) {
            SetworksManaEffect.Parameters value = SetworksManaEffect.parse(params);
            sums.computeIfAbsent(value.stat, ignored -> new double[2])[value.percent ? 1 : 0] += value.amount;
        }
        return ManaStatProjection.project(stat -> {
            double[] values = sums.get(stat);
            return values == null ? ManaStatProjection.Totals.ZERO
                    : new ManaStatProjection.Totals(values[0], values[1]);
        });
    }

    void publish(ManaProfileGeneration current) {
        if (current == null || current != generation || publishing || dirty || verified == null
                || receiptUncertain || (!suspended && Objects.equals(recorded, verified)
                && recordedOmissions.equals(verifiedOmissions))) return;
        ManaContribution candidate = verified;
        String omissions = verifiedOmissions;
        boolean initial = revision == 0;
        publishing = true;
        try {
            ManaContributionPublicationResult result = initial
                    ? slot.complete(current, candidate) : slot.update(current, revision, candidate);
            if (current != generation) return;
            ManaContributionPublicationStatus success = initial ? ManaContributionPublicationStatus.INITIAL_RECORDED
                    : ManaContributionPublicationStatus.UPDATE_RECORDED;
            if (result.getStatus() == success && result.getRevision().isPresent()) {
                revision = result.getRevision().getAsLong();
                recorded = candidate;
                recordedOmissions = omissions;
                suspended = false;
                publicationFailure = null;
            } else {
                reject(initial ? "initial" : "update", result.getStatus());
            }
        } catch (RuntimeException failure) {
            if (current == generation) {
                unknownReceipt("publication", failure);
            }
        } finally {
            publishing = false;
        }
    }

    private void unknownReceipt(String action, RuntimeException failure) {
        receiptUncertain = true;
        publicationFailure = action + " threw " + failure.getClass().getSimpleName();
        warnOnce(action + " exception", publicationFailure);
    }

    private void reject(String action, ManaContributionPublicationStatus status) {
        publicationFailure = action + " returned " + status;
        switch (status) {
            case BUSY:
            case STALE_GENERATION:
            case INAPPLICABLE:
                return;
            default:
                // A rejected call gives no new revision. Only a fresh token can clear this condition.
                receiptUncertain = true;
                warnOnce(publicationFailure, publicationFailure);
        }
    }

    SetworksSourceAccess.PlayerStatus inspect(ManaProfileReadiness phase, ManaContribution applied) {
        if (verificationFailure != null || publicationFailure != null) {
            String state = applied != null ? "stale applied contribution" : (suspended ? "suspended" : "unresolved");
            String reason = verificationFailure != null ? verificationFailure : publicationFailure;
            return new SetworksSourceAccess.PlayerStatus(state + " (" + phase + "): " + reason, applied);
        }
        if (applied != null) {
            if (dirty || verified != recorded || !matchesAccepted(recorded, applied)) {
                return new SetworksSourceAccess.PlayerStatus("stale applied contribution (replacement pending)", applied);
            }
            String state = applied.isEmpty() ? "verified zero contribution" : "applied contribution";
            return new SetworksSourceAccess.PlayerStatus(state + (recordedOmissions.isEmpty()
                    ? "" : "; invalid fields omitted: " + recordedOmissions), applied);
        }
        if (phase == ManaProfileReadiness.LEGACY) {
            return new SetworksSourceAccess.PlayerStatus("no applicable JAWMS readiness slot", null);
        }
        return new SetworksSourceAccess.PlayerStatus("awaiting verified acceptance (" + phase + ")", null);
    }

    private static boolean matchesAccepted(ManaContribution recorded, ManaContribution applied) {
        if (recorded == applied) return true;
        // Explanations can retain an equivalent immutable snapshot rather than the submission object.
        return recorded != null
                && recorded.getFlatMaximumMana() == applied.getFlatMaximumMana()
                && recorded.getMaximumManaIncrease() == applied.getMaximumManaIncrease()
                && recorded.getMaximumManaReduction() == applied.getMaximumManaReduction()
                && recorded.getFlatRegeneration() == applied.getFlatRegeneration()
                && recorded.getRegenerationIncrease() == applied.getRegenerationIncrease()
                && recorded.getRegenerationReduction() == applied.getRegenerationReduction()
                && recorded.getFlatLockoutSeconds() == applied.getFlatLockoutSeconds()
                && recorded.getLockoutIncrease() == applied.getLockoutIncrease()
                && recorded.getLockoutReduction() == applied.getLockoutReduction()
                && recorded.getGlobalSpellEfficiency() == applied.getGlobalSpellEfficiency()
                && recorded.getElementSpellEfficiency().equals(applied.getElementSpellEfficiency());
    }

    private void warnOnce(String key, String detail) {
        if (warned.add(key)) warning.accept(detail);
    }

    interface Slot {
        ManaProfileReadiness readiness();
        ManaContributionPublicationResult complete(ManaProfileGeneration token, ManaContribution group);
        ManaContributionPublicationResult suspend(ManaProfileGeneration token, long revision);
        ManaContributionPublicationResult update(ManaProfileGeneration token, long revision, ManaContribution group);
    }
}
