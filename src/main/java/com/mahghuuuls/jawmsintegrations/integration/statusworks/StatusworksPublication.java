package com.mahghuuuls.jawmsintegrations.integration.statusworks;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;

import java.util.OptionalLong;
import java.util.function.Supplier;

/** The acknowledgement ledger for one actual JAWMS generation. */
final class StatusworksPublication {
    ManaProfileGeneration generation;
    long revision;
    ManaContribution candidate;
    boolean uncertain;
    String outcome = "awaiting generation";

    void bind(ManaProfileGeneration current) {
        if (generation == current) return;
        generation = current;
        revision = 0;
        candidate = null;
        uncertain = false;
        outcome = current == null ? "no current generation" : "awaiting initial receipt";
    }

    boolean matches(ManaProfileGeneration current, long currentRevision, ManaContribution contribution) {
        return !uncertain && generation == current && revision == currentRevision
                && candidate != null && StatusworksProjection.same(candidate, contribution);
    }

    boolean verifies(ManaProfileGeneration current, long currentRevision, ManaContribution contribution,
                     Supplier<StatusworksProjection.Read> read) {
        if (!matches(current, currentRevision, contribution)) return false;
        StatusworksProjection.Read nativeResult = read.get();
        return nativeResult.failure == null && StatusworksProjection.same(contribution, nativeResult.contribution);
    }

    boolean acknowledge(ManaContributionPublicationResult result, ManaContributionPublicationStatus expected,
                        ManaContribution contribution) {
        ManaContributionPublicationStatus status = result.getStatus();
        outcome = status.name();
        if (status == expected) {
            OptionalLong next = result.getRevision();
            if (next.isPresent() && next.getAsLong() > revision) {
                revision = next.getAsLong();
                candidate = contribution;
                return true;
            }
            loseReceipt("invalid " + expected + " receipt");
        } else if (status != ManaContributionPublicationStatus.BUSY
                && status != ManaContributionPublicationStatus.STALE_GENERATION
                && !(expected == ManaContributionPublicationStatus.SUSPENDED
                && status == ManaContributionPublicationStatus.ALREADY_ACCEPTED)) {
            loseReceipt("unconfirmed " + expected + " receipt: " + status);
        }
        return false;
    }

    void loseReceipt(String reason) {
        uncertain = true;
        outcome = reason;
    }
}
