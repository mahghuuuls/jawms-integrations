package com.mahghuuuls.jawmsintegrations.config;

/** Restart-scoped, validated configuration consumed by runtime services. */
public final class IntegrationConfigSnapshot {

    private final QualityToolsConfig qualityTools;
    private final AncientSpellcraftConfig ancientSpellcraft;
    private final DiagnosticsConfig diagnostics;

    public IntegrationConfigSnapshot(QualityToolsConfig qualityTools,
                                     AncientSpellcraftConfig ancientSpellcraft,
                                     DiagnosticsConfig diagnostics) {
        if (qualityTools == null || ancientSpellcraft == null || diagnostics == null) {
            throw new IllegalArgumentException("Configuration sections must not be null");
        }
        this.qualityTools = qualityTools;
        this.ancientSpellcraft = ancientSpellcraft;
        this.diagnostics = diagnostics;
    }

    public static IntegrationConfigSnapshot defaults() {
        return new IntegrationConfigSnapshot(
                new QualityToolsConfig(true, true),
                new AncientSpellcraftConfig(true),
                new DiagnosticsConfig(false)
        );
    }

    public QualityToolsConfig getQualityTools() {
        return qualityTools;
    }

    public AncientSpellcraftConfig getAncientSpellcraft() {
        return ancientSpellcraft;
    }

    public DiagnosticsConfig getDiagnostics() {
        return diagnostics;
    }

    public static final class QualityToolsConfig {
        private final boolean integrationEnabled;
        private final boolean builtInQualitiesEnabled;

        public QualityToolsConfig(boolean integrationEnabled, boolean builtInQualitiesEnabled) {
            this.integrationEnabled = integrationEnabled;
            this.builtInQualitiesEnabled = builtInQualitiesEnabled;
        }

        public boolean isIntegrationEnabled() {
            return integrationEnabled;
        }

        public boolean areBuiltInQualitiesEnabled() {
            return builtInQualitiesEnabled;
        }
    }

    public static final class AncientSpellcraftConfig {
        private final boolean integrationEnabled;

        public AncientSpellcraftConfig(boolean integrationEnabled) {
            this.integrationEnabled = integrationEnabled;
        }

        public boolean isIntegrationEnabled() {
            return integrationEnabled;
        }
    }

    public static final class DiagnosticsConfig {
        private final boolean enabled;

        public DiagnosticsConfig(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
