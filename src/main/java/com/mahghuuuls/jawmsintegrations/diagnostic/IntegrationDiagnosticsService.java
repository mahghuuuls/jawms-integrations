package com.mahghuuuls.jawmsintegrations.diagnostic;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawmsintegrations.Tags;
import com.mahghuuuls.jawmsintegrations.config.IntegrationConfigSnapshot;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationCoordinator;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationId;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationState;
import com.mahghuuuls.jawmsintegrations.integration.IntegrationStatusView;
import com.mahghuuuls.jawmsintegrations.integration.JawmsCompatibility;
import com.mahghuuuls.jawmsintegrations.integration.ancientspellcraft.DagorimFlaskService;
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
        result.append("; built-in qualities=")
                .append(builtInSummary())
                .append("; Ancient replacements=")
                .append(ancientReplacementSummary());
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
                + ", built-in qualities=" + builtInSummary()
                + ", Ancient Spellcraft=" + enabled(config.getAncientSpellcraft().isIntegrationEnabled())
                + ", Ancient replacements=" + ancientReplacementSummary()
                + ", startup diagnostics=" + enabled(config.getDiagnostics().isEnabled()));
        if (config.getDiagnostics().isEnabled()) {
            lines.add("Latest Ring of Dagorim activation: "
                    + DagorimFlaskService.active().latestActivationSummary());
        }
        return lines;
    }

    public String qualityToolsReloadSummary() {
        return "Quality Tools reload summary: built-in qualities=" + builtInSummary();
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
                line.append(", contribution=")
                        .append(formatContribution(contribution.getAcceptedContribution()));
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

    private String builtInSummary() {
        int total = config.getQualityTools().getBuiltInQualities().size();
        if (!config.getQualityTools().areBuiltInQualitiesEnabled()) {
            return "disabled(0/" + total + ")";
        }
        int enabled = 0;
        for (IntegrationConfigSnapshot.BuiltInQualityConfig quality
                : config.getQualityTools().getBuiltInQualities().values()) {
            if (quality.isEnabled()) {
                enabled++;
            }
        }
        return "enabled(" + enabled + "/" + total + ")";
    }

    private String ancientReplacementSummary() {
        IntegrationState state = coordinator.getStatus(IntegrationId.ANCIENT_SPELLCRAFT).getState();
        if (state != IntegrationState.ACTIVE) {
            return "inactive(state=" + state + ")";
        }
        IntegrationConfigSnapshot.AncientSpellcraftConfig ancient = config.getAncientSpellcraft();
        int enabledCount = 0;
        enabledCount += ancient.getLesserManaRing().isEnabled() ? 1 : 0;
        enabledCount += ancient.getGreaterManaRing().isEnabled() ? 1 : 0;
        enabledCount += ancient.getMajesticManaCharm().isEnabled() ? 1 : 0;
        enabledCount += ancient.getCrystalRing().isEnabled() ? 1 : 0;
        enabledCount += ancient.getEverfullManaFlask().isEnabled() ? 1 : 0;
        enabledCount += ancient.getRingOfDagorim().isEnabled() ? 1 : 0;
        return "enabled(" + enabledCount + "/6"
                + ", lesser=" + ancient.getLesserManaRing().getValue()
                + ", greater=" + ancient.getGreaterManaRing().getValue()
                + ", majestic=" + ancient.getMajesticManaCharm().getValue()
                + ", crystal=" + ancient.getCrystalRing().getValue()
                + ", everfull=capacity100/regen"
                + ancient.getEverfullManaFlask().getRegenerationAmount()
                + "@" + ancient.getEverfullManaFlask().getRegenerationIntervalSeconds()
                + "s/transfer" + ancient.getEverfullManaFlask().getTransferAmount()
                + ", dagorim=" + ancient.getRingOfDagorim().getIntervalSeconds()
                + "s/below" + ancient.getRingOfDagorim().getManaThreshold()
                + "/chance" + ancient.getRingOfDagorim().getActivationChancePercent() + "%)";
    }

    static String formatContribution(ManaContribution contribution) {
        if (contribution == null || contribution.isEmpty()) {
            return "{}";
        }
        List<String> fields = new ArrayList<>();
        add(fields, "flatMaximumMana", contribution.getFlatMaximumMana(), 0);
        add(fields, "maximumManaIncrease", contribution.getMaximumManaIncrease(), 0.0D);
        add(fields, "maximumManaReduction", contribution.getMaximumManaReduction(), 0.0D);
        add(fields, "flatRegeneration", contribution.getFlatRegeneration(), 0.0D);
        add(fields, "regenerationIncrease", contribution.getRegenerationIncrease(), 0.0D);
        add(fields, "regenerationReduction", contribution.getRegenerationReduction(), 0.0D);
        add(fields, "flatLockoutSeconds", contribution.getFlatLockoutSeconds(), 0.0D);
        add(fields, "lockoutIncrease", contribution.getLockoutIncrease(), 0.0D);
        add(fields, "lockoutReduction", contribution.getLockoutReduction(), 0.0D);
        add(fields, "spellEfficiency", contribution.getGlobalSpellEfficiency(), 0.0D);
        if (!contribution.getElementSpellEfficiency().isEmpty()) {
            fields.add("elementSpellEfficiency=" + contribution.getElementSpellEfficiency());
        }
        add(fields, "spellCostMultiplier", contribution.getGlobalSpellCostMultiplier(), 1.0D);
        if (!contribution.getElementSpellCostMultipliers().isEmpty()) {
            fields.add("elementSpellCostMultipliers=" + contribution.getElementSpellCostMultipliers());
        }
        if (contribution.canRegenerateDuringPostCastLockout()) {
            fields.add("regenerateDuringPostCastLockout=true");
        }
        if (contribution.canRegenerateDuringContinuousCasting()) {
            fields.add("regenerateDuringContinuousCasting=true");
        }
        return "{" + String.join(", ", fields) + "}";
    }

    private static void add(List<String> fields, String name, int value, int neutral) {
        if (value != neutral) {
            fields.add(name + "=" + value);
        }
    }

    private static void add(List<String> fields, String name, double value, double neutral) {
        if (Double.compare(value, neutral) != 0) {
            fields.add(name + "=" + value);
        }
    }
}
