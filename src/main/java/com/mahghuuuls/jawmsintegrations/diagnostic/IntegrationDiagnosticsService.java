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
import com.mahghuuuls.jawmsintegrations.integration.jafs.FoodSourceAccess;
import com.mahghuuuls.jawmsintegrations.integration.jafs.FoodStatProjection;
import com.mahghuuuls.jawmsintegrations.integration.jass.SpellStaminaAccess;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

/** Formats bounded, read-only diagnostics from authoritative status and JAWMS explanations. */
public final class IntegrationDiagnosticsService {

    private final JawmsCompatibility.Status jawms;
    private final IntegrationConfigSnapshot config;
    private final IntegrationCoordinator coordinator;
    private final FoodSourceAccess foodSource;
    private final SpellStaminaAccess spellStamina;

    public IntegrationDiagnosticsService(JawmsCompatibility.Status jawms,
                                         IntegrationConfigSnapshot config,
                                         IntegrationCoordinator coordinator) {
        this(jawms, config, coordinator, null);
    }

    public IntegrationDiagnosticsService(JawmsCompatibility.Status jawms,
                                         IntegrationConfigSnapshot config,
                                         IntegrationCoordinator coordinator,
                                         FoodSourceAccess foodSource) {
        this(jawms, config, coordinator, foodSource, null);
    }

    public IntegrationDiagnosticsService(JawmsCompatibility.Status jawms,
                                         IntegrationConfigSnapshot config,
                                         IntegrationCoordinator coordinator,
                                         FoodSourceAccess foodSource,
                                         SpellStaminaAccess spellStamina) {
        this.jawms = jawms;
        this.config = config;
        this.coordinator = coordinator;
        this.foodSource = foodSource;
        this.spellStamina = spellStamina;
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
                .append(ancientReplacementSummary())
                .append("; ").append(spellStaminaSettings());
        return result.toString();
    }

    public List<String> overallStatus() {
        List<String> lines = new ArrayList<>();
        lines.add("JAWMS " + jawms.getModVersion() + " (API " + jawms.getApiVersion() + ")");
        for (IntegrationStatusView status : coordinator.getStatuses()) {
            String detected = status.isPresent() ? status.getDetectedVersion() : "not installed";
            lines.add(status.getIntegration().getDisplayName() + ": " + status.getState()
                    + ", detected=" + detected
                    + ", minimum=" + status.getMinimumVersion()
                    + ", reason=" + status.getDetail());
        }
        lines.add("Configuration: Quality Tools=" + enabled(config.getQualityTools().isIntegrationEnabled())
                + ", built-in qualities=" + builtInSummary()
                + ", Ancient Spellcraft=" + enabled(config.getAncientSpellcraft().isIntegrationEnabled())
                + ", Ancient replacements=" + ancientReplacementSummary()
                + ", CraftTweaker=" + enabled(config.getCraftTweaker().isEnabled())
                + ", JAFS=" + enabled(config.getJafs().isEnabled())
                + ", startup diagnostics=" + enabled(config.getDiagnostics().isEnabled()));
        lines.add(spellStaminaSettings());
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
        lines.addAll(foodStatus(player));
        lines.add(spellStaminaPlayerStatus(player));
        ManaProfileExplanation profile;
        try {
            profile = ManaApi.explainManaProfile(player);
        } catch (IllegalStateException unavailable) {
            lines.add("JAWMS last accepted profile for " + player.getName()
                    + ": unavailable (" + unavailable.getMessage() + ")");
            return lines;
        }
        lines.add("JAWMS last accepted profile: tick=" + profile.getEvaluatedWorldTick()
                + ", rulesRevision=" + profile.getGameplayRulesRevision()
                + ", maximumMana=" + profile.getFinalMaximumMana()
                + ", regeneration=" + profile.getFinalRegeneration()
                + ", lockout=" + profile.getFinalLockoutTicks() + " ticks");
        lines.add("JAWMS Integrations contributions for " + player.getName() + ":");
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

    private String spellStaminaSettings() {
        IntegrationConfigSnapshot.SpellStaminaConfig prices = config.getSpellStamina();
        boolean active = coordinator.getStatus(IntegrationId.SPELL_STAMINA).getState() == IntegrationState.ACTIVE;
        return "Spell stamina prices (" + (active ? "effective" : "configured, inactive") + "): "
                + prices.getDiscreteCost() + " per discrete cast, "
                + prices.getContinuousCostPerSecond() + " per continuous second";
    }

    private String spellStaminaPlayerStatus(EntityPlayer player) {
        if (spellStamina == null) {
            return "Spell stamina: unavailable (" + coordinator.getStatus(IntegrationId.SPELL_STAMINA).getState()
                    + ")";
        }
        try {
            return "Spell stamina: " + spellStamina.inspect(player);
        } catch (RuntimeException | LinkageError unavailable) {
            return "Spell stamina: unavailable (" + unavailable.getClass().getSimpleName() + ")";
        }
    }

    public List<String> foodStatus(EntityPlayer player) {
        List<String> lines = new ArrayList<>();
        IntegrationStatusView status = coordinator.getStatus(IntegrationId.JAFS);
        if (status.getState() != IntegrationState.ACTIVE) {
            lines.add("JAFS source: inactive (" + status.getState() + ", " + status.getDetail() + ")");
            return lines;
        }
        try {
            if (foodSource == null) throw new IllegalStateException("source access not installed");
            FoodStatProjection.Result source = foodSource.inspect(player);
            boolean nonzero = source.getSourceTotals().values().stream()
                    .anyMatch(value -> value.getFlat() != 0.0D || value.getPercent() != 0.0D);
            lines.add("JAFS current source for " + player.getName() + ": "
                    + (!source.getProblems().isEmpty() ? "INVALID FIELDS OMITTED"
                    : nonzero ? "VALID" : "ZERO (no authored active contribution)")
                    + "; projected=" + formatContribution(source.getContribution())
                    + "; independent of the last accepted JAWMS profile below");
            for (java.util.Map.Entry<FoodStatProjection.Stat, FoodStatProjection.Totals> entry
                    : source.getSourceTotals().entrySet()) {
                FoodStatProjection.Totals total = entry.getValue();
                if (total.getFlat() != 0.0D || total.getPercent() != 0.0D) {
                    lines.add("- native " + entry.getKey().getId() + ": flat=" + total.getFlat()
                            + ", percent=" + total.getPercent());
                }
            }
            for (FoodStatProjection.Problem problem : source.getProblems()) {
                lines.add("- omitted " + problem);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            lines.add("JAFS current source: unavailable (" + unavailable.getClass().getSimpleName() + ")");
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
                + ", majestic=" + ancient.getMajesticManaCharm().getValue() + "%"
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
