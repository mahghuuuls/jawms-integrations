package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

/** Formats bounded, read-only diagnostics from authoritative status and JAWMS explanations. */
public final class IntegrationDiagnosticsService {

    private final JawmsCompatibility.Status jawms;
    private final IntegrationConfigSnapshot config;
    private final IntegrationCoordinator coordinator;

    public IntegrationDiagnosticsService(JawmsCompatibility.Status jawms,
                                         IntegrationConfigSnapshot config,
                                         IntegrationCoordinator coordinator) {
        this.jawms = jawms;
        this.config = config;
        this.coordinator = coordinator;
    }

    public String startupSummary() {
        StringBuilder result = new StringBuilder("Integration summary: JAWMS ")
                .append(jawms.getModVersion())
                .append("/API ")
                .append(jawms.getApiVersion());
        for (IntegrationStatusView status : coordinator.getStatuses()) {
            result.append("; ")
                    .append(status.getIntegration().getDisplayName())
                    .append('=')
                    .append(status.getState());
            if (status.isPresent()) {
                result.append('(').append(status.getDetectedVersion()).append(')');
            }
        }
        result.append("; built-in qualities=configured-")
                .append(config.getQualityTools().areBuiltInQualitiesEnabled() ? "enabled" : "disabled")
                .append("; Ancient replacements=")
                .append(coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState()
                        == IntegrationState.ACTIVE ? "eligible" : "inactive");
        return result.toString();
    }

    public List<String> overallStatus() {
        List<String> lines = new ArrayList<>();
        lines.add("JAWMS " + jawms.getModVersion() + " (API " + jawms.getApiVersion() + ")");
        for (IntegrationStatusView status : coordinator.getStatuses()) {
            String detected = status.isPresent() ? status.getDetectedVersion() : "not installed";
            lines.add(status.getIntegration().getDisplayName() + ": " + status.getState()
                    + ", detected=" + detected
                    + ", supported=" + status.getSupportedVersion()
                    + ", reason=" + status.getDetail());
        }
        lines.add("Configuration: Quality Tools=" + enabled(config.getQualityTools().isIntegrationEnabled())
                + ", built-in qualities=" + enabled(config.getQualityTools().areBuiltInQualitiesEnabled())
                + ", Ancient Spellcraft=" + enabled(config.getAncientSpellcraft().isIntegrationEnabled())
                + ", startup diagnostics=" + enabled(config.getDiagnostics().isEnabled()));
        return lines;
    }

    public List<String> playerStatus(EntityPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add("JAWMS Integrations contributions for " + player.getName() + ":");
        ManaProfileExplanation profile = ManaApi.explainManaProfile(player);
        int count = 0;
        for (ManaContributionExplanation contribution : profile.getContributions()) {
            if (!Tags.MOD_ID.equals(contribution.getSourceId().getNamespace())) {
                continue;
            }
            count++;
            StringBuilder line = new StringBuilder("- ")
                    .append(contribution.getSourceId())
                    .append(": ")
                    .append(contribution.getDisposition());
            if (contribution.hasItemId()) {
                line.append(", item=").append(contribution.getItemId());
            }
            if (contribution.hasSlot()) {
                line.append(", slot=").append(contribution.getSlotCategory())
                        .append('[').append(contribution.getSlotIndex()).append(']');
            }
            if (contribution.hasAcceptedContribution()) {
                line.append(", contribution=").append(contribution.getAcceptedContribution());
            } else if (contribution.getFailure() != null) {
                line.append(", failure=").append(contribution.getFailure());
            }
            lines.add(line.toString());
        }
        if (count == 0) {
            lines.add("- none");
        }
        return lines;
    }

    private static String enabled(boolean value) {
        return value ? "enabled" : "disabled";
    }
}
